package ru.civitas.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;

/**
 * Persistent, bounded navigation retry tracking for Civitas workers.
 * Called at the workers' existing one-second update cadence.
 */
final class WorkerNavigationGuard {
    enum Outcome {
        MOVING,
        RETRY,
        EXHAUSTED
    }

    private static final double MIN_PROGRESS_SQUARED = 0.0625D;

    private WorkerNavigationGuard() {
    }

    static Outcome inspect(Villager villager, String workerKey, int stagnantChecksLimit, int retryLimit) {
        CompoundTag tag = villager.getPersistentData();
        String prefix = "civitas." + workerKey + "_navigation_";
        String initializedKey = prefix + "initialized";
        String xKey = prefix + "last_x";
        String yKey = prefix + "last_y";
        String zKey = prefix + "last_z";
        String stagnantKey = prefix + "stagnant_checks";
        String retriesKey = prefix + "retries";
        String exhaustedKey = prefix + "exhausted";

        if (tag.getBoolean(exhaustedKey)) {
            return Outcome.EXHAUSTED;
        }

        double x = villager.getX();
        double y = villager.getY();
        double z = villager.getZ();
        if (!tag.getBoolean(initializedKey)) {
            tag.putBoolean(initializedKey, true);
            savePosition(tag, xKey, yKey, zKey, x, y, z);
            tag.putInt(stagnantKey, 0);
            return villager.getNavigation().isDone() ? countFailedAttempt(tag, retriesKey, exhaustedKey, retryLimit)
                    : Outcome.MOVING;
        }

        double dx = x - tag.getDouble(xKey);
        double dy = y - tag.getDouble(yKey);
        double dz = z - tag.getDouble(zKey);
        savePosition(tag, xKey, yKey, zKey, x, y, z);

        if (villager.getNavigation().isDone()) {
            tag.putInt(stagnantKey, 0);
            return countFailedAttempt(tag, retriesKey, exhaustedKey, retryLimit);
        }

        if (dx * dx + dy * dy + dz * dz < MIN_PROGRESS_SQUARED) {
            int stagnant = tag.getInt(stagnantKey) + 1;
            tag.putInt(stagnantKey, stagnant);
            if (WorkerNavigationPolicy.hasStalled(stagnant) || stagnant >= stagnantChecksLimit) {
                tag.putInt(stagnantKey, 0);
                villager.getNavigation().stop();
                return countFailedAttempt(tag, retriesKey, exhaustedKey, retryLimit);
            }
        } else {
            tag.putInt(stagnantKey, 0);
        }
        return Outcome.MOVING;
    }

    private static Outcome countFailedAttempt(
            CompoundTag tag, String retriesKey, String exhaustedKey, int retryLimit) {
        int retries = tag.getInt(retriesKey) + 1;
        tag.putInt(retriesKey, retries);
        if (WorkerNavigationPolicy.hasExhaustedRetries(retries) || retries >= retryLimit) {
            tag.putBoolean(exhaustedKey, true);
            return Outcome.EXHAUSTED;
        }
        return Outcome.RETRY;
    }

    private static void savePosition(
            CompoundTag tag, String xKey, String yKey, String zKey, double x, double y, double z) {
        tag.putDouble(xKey, x);
        tag.putDouble(yKey, y);
        tag.putDouble(zKey, z);
    }

    static boolean isExhausted(Villager villager, String workerKey) {
        return villager.getPersistentData().getBoolean("civitas." + workerKey + "_navigation_exhausted");
    }

    static void reset(Villager villager, String workerKey) {
        CompoundTag tag = villager.getPersistentData();
        String prefix = "civitas." + workerKey + "_navigation_";
        tag.remove(prefix + "initialized");
        tag.remove(prefix + "last_x");
        tag.remove(prefix + "last_y");
        tag.remove(prefix + "last_z");
        tag.remove(prefix + "stagnant_checks");
        tag.remove(prefix + "retries");
        tag.remove(prefix + "exhausted");
    }
}
