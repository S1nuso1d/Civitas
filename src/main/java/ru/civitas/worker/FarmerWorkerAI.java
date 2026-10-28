package ru.civitas.worker;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import ru.civitas.CivitasConfig;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.data.StorageFoodExtension;

import java.util.Comparator;

/** Server-authoritative farmer with persistent cargo and settlement-wide crop reservations. */
public final class FarmerWorkerAI {
    private static final String ROLE_KEY = "civitas.worker_role";
    private static final String TARGET_X = "civitas.farmer_target_x";
    private static final String TARGET_Y = "civitas.farmer_target_y";
    private static final String TARGET_Z = "civitas.farmer_target_z";
    private static final String TARGET_SET = "civitas.farmer_target_set";
    private static final String CARGO_KEY = "civitas.farmer_cargo";
    private static final String LEGACY_CARRIED_ITEM = "civitas.farmer_carried_item";
    private static final String LEGACY_CARRIED_COUNT = "civitas.farmer_carried_count";
    private static final String STATUS = "civitas.farmer_status";
    private static final String NEXT_SCAN_TICK = "civitas.farmer_next_scan_tick";
    private static final String HARVEST_PROGRESS = "civitas.farmer_harvest_progress_ticks";
    private static final int HARVEST_WORK_TICKS = 60;
    private static final int UPDATE_TICKS = 20;
    private static final double MOVE_SPEED = 0.8D;
    private static final double WORK_DISTANCE_SQUARED = 4.0D;

