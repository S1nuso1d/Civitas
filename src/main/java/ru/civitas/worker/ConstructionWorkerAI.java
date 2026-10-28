package ru.civitas.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
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
    private static final String WORK_PROGRESS_KEY = "civitas.builder_work_progress_ticks";

    private static final int WORKER_SCAN_XZ = 96;
    private static final int WORKER_SCAN_Y = 32;
    private static final double MOVE_SPEED = 0.85D;
    private static final double WORK_DISTANCE_SQUARED = 9.0D;
    private static final int WORK_PROGRESS_TICKS = 60;
    private static final int WORK_UPDATE_TICKS = 20;

    private ConstructionWorkerAI() {
    }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || !event.level.dimension().equals(Level.OVERWORLD)
                || level.getGameTime() % WORK_UPDATE_TICKS != 0) {
            return;
        }

        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        List<CivitasSavedData.ConstructionOrder> orders = data.getConstructionOrders();

        BlockPos settlementCenter = data.hasStarterVillage()
                ? data.getStarterVillageCenter() : data.getCenter();
        AABB workerArea = new AABB(settlementCenter).inflate(WORKER_SCAN_XZ, WORKER_SCAN_Y, WORKER_SCAN_XZ);
        List<Villager> builders = new ArrayList<>(level.getEntitiesOfClass(
                Villager.class,
                workerArea,
                villager -> "builder".equals(villager.getPersistentData().getString(ROLE_KEY))
        ));

        Set<BlockPos> activeSites = new HashSet<>();
        for (CivitasSavedData.ConstructionOrder order : orders) {
            activeSites.add(new BlockPos(order.x(), order.y(), order.z()));
        }

        // Clear obsolete assignments before returning when the queue becomes empty.
        for (Villager builder : builders) {
            BlockPos target = readTarget(builder);
            if (target != null && !activeSites.contains(target)) {
                clearTarget(builder);
                builder.getPersistentData().remove(BLOCKED_REASON_KEY);
                builder.getNavigation().stop();
                prepareBuilder(builder);
            }
        }
        if (orders.isEmpty() || builders.isEmpty()) {
            return;
        }

        Set<UUID> assignedThisTick = new HashSet<>();
        for (CivitasSavedData.ConstructionOrder order : orders) {
            BlockPos site = new BlockPos(order.x(), order.y(), order.z());
            if (!level.hasChunkAt(site)) {
                continue;
            }

            Villager builder = findBuilderForSite(builders, assignedThisTick, site);
            if (builder == null) {
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
                builder.getPersistentData().putInt(WORK_PROGRESS_KEY, 0);
                if (builder.getNavigation().isDone()) {
                    builder.getNavigation().moveTo(
                            site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D, MOVE_SPEED);
                }
                prepareBuilder(builder);
                continue;
            }

            builder.getNavigation().stop();
            var persistent = builder.getPersistentData();
            int progressTicks = Math.min(WORK_PROGRESS_TICKS,
                    Math.max(0, persistent.getInt(WORK_PROGRESS_KEY)) + WORK_UPDATE_TICKS);
            persistent.putInt(WORK_PROGRESS_KEY, progressTicks);
            prepareBuilder(builder);
            if (progressTicks < WORK_PROGRESS_TICKS) {
                continue;
            }

            SettlementBuildingManager.BuildResult result =
                    SettlementBuildingManager.placeConstructionLayer(level, data, order);
            if (result.success()) {
                persistent.putInt(WORK_PROGRESS_KEY, 0);
                persistent.remove(BLOCKED_REASON_KEY);
            } else {
                // Keep the bar full while blocked, and retry safely once per second.
                persistent.putInt(WORK_PROGRESS_KEY, WORK_PROGRESS_TICKS);
                persistent.putString(BLOCKED_REASON_KEY, result.message());
            }
            prepareBuilder(builder);
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
        var persistent = builder.getPersistentData();
        boolean blocked = !persistent.getString(BLOCKED_REASON_KEY).isBlank();
        int progressTicks = Math.max(0, Math.min(WORK_PROGRESS_TICKS, persistent.getInt(WORK_PROGRESS_KEY)));
        Component displayName;
        if (blocked) {
            displayName = Component.literal("Строитель Civitas — ожидание")
                    .withStyle(ChatFormatting.RED);
        } else if (progressTicks > 0) {
            int filled = Math.max(0, Math.min(10,
                    (int) Math.ceil(progressTicks * 10.0D / WORK_PROGRESS_TICKS)));
            displayName = Component.literal("Строитель Civitas ")
                    .append(Component.literal("[").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("■".repeat(filled)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("□".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("] " + (progressTicks / WORK_UPDATE_TICKS) + "/3 сек")
                            .withStyle(ChatFormatting.YELLOW));
        } else if (!builder.getNavigation().isDone()) {
            displayName = Component.literal("Строитель Civitas — идёт на площадку")
                    .withStyle(ChatFormatting.GRAY);
        } else {
            displayName = Component.literal("Строитель Civitas");
        }
        if (builder.getCustomName() == null
                || !builder.getCustomName().getString().equals(displayName.getString())) {
            builder.setCustomName(displayName);
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
        if (!site.equals(readTarget(villager))) {
            tag.putInt(WORK_PROGRESS_KEY, 0);
            tag.remove(BLOCKED_REASON_KEY);
        }
        tag.putBoolean(TARGET_ASSIGNED_KEY, true);
        tag.putInt(TARGET_X_KEY, site.getX());
        tag.putInt(TARGET_Y_KEY, site.getY());
        tag.putInt(TARGET_Z_KEY, site.getZ());
    }

    /** Releases a selected builder so another worker can take its queued order. */
    public static boolean releaseBuilderRole(Villager villager) {
        if (villager == null) return false;
        var tag = villager.getPersistentData();
        if (!"builder".equals(tag.getString(ROLE_KEY))) return true;
        clearTarget(villager);
        tag.putInt(WORK_PROGRESS_KEY, 0);
        tag.remove(BLOCKED_REASON_KEY);
        tag.putString(ROLE_KEY, "");
        villager.getNavigation().stop();
        if (villager.getCustomName() != null
                && villager.getCustomName().getString().startsWith("Строитель Civitas")) {
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
        }
        return true;
    }

    private static void clearTarget(Villager villager) {
        var tag = villager.getPersistentData();
        tag.remove(TARGET_ASSIGNED_KEY);
        tag.remove(TARGET_X_KEY);
        tag.remove(TARGET_Y_KEY);
        tag.remove(TARGET_Z_KEY);
        tag.putInt(WORK_PROGRESS_KEY, 0);
    }
}
