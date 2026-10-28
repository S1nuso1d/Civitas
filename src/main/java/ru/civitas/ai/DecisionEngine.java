package ru.civitas.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.data.StorageFoodExtension;

/**
 * Small, server-authoritative local decision layer for assigned workers.
 * Current decision: seek food when hunger is low. All state is stored on the villager
 * with safe defaults, so existing worlds do not need a save migration.
 */
public final class DecisionEngine {
    private static final String HUNGER_KEY = "civitas.hunger";
    private static final String HUNGER_CLOCK_KEY = "civitas.hunger.last_tick";
    private static final String ISSUE_KEY = "civitas.worker_issue";
    private static final int HUNGRY_THRESHOLD = 40;
    private static final int HUNGER_RECOVERY = 20;
    private static final long HUNGER_DECAY_TICKS = 1_000L;

    private DecisionEngine() { }

    /**
     * Advances the worker's need clock and handles a food-seeking interruption.
     * @return true when the profession routine must yield this update.
     */
    public static boolean tick(ServerLevel level, Villager worker) {
        if (level == null || worker == null || level.isClientSide
                || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return false;

        var tag = worker.getPersistentData();
        long now = level.getGameTime();
        if (!tag.contains(HUNGER_KEY)) tag.putInt(HUNGER_KEY, 100);
        if (!tag.contains(HUNGER_CLOCK_KEY)) tag.putLong(HUNGER_CLOCK_KEY, now);

        long last = tag.getLong(HUNGER_CLOCK_KEY);
        if (now > last && now - last >= HUNGER_DECAY_TICKS) {
            long elapsedIntervals = Math.min(24_000L, (now - last) / HUNGER_DECAY_TICKS);
            int hunger = Math.max(0, tag.getInt(HUNGER_KEY) - (int) elapsedIntervals);
            tag.putInt(HUNGER_KEY, hunger);
            tag.putLong(HUNGER_CLOCK_KEY, last + elapsedIntervals * HUNGER_DECAY_TICKS);
        }

        int hunger = Math.max(0, Math.min(100, tag.getInt(HUNGER_KEY)));
        if (hunger > HUNGRY_THRESHOLD) return false;

        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.hasStarterVillage()) return false;
        BlockPos warehousePos = data.getStarterVillageCenter();
        if (!level.hasChunkAt(warehousePos)
                || !(level.getBlockEntity(warehousePos) instanceof SettlementWarehouseBlockEntity warehouse)
                || StorageFoodExtension.totalFoodValue(warehouse) <= 0) {
            tag.putString(ISSUE_KEY, "Голоден: на складе нет еды");
            return false;
        }

        double distance = worker.blockPosition().distSqr(warehousePos);
        if (distance > 9.0D) {
            worker.getNavigation().moveTo(warehousePos.getX() + 0.5D,
                    warehousePos.getY(), warehousePos.getZ() + 0.5D, 1.0D);
            tag.putString(ISSUE_KEY, "Идёт поесть (" + hunger + "/100)");
            return true;
        }

        worker.getNavigation().stop();
        int consumed = StorageFoodExtension.consumeFood(warehouse, 1);
        if (consumed <= 0) {
            tag.putString(ISSUE_KEY, "Голоден: на складе нет еды");
            return false;
        }
        int restored = Math.min(100, hunger + HUNGER_RECOVERY);
        tag.putInt(HUNGER_KEY, restored);
        tag.putString(ISSUE_KEY, "Ест (" + restored + "/100)");
        level.broadcastEntityEvent(worker, (byte) 12);
        return true;
    }

    public static int hunger(Villager worker) {
        if (worker == null) return 100;
        return Math.max(0, Math.min(100, worker.getPersistentData().getInt(HUNGER_KEY)));
    }
}
