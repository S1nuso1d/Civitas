package ru.civitas.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.event.entity.living.LivingEvent;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;

/**
 * First physical worker prototype. A tagged village villager walks to nearby
 * tree logs, chops them over several seconds, carries up to eight logs, and
 * physically returns to the starter warehouse to deposit them.
 */
public final class LumberjackWorkerAI {
    private static final String ROLE_KEY = "civitas.worker_role";
    private static final String CARRIED_ITEM_KEY = "civitas.worker_carried_item";
    private static final String CARRIED_COUNT_KEY = "civitas.worker_carried_count";
    private static final String CHOP_PROGRESS_KEY = "civitas.worker_chop_progress";
    private static final int MAX_CARRIED = 8;
    private static final int WORK_INTERVAL = 20;

    private LumberjackWorkerAI() {
    }

    /**
     * Assign one resident as lumberjack. This also upgrades older worlds without
     * rebuilding their starter village.
     */
    public static void ensureWorkerExists(ServerLevel level, BlockPos villageCenter) {
        AABB area = new AABB(
                villageCenter.getX() - 24, villageCenter.getY() - 12, villageCenter.getZ() - 24,
                villageCenter.getX() + 24, villageCenter.getY() + 12, villageCenter.getZ() + 24
        );
        java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        Villager chosen = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Villager villager : villagers) {
            if ("lumberjack".equals(villager.getPersistentData().getString(ROLE_KEY))) {
                return;
            }
            double distance = villager.distanceToSqr(
                    villageCenter.getX() + 0.5D, villageCenter.getY() + 1.0D, villageCenter.getZ() + 0.5D);
            if (distance < nearestDistance) {
                chosen = villager;
                nearestDistance = distance;
            }
        }
        if (chosen == null) {
            chosen = EntityType.VILLAGER.create(level);
            if (chosen == null) {
                return;
            }
            chosen.moveTo(villageCenter.getX() + 0.5D, villageCenter.getY() + 1.0D,
                    villageCenter.getZ() + 2.5D, 0.0F, 0.0F);
            level.addFreshEntity(chosen);
        }
        chosen.getPersistentData().putString(ROLE_KEY, "lumberjack");
        chosen.setCustomName(Component.literal("Лесоруб Civitas"));
        chosen.setCustomNameVisible(true);
        chosen.setPersistenceRequired();
    }

    /** Returns the Civitas role currently assigned to a villager, or an empty string. */
    public static String getWorkerRole(Villager villager) {
        return villager.getPersistentData().getString(ROLE_KEY);
    }

    /**
     * Assigns the nearest resident to the lumberjack role and removes that role
     * from any other resident in the starter-village area.
     */
    public static boolean assignNearestLumberjack(ServerLevel level, BlockPos villageCenter) {
        AABB area = new AABB(
                villageCenter.getX() - 24, villageCenter.getY() - 12, villageCenter.getZ() - 24,
                villageCenter.getX() + 24, villageCenter.getY() + 12, villageCenter.getZ() + 24
        );
        java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        if (villagers.isEmpty()) {
            ensureWorkerExists(level, villageCenter);
            villagers = level.getEntitiesOfClass(Villager.class, area);
        }
        if (villagers.isEmpty()) {
            return false;
        }

        Villager chosen = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Villager villager : villagers) {
            if ("lumberjack".equals(getWorkerRole(villager))) {
                villager.getPersistentData().putString(ROLE_KEY, "");
                if (villager.getCustomName() != null
                        && "Лесоруб Civitas".equals(villager.getCustomName().getString())) {
                    villager.setCustomName(null);
                    villager.setCustomNameVisible(false);
                }
            }
            double distance = villager.distanceToSqr(
                    villageCenter.getX() + 0.5D, villageCenter.getY() + 1.0D, villageCenter.getZ() + 0.5D);
            if (distance < nearestDistance) {
                chosen = villager;
                nearestDistance = distance;
            }
        }
        if (chosen == null) {
            return false;
        }
        chosen.getPersistentData().putString(ROLE_KEY, "lumberjack");
        chosen.setCustomName(Component.literal("Лесоруб Civitas"));
        chosen.setCustomNameVisible(true);
        chosen.setPersistenceRequired();
        return true;
    }

    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }
        if (!"lumberjack".equals(villager.getPersistentData().getString(ROLE_KEY))) {
            return;
        }
        if (villager.tickCount % WORK_INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        tickWorker(level, villager);
    }

    private static void tickWorker(ServerLevel level, Villager villager) {
        CivitasSavedData worldData = CivitasSavedData.get(level.getServer());
        if (!worldData.hasStarterVillage() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        BlockPos warehousePos = worldData.getStarterVillageCenter();
        if (!(level.getBlockEntity(warehousePos) instanceof SettlementWarehouseBlockEntity warehouse)) {
            villager.getNavigation().stop();
            return;
        }

        var persistent = villager.getPersistentData();
        String carriedItemId = persistent.getString(CARRIED_ITEM_KEY);
        int carriedCount = Math.max(0, persistent.getInt(CARRIED_COUNT_KEY));
        if (carriedCount == 0) {
            carriedItemId = "";
            persistent.putString(CARRIED_ITEM_KEY, "");
        }

        BlockPos treeLog = findNearestTreeLog(level, villager.blockPosition());
        String targetItemId = treeLog == null ? "" : BuiltInRegistries.ITEM.getKey(level.getBlockState(treeLog).getBlock().asItem()).toString();
        boolean mustDeliver = carriedCount >= MAX_CARRIED
                || carriedCount > 0 && (treeLog == null || !carriedItemId.equals(targetItemId));

        BlockPos deliveryStand = warehousePos.offset(0, 1, 2);
        if (mustDeliver || treeLog == null && carriedCount > 0) {
            if (villager.distanceToSqr(deliveryStand.getX() + 0.5D, deliveryStand.getY(), deliveryStand.getZ() + 0.5D) > 5.0D) {
                villager.getNavigation().moveTo(deliveryStand.getX() + 0.5D, deliveryStand.getY(), deliveryStand.getZ() + 0.5D, 0.9D);
                return;
            }
            depositCarriedLogs(warehouse, persistent, carriedItemId, carriedCount);
            return;
        }

        if (treeLog == null) {
            if (carriedCount > 0) {
                if (villager.distanceToSqr(deliveryStand.getX() + 0.5D, deliveryStand.getY(), deliveryStand.getZ() + 0.5D) > 5.0D) {
                    villager.getNavigation().moveTo(deliveryStand.getX() + 0.5D, deliveryStand.getY(), deliveryStand.getZ() + 0.5D, 0.9D);
                } else {
                    depositCarriedLogs(warehouse, persistent, carriedItemId, carriedCount);
                }
            } else {
                villager.getNavigation().stop();
            }
            return;
        }

        BlockPos workPos = findWorkPosition(level, treeLog);
        if (workPos == null) {
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            if (carriedCount > 0) {
                villager.getNavigation().moveTo(deliveryStand.getX() + 0.5D, deliveryStand.getY(), deliveryStand.getZ() + 0.5D, 0.9D);
            } else {
                villager.getNavigation().stop();
            }
            return;
        }

        if (villager.distanceToSqr(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D) > 4.0D) {
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            villager.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D, 0.9D);
            return;
        }

        villager.getNavigation().stop();
        int progress = persistent.getInt(CHOP_PROGRESS_KEY) + 1;
        if (progress < 3) {
            persistent.putInt(CHOP_PROGRESS_KEY, progress);
            return;
        }

        BlockState logState = level.getBlockState(treeLog);
        if (!logState.is(BlockTags.LOGS)) {
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            return;
        }

        Item logItem = logState.getBlock().asItem();
        String actualItemId = BuiltInRegistries.ITEM.getKey(logItem).toString();
        if (logItem == net.minecraft.world.item.Items.AIR
                || carriedCount > 0 && !carriedItemId.equals(actualItemId)) {
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            return;
        }

        if (level.destroyBlock(treeLog, false)) {
            if (carriedCount == 0) {
                carriedItemId = actualItemId;
                persistent.putString(CARRIED_ITEM_KEY, carriedItemId);
            }
            persistent.putInt(CARRIED_COUNT_KEY, carriedCount + 1);
        }
        persistent.putInt(CHOP_PROGRESS_KEY, 0);
    }

    private static BlockPos findNearestTreeLog(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                for (int dy = -3; dy <= 9; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.LOGS) || !hasNearbyLeaves(level, pos)) {
                        continue;
                    }
                    double distance = origin.distSqr(pos);
                    if (distance < bestDistance) {
                        best = pos.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean hasNearbyLeaves(ServerLevel level, BlockPos logPos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 6; dy++) {
                    if (level.getBlockState(logPos.offset(dx, dy, dz)).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos findWorkPosition(ServerLevel level, BlockPos logPos) {
        BlockPos[] adjacent = {
                logPos.east(), logPos.west(), logPos.north(), logPos.south()
        };
        for (BlockPos side : adjacent) {
            for (int y = logPos.getY(); y >= Math.max(level.getMinBuildHeight() + 1, logPos.getY() - 16); y--) {
                BlockPos candidate = new BlockPos(side.getX(), y, side.getZ());
                BlockPos below = candidate.below();
                if (level.getBlockState(candidate).isAir()
                        && !level.getBlockState(below).isAir()
                        && level.getFluidState(below).isEmpty()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static void depositCarriedLogs(SettlementWarehouseBlockEntity warehouse,
                                           net.minecraft.nbt.CompoundTag persistent,
                                           String itemId, int carriedCount) {
        if (carriedCount <= 0 || itemId == null || itemId.isBlank()) {
            persistent.putInt(CARRIED_COUNT_KEY, 0);
            persistent.putString(CARRIED_ITEM_KEY, "");
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR) {
            return;
        }

        int remaining = carriedCount;
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = warehouse.getItem(slot);
            if (existing.isEmpty() || existing.getItem() != item) {
                continue;
            }
            int moved = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
            if (moved > 0) {
                existing.grow(moved);
                remaining -= moved;
                warehouse.setChanged();
            }
        }

        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            if (!warehouse.getItem(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(remaining, item.getDefaultMaxStackSize());
            warehouse.setItem(slot, new ItemStack(item, moved));
            remaining -= moved;
        }

        persistent.putInt(CARRIED_COUNT_KEY, remaining);
        persistent.putString(CARRIED_ITEM_KEY, remaining == 0 ? "" : itemId);
    }

}
