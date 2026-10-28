package ru.civitas.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.ChatFormatting;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;

import java.util.Comparator;
import java.util.List;

/** Server-authoritative farm worker. It harvests mature crops inside the settlement and stores food physically. */
public final class FarmerWorkerAI {
    private static final String ROLE_KEY = "civitas.worker_role";
    private static final String TARGET_X = "civitas.farmer_target_x";
    private static final String TARGET_Y = "civitas.farmer_target_y";
    private static final String TARGET_Z = "civitas.farmer_target_z";
    private static final String TARGET_SET = "civitas.farmer_target_set";
    private static final String CARRIED_ITEM = "civitas.farmer_carried_item";
    private static final String CARRIED_COUNT = "civitas.farmer_carried_count";
    private static final String PROGRESS = "civitas.farmer_progress";
    private static final String STATUS = "civitas.farmer_status";
    private static final int UPDATE_TICKS = 20;
    private static final int MAX_CARRIED = 16;
    private static final double MOVE_SPEED = 0.8D;

    private FarmerWorkerAI() { }

    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Villager villager) || villager.level().isClientSide
                || !"farmer".equals(villager.getPersistentData().getString(ROLE_KEY))
                || villager.tickCount % UPDATE_TICKS != 0
                || !(villager.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        tickWorker(level, villager);
    }

    private static void tickWorker(ServerLevel level, Villager villager) {
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.hasStarterVillage()) {
            setStatus(villager, "Поселение не найдено");
            return;
        }
        BlockPos center = data.getStarterVillageCenter();
        if (!(level.getBlockEntity(center) instanceof SettlementWarehouseBlockEntity warehouse)) {
            setStatus(villager, "Склад не найден");
            villager.getNavigation().stop();
            return;
        }

        var tag = villager.getPersistentData();
        int carried = Math.max(0, tag.getInt(CARRIED_COUNT));
        String itemId = tag.getString(CARRIED_ITEM);
        BlockPos delivery = center.offset(0, 1, 2);
        if (carried > 0) {
            if (villager.distanceToSqr(delivery.getX() + 0.5D, delivery.getY(), delivery.getZ() + 0.5D) > 5.0D) {
                villager.getNavigation().moveTo(delivery.getX() + 0.5D, delivery.getY(), delivery.getZ() + 0.5D, MOVE_SPEED);
                setStatus(villager, "Несёт урожай на склад");
            } else {
                villager.getNavigation().stop();
                int remaining = deposit(warehouse, itemId, carried);
                tag.putInt(CARRIED_COUNT, remaining);
                tag.putString(CARRIED_ITEM, remaining == 0 ? "" : itemId);
                setStatus(villager, remaining == 0 ? "Урожай доставлен" : "Склад заполнен — урожай сохранён");
            }
            return;
        }

        BlockPos target = readTarget(villager);
        if (target == null || !isValidCrop(level, target, center) || !isMature(level.getBlockState(target))) {
            target = findTarget(level, villager, center);
            if (target == null) {
                tag.putString(STATUS, "Нет зрелых культур");
                setName(villager, " — ищет урожай");
                return;
            }
            writeTarget(villager, target);
        }

        if (villager.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) > 4.0D) {
            villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, MOVE_SPEED);
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
        if (!removeOne(warehouse, seed)) {
            setStatus(villager, "На складе нет семян: " + seed.getDescription().getString());
            return;
        }

        // A bounded, deterministic harvest: one seed is consumed and two food units are stored as cargo.
        level.setBlock(target, state.setValue(CropBlock.AGE, 0), 3);
        tag.putString(CARRIED_ITEM, BuiltInRegistries.ITEM.getKey(food).toString());
        tag.putInt(CARRIED_COUNT, Math.min(2, MAX_CARRIED));
        tag.putInt(PROGRESS, 0);
        clearTarget(villager);
        setStatus(villager, "Собрал урожай");
    }

    public static boolean assignFarmerTo(ServerLevel level, BlockPos center, Villager villager) {
        if (level == null || center == null || villager == null || villager.level() != level
                || !new net.minecraft.world.phys.AABB(center).inflate(24, 12, 24)
                .contains(villager.getX(), villager.getY(), villager.getZ())) {
            return false;
        }
        var tag = villager.getPersistentData();
        String role = tag.getString(ROLE_KEY);
        if ("farmer".equals(role)) return true;
        if (!role.isBlank()) return false;
        tag.putString(ROLE_KEY, "farmer");
        tag.putInt(CARRIED_COUNT, 0);
        tag.putString(CARRIED_ITEM, "");
        tag.putInt(PROGRESS, 0);
        tag.putString(STATUS, "Ищет зрелые культуры");
        clearTarget(villager);
        setName(villager, "");
        return true;
    }

    public static boolean releaseFarmerRole(Villager villager) {
        if (villager == null) return false;
        var tag = villager.getPersistentData();
        if (!"farmer".equals(tag.getString(ROLE_KEY))) return true;
        if (tag.getInt(CARRIED_COUNT) > 0) return false;
        tag.putString(ROLE_KEY, "");
        tag.remove(STATUS);
        clearTarget(villager);
        villager.getNavigation().stop();
        if (villager.getCustomName() != null
                && villager.getCustomName().getString().startsWith("Фермер Civitas")) {
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
        }
        return true;
    }

    public static String getStatus(Villager villager) {
        if (villager == null) return "";
        String status = villager.getPersistentData().getString(STATUS);
        return status.isBlank() ? "Ищет урожай" : status;
    }

    private static BlockPos findTarget(ServerLevel level, Villager villager, BlockPos center) {
        return BlockPos.betweenClosedStream(center.offset(-24, -8, -24), center.offset(24, 8, 24))
                .filter(pos -> level.hasChunkAt(pos))
                .filter(pos -> isValidCrop(level, pos, center))
                .filter(pos -> isMature(level.getBlockState(pos)))
                .filter(pos -> !reservedByAnotherFarmer(level, villager, pos, center))
                .min(Comparator.comparingDouble(pos -> villager.distanceToSqr(
                        pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)))
                .map(BlockPos::immutable).orElse(null);
    }

    private static boolean reservedByAnotherFarmer(ServerLevel level, Villager self, BlockPos target, BlockPos center) {
        for (Villager other : level.getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(center).inflate(24, 12, 24),
                v -> v != self && "farmer".equals(v.getPersistentData().getString(ROLE_KEY)))) {
            BlockPos assigned = readTarget(other);
            if (target.equals(assigned)) return true;
        }
        return false;
    }

    private static boolean isValidCrop(ServerLevel level, BlockPos pos, BlockPos center) {
        if (Math.abs(pos.getX() - center.getX()) > 24 || Math.abs(pos.getZ() - center.getZ()) > 24
                || Math.abs(pos.getY() - center.getY()) > 8 || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return cropBlock(state) != null && level.getBlockState(pos.below()).is(Blocks.FARMLAND);
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

    private static boolean removeOne(SettlementWarehouseBlockEntity warehouse, Item item) {
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                if (stack.isEmpty()) warehouse.setItem(slot, ItemStack.EMPTY);
                else warehouse.setChanged();
                return true;
            }
        }
        return false;
    }

    private static int deposit(SettlementWarehouseBlockEntity warehouse, String itemId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return count;
        Item item = BuiltInRegistries.ITEM.get(id);
        int remaining = count;
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = warehouse.getItem(slot);
            if (existing.isEmpty() || existing.getItem() != item) continue;
            int moved = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
            if (moved > 0) { existing.grow(moved); remaining -= moved; warehouse.setChanged(); }
        }
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            if (!warehouse.getItem(slot).isEmpty()) continue;
            int moved = Math.min(remaining, item.getDefaultMaxStackSize());
            warehouse.setItem(slot, new ItemStack(item, moved));
            remaining -= moved;
        }
        return remaining;
    }

    private static BlockPos readTarget(Villager villager) {
        var tag = villager.getPersistentData();
        if (!tag.getBoolean(TARGET_SET)) return null;
        return new BlockPos(tag.getInt(TARGET_X), tag.getInt(TARGET_Y), tag.getInt(TARGET_Z));
    }

    private static void writeTarget(Villager villager, BlockPos pos) {
        var tag = villager.getPersistentData();
        tag.putBoolean(TARGET_SET, true);
        tag.putInt(TARGET_X, pos.getX()); tag.putInt(TARGET_Y, pos.getY()); tag.putInt(TARGET_Z, pos.getZ());
    }

    private static void clearTarget(Villager villager) {
        var tag = villager.getPersistentData();
        tag.remove(TARGET_SET); tag.remove(TARGET_X); tag.remove(TARGET_Y); tag.remove(TARGET_Z);
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
