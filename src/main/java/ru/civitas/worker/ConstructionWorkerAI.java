package ru.civitas.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.world.SettlementBuildingManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs queued construction with one stable assignment per builder.
 * More than one resident can work on separate orders during the same tick.
 */
public final class ConstructionWorkerAI {
    private static final String ROLE_KEY = "civitas.worker_role";
    private static final String TARGET_ASSIGNED_KEY = "civitas.builder_target_assigned";
    private static final String TARGET_X_KEY = "civitas.builder_target_x";
    private static final String TARGET_Y_KEY = "civitas.builder_target_y";
    private static final String TARGET_Z_KEY = "civitas.builder_target_z";
    private static final String BLOCKED_REASON_KEY = "civitas.builder_blocked_reason";

    private static final int WORKER_SCAN_XZ = 96;
    private static final int WORKER_SCAN_Y = 32;
    private static final double MOVE_SPEED = 0.85D;
    private static final double WORK_DISTANCE_SQUARED = 25.0D;
    private static final long TICKS_BETWEEN_LAYERS = 60L;

    private ConstructionWorkerAI() {
    }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || !event.level.dimension().equals(Level.OVERWORLD)
                || level.getGameTime() % 20 != 0) {
            return;
        }

        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        List<CivitasSavedData.ConstructionOrder> orders = data.getConstructionOrders();
        if (orders.isEmpty()) {
            return;
        }

        BlockPos settlementCenter = data.hasStarterVillage()
                ? data.getStarterVillageCenter() : data.getCenter();
        AABB workerArea = new AABB(settlementCenter).inflate(WORKER_SCAN_XZ, WORKER_SCAN_Y, WORKER_SCAN_XZ);
        List<Villager> builders = new ArrayList<>(level.getEntitiesOfClass(
                Villager.class,
                workerArea,
                villager -> "builder".equals(villager.getPersistentData().getString(ROLE_KEY))
        ));
        if (builders.isEmpty()) {
            return;
        }

        Set<BlockPos> activeSites = new HashSet<>();
        for (CivitasSavedData.ConstructionOrder order : orders) {
            activeSites.add(new BlockPos(order.x(), order.y(), order.z()));
        }

        // A finished or removed order must not leave a worker permanently reserved.
        for (Villager builder : builders) {
            BlockPos target = readTarget(builder);
            if (target != null && !activeSites.contains(target)) {
                clearTarget(builder);
                builder.getNavigation().stop();
            }
        }

        Set<UUID> assignedThisTick = new HashSet<>();
        for (CivitasSavedData.ConstructionOrder order : orders) {
            BlockPos site = new BlockPos(order.x(), order.y(), order.z());
            if (!level.hasChunkAt(site)) {
                continue;
            }

            Villager builder = findBuilderForSite(builders, assignedThisTick, site);
            if (builder == null) {
                // Keep each builder on an existing assignment; only unassigned builders
                // may be allocated to a new site.
                builder = findUnassignedBuilder(builders, assignedThisTick, site);
                if (builder != null) {
                    writeTarget(builder, site);
                }
            }
            if (builder == null) {
                continue;
            }

            assignedThisTick.add(builder.getUUID());
            prepareBuilder(builder);

            double distance = builder.distanceToSqr(
                    site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D);
            if (distance > WORK_DISTANCE_SQUARED) {
                if (builder.getNavigation().isDone()) {
                    builder.getNavigation().moveTo(
                            site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D, MOVE_SPEED);
                }
                continue;
            }

            builder.getNavigation().stop();
            if (level.getGameTime() % TICKS_BETWEEN_LAYERS == 0) {
                SettlementBuildingManager.BuildResult result =
                        SettlementBuildingManager.placeConstructionLayer(level, data, order);
                if (result.success()) {
                    builder.getPersistentData().remove(BLOCKED_REASON_KEY);
                } else {
                    builder.getPersistentData().putString(BLOCKED_REASON_KEY, result.message());
                }
                prepareBuilder(builder);
            }
        }
    }

    private static Villager findBuilderForSite(
            List<Villager> builders, Set<UUID> assignedThisTick, BlockPos site) {
        Villager closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Villager builder : builders) {
            if (assignedThisTick.contains(builder.getUUID())) {
                continue;
            }
            if (!site.equals(readTarget(builder))) {
                continue;
            }
            double distance = builder.distanceToSqr(
                    site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D);
            if (distance < bestDistance) {
                closest = builder;
                bestDistance = distance;
            }
        }
        return closest;
    }

    private static Villager findUnassignedBuilder(
            List<Villager> builders, Set<UUID> assignedThisTick, BlockPos site) {
        Villager closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Villager builder : builders) {
            if (assignedThisTick.contains(builder.getUUID()) || readTarget(builder) != null) {
                continue;
            }
            double distance = builder.distanceToSqr(
                    site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D);
            if (distance < bestDistance) {
                closest = builder;
                bestDistance = distance;
            }
        }
        return closest;
    }

    private static void prepareBuilder(Villager builder) {
        boolean blocked = !builder.getPersistentData().getString(BLOCKED_REASON_KEY).isBlank();
        String displayName = blocked
                ? "Строитель Civitas — ждёт снабжение"
                : "Строитель Civitas";
        if (builder.getCustomName() == null
                || !builder.getCustomName().getString().equals(displayName)) {
            builder.setCustomName(Component.literal(displayName));
        }
        builder.setCustomNameVisible(true);
        builder.setPersistenceRequired();
    }

    private static BlockPos readTarget(Villager villager) {
        var tag = villager.getPersistentData();
        if (!tag.getBoolean(TARGET_ASSIGNED_KEY)) {
            return null;
        }
        return new BlockPos(
                tag.getInt(TARGET_X_KEY),
                tag.getInt(TARGET_Y_KEY),
                tag.getInt(TARGET_Z_KEY)
        );
    }

    private static void writeTarget(Villager villager, BlockPos site) {
        var tag = villager.getPersistentData();
        tag.putBoolean(TARGET_ASSIGNED_KEY, true);
        tag.putInt(TARGET_X_KEY, site.getX());
        tag.putInt(TARGET_Y_KEY, site.getY());
        tag.putInt(TARGET_Z_KEY, site.getZ());
    }

    private static void clearTarget(Villager villager) {
        var tag = villager.getPersistentData();
        tag.remove(TARGET_ASSIGNED_KEY);
        tag.remove(TARGET_X_KEY);
        tag.remove(TARGET_Y_KEY);
        tag.remove(TARGET_Z_KEY);
    }
}