    private FarmerWorkerAI() { }

    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Villager villager) || villager.level().isClientSide
                || !"farmer".equals(villager.getPersistentData().getString(ROLE_KEY))
                || villager.tickCount % UPDATE_TICKS != 0
                || !(villager.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) return;
        tickWorker(level, villager);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level) || level.isClientSide) return;
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        data.releaseFarmlandForWorker(villager.getUUID());
        migrateLegacyCargo(villager);
        FoodCargo cargo = FoodCargo.load(villager.getPersistentData(), CARGO_KEY);
        boolean droppedAll = true;
        for (var entry : cargo.contents().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                droppedAll = false;
                break;
            }
            Item cargoItem = BuiltInRegistries.ITEM.get(id);
            int remaining = entry.getValue();
            while (remaining > 0) {
                int count = Math.min(remaining, cargoItem.getDefaultMaxStackSize());
                ItemEntity dropped = new ItemEntity(level, villager.getX(), villager.getY() + 0.3D,
                        villager.getZ(), new ItemStack(cargoItem, count));
                if (!level.addFreshEntity(dropped)) {
                    droppedAll = false;
                    break;
                }
                remaining -= count;
            }
            if (!droppedAll) break;
        }
        if (droppedAll && !cargo.isEmpty()) {
            villager.getPersistentData().remove(CARGO_KEY);
            villager.getPersistentData().putInt(LEGACY_CARRIED_COUNT, 0);
            villager.getPersistentData().putString(LEGACY_CARRIED_ITEM, "");
        }
    }

    private static void tickWorker(ServerLevel level, Villager villager) {
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.hasStarterVillage()) {
            setStatus(villager, "Поселение не найдено");
            return;
        }
        BlockPos center = data.getStarterVillageCenter();
        if (!level.hasChunkAt(center)
                || !(level.getBlockEntity(center) instanceof SettlementWarehouseBlockEntity warehouse)) {
            setStatus(villager, "Склад не найден");
            villager.getNavigation().stop();
            return;
        }

        migrateLegacyCargo(villager);
        var tag = villager.getPersistentData();
        FoodCargo cargo = FoodCargo.load(tag, CARGO_KEY);
        BlockPos delivery = center.offset(0, 1, 2);
        if (!cargo.isEmpty()) {
            if (villager.distanceToSqr(delivery.getX() + 0.5D, delivery.getY(), delivery.getZ() + 0.5D)
                    > WORK_DISTANCE_SQUARED) {
                villager.getNavigation().moveTo(delivery.getX() + 0.5D, delivery.getY(),
                        delivery.getZ() + 0.5D, MOVE_SPEED);
                setStatus(villager, "Несёт урожай на склад");
            } else {
                villager.getNavigation().stop();
                int nutrition = cargo.totalFoodValue();
                if (StorageFoodExtension.depositCargo(warehouse, cargo)) {
                    tag.remove(CARGO_KEY);
                    long day = Math.max(0L, level.getDayTime() / 24_000L);
                    data.recordFoodProduction(day, nutrition);
                    setStatus(villager, "Урожай доставлен (" + nutrition + " ед.)");
                } else {
                    cargo.save(tag, CARGO_KEY);
                    setStatus(villager, "Склад заполнен — груз сохранён");
                }
            }
            return;
        }

        BlockPos target = readTarget(villager);
        if (target != null && (!isValidCrop(level, target, center) || !isMature(level.getBlockState(target))) ) {
            clearTarget(villager);
            target = null;
        }
        if (target == null) {
            long nextScanTick = tag.getLong(NEXT_SCAN_TICK);
            if (level.getGameTime() < nextScanTick) {
                setStatus(villager, "Нет свободных зрелых грядок");
                return;
            }
            target = findTarget(level, data, villager, center);
            if (target == null) {
                tag.putLong(NEXT_SCAN_TICK, level.getGameTime() + 100L);
                setStatus(villager, "Нет свободных зрелых грядок");
                return;
            }
            tag.putLong(NEXT_SCAN_TICK, 0L);
            if (!data.tryReserveFarmland(target, villager.getUUID())) {
                setStatus(villager, "Грядка занята другим фермером");
                return;
            }
            writeTarget(villager, target);
        } else if (!data.isFarmlandReservedBy(target, villager.getUUID())) {
            if (!data.tryReserveFarmland(target, villager.getUUID())) {
                clearTarget(villager);
                setStatus(villager, "Грядка занята другим фермером");
                return;
            }
        }

        if (villager.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D)
                > WORK_DISTANCE_SQUARED) {
            villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(),
                    target.getZ() + 0.5D, MOVE_SPEED);
            setStatus(villager, "Идёт к грядке");
            return;
        }
        villager.getNavigation().stop();

        BlockState state = level.getBlockState(target);
        CropBlock crop = cropBlock(state);
        Item seed = seedFor(state);
        Item food = foodFor(state);
        if (crop == null || seed == Items.AIR || food == Items.AIR || !isMature(state)) {
            clearTarget(villager);
            return;
        }
        int harvestProgress = Math.min(HARVEST_WORK_TICKS,
                Math.max(0, tag.getInt(HARVEST_PROGRESS)) + UPDATE_TICKS);
        tag.putInt(HARVEST_PROGRESS, harvestProgress);
        villager.getLookControl().setLookAt(
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        villager.swing(InteractionHand.MAIN_HAND);
        if (harvestProgress < HARVEST_WORK_TICKS) {
            setStatus(villager, "Собирает урожай (" + harvestProgress / UPDATE_TICKS + "/3 сек)");
            return;
        }
        tag.putInt(HARVEST_PROGRESS, 0);
        if (!StorageFoodExtension.removeOne(warehouse, seed)) {
            setStatus(villager, "Нет семян на складе: " + seed.getDescription().getString());
            return;
        }

        FoodCargo harvested = new FoodCargo();
        harvested.add(food, yieldFor(state));
        harvested.save(tag, CARGO_KEY);
        level.setBlock(target, state.setValue(CropBlock.AGE, 0), 3);
        clearTarget(villager);
        setStatus(villager, "Урожай собран");
    }

    public static boolean assignFarmerTo(ServerLevel level, BlockPos center, Villager villager) {
        if (level == null || center == null || villager == null || villager.level() != level
                || !new AABB(center).inflate(24, 12, 24)
                .contains(villager.getX(), villager.getY(), villager.getZ())) return false;

        migrateLegacyCargo(villager);
        var tag = villager.getPersistentData();
        String role = tag.getString(ROLE_KEY);
        if ("farmer".equals(role)) return true;
        if (hasCargo(villager)) return false;

        if ("lumberjack".equals(role)) {
            if (!LumberjackWorkerAI.releaseLumberjackRole(villager)) return false;
        } else if ("builder".equals(role)) {
            if (!ConstructionWorkerAI.releaseBuilderRole(villager)) return false;
        } else if (!role.isBlank()) return false;

        clearTarget(villager);
        tag.remove(CARGO_KEY);
        tag.putString(LEGACY_CARRIED_ITEM, "");
        tag.putInt(LEGACY_CARRIED_COUNT, 0);
        tag.putString(ROLE_KEY, "farmer");
        tag.putLong(NEXT_SCAN_TICK, 0L);
        tag.putString(STATUS, "Ищет зрелые культуры");
        setName(villager, "");
        return true;
    }

    public static boolean releaseFarmerRole(Villager villager) {
        if (villager == null) return false;
        migrateLegacyCargo(villager);
        var tag = villager.getPersistentData();
        if (!"farmer".equals(tag.getString(ROLE_KEY))) return true;
        if (hasCargo(villager)) return false;
        clearTarget(villager);
        tag.putString(ROLE_KEY, "");
        tag.remove(STATUS);
        villager.getNavigation().stop();
        if (villager.getCustomName() != null
                && villager.getCustomName().getString().startsWith("Фермер Civitas")) {
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
        }
        return true;
    }

    public static boolean hasCargo(Villager villager) {
        if (villager == null) return false;
        migrateLegacyCargo(villager);
        return !FoodCargo.load(villager.getPersistentData(), CARGO_KEY).isEmpty()
                || villager.getPersistentData().getInt(LEGACY_CARRIED_COUNT) > 0;
    }

    public static String getStatus(Villager villager) {
        if (villager == null) return "";
        String status = villager.getPersistentData().getString(STATUS);
        return status.isBlank() ? "Ищет урожай" : status;
    }

    private static BlockPos findTarget(ServerLevel level, CivitasSavedData data, Villager villager, BlockPos center) {
        int radius = CivitasConfig.FARMER_SEARCH_RADIUS.get();
        return BlockPos.betweenClosedStream(center.offset(-radius, -8, -radius),
                        center.offset(radius, 8, radius))
                .filter(pos -> level.hasChunkAt(pos))
                .filter(pos -> isValidCrop(level, pos, center))
                .filter(pos -> isMature(level.getBlockState(pos)))
                .filter(pos -> !data.isFarmlandReservedByOther(pos, villager.getUUID()))
                .min(Comparator.comparingDouble(pos -> villager.distanceToSqr(
                        pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)))
                .map(BlockPos::immutable).orElse(null);
    }

    private static boolean isValidCrop(ServerLevel level, BlockPos pos, BlockPos center) {
        int radius = CivitasConfig.FARMER_SEARCH_RADIUS.get();
        if (Math.abs(pos.getX() - center.getX()) > radius
                || Math.abs(pos.getZ() - center.getZ()) > radius
                || Math.abs(pos.getY() - center.getY()) > 8 || !level.hasChunkAt(pos)) return false;
        return cropBlock(level.getBlockState(pos)) != null
                && level.getBlockState(pos.below()).is(Blocks.FARMLAND);
    }

    private static boolean isMature(BlockState state) {
        CropBlock crop = cropBlock(state);
        return crop != null && state.getValue(CropBlock.AGE) >= crop.getMaxAge();
    }

    private static CropBlock cropBlock(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop
                && (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS)
                || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS))) return crop;
        return null;
    }

    private static Item seedFor(BlockState state) {
        if (state.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (state.is(Blocks.CARROTS)) return Items.CARROT;
        if (state.is(Blocks.POTATOES)) return Items.POTATO;
        if (state.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        return Items.AIR;
    }

    private static Item foodFor(BlockState state) {
        if (state.is(Blocks.WHEAT)) return Items.WHEAT;
        if (state.is(Blocks.CARROTS)) return Items.CARROT;
        if (state.is(Blocks.POTATOES)) return Items.POTATO;
        if (state.is(Blocks.BEETROOTS)) return Items.BEETROOT;
        return Items.AIR;
    }

    private static int yieldFor(BlockState state) {
        return state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) ? 2 : 1;
    }

    private static void migrateLegacyCargo(Villager villager) {
        var tag = villager.getPersistentData();
        if (tag.getInt(LEGACY_CARRIED_COUNT) <= 0
                || !tag.getList(CARGO_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(LEGACY_CARRIED_ITEM));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (!FoodRegistry.isFood(item)) return;
        FoodCargo cargo = new FoodCargo();
        cargo.add(item, tag.getInt(LEGACY_CARRIED_COUNT));
        cargo.save(tag, CARGO_KEY);
        tag.putInt(LEGACY_CARRIED_COUNT, 0);
        tag.putString(LEGACY_CARRIED_ITEM, "");
    }

    private static BlockPos readTarget(Villager villager) {
        var tag = villager.getPersistentData();
        if (!tag.getBoolean(TARGET_SET)) return null;
        return new BlockPos(tag.getInt(TARGET_X), tag.getInt(TARGET_Y), tag.getInt(TARGET_Z));
    }

    private static void writeTarget(Villager villager, BlockPos pos) {
        var tag = villager.getPersistentData();
        tag.putBoolean(TARGET_SET, true);
        tag.putInt(TARGET_X, pos.getX());
        tag.putInt(TARGET_Y, pos.getY());
        tag.putInt(TARGET_Z, pos.getZ());
    }

    private static void clearTarget(Villager villager) {
        if (villager.level() instanceof ServerLevel level && level.getServer() != null) {
            CivitasSavedData.get(level.getServer()).releaseFarmlandForWorker(villager.getUUID());
        }
        var tag = villager.getPersistentData();
        tag.remove(TARGET_SET);
        tag.remove(TARGET_X);
        tag.remove(TARGET_Y);
        tag.remove(TARGET_Z);
        tag.putLong(NEXT_SCAN_TICK, 0L);
    }

    private static void setStatus(Villager villager, String status) {
        villager.getPersistentData().putString(STATUS, status);
        setName(villager, " — " + status.toLowerCase(java.util.Locale.ROOT));
    }

    private static void setName(Villager villager, String suffix) {
        Component name = Component.literal("Фермер Civitas" + (suffix == null ? "" : suffix))
                .withStyle(ChatFormatting.GOLD);
        if (villager.getCustomName() == null
                || !villager.getCustomName().getString().equals(name.getString())) villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
    }
}
