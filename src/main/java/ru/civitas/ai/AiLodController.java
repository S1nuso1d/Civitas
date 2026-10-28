package ru.civitas.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Distance-based decision cadence for Civitas workers.
 *
 * Vanilla navigation continues ticking every game tick; this controller only
 * throttles Civitas' comparatively expensive decision logic. Existing worker
 * routines already have a 20-tick work cadence, so LOD never increases that
 * cadence and never makes work progress advance faster than before.
 */
public final class AiLodController {
    private static final int NEAR_INTERVAL = 20;
    private static final int MEDIUM_INTERVAL = 40;
    private static final int FAR_INTERVAL = 100;
    private static final int VERY_FAR_INTERVAL = 200;

    private AiLodController() {
    }

    public static int tickInterval(ServerLevel level, Villager villager) {
        var nearest = level.getNearestPlayer(villager, 256.0D);
        if (nearest == null) {
            return VERY_FAR_INTERVAL;
        }
        return intervalForDistanceSquared(nearest.distanceToSqr(villager));
    }

    /** Pure policy method kept separate so distance thresholds can be unit-tested. */
    public static int intervalForDistanceSquared(double distanceSquared) {
        if (distanceSquared < 32.0D * 32.0D) return NEAR_INTERVAL;
        if (distanceSquared < 64.0D * 64.0D) return MEDIUM_INTERVAL;
        if (distanceSquared < 128.0D * 128.0D) return FAR_INTERVAL;
        return VERY_FAR_INTERVAL;
    }

    public static boolean shouldTick(ServerLevel level, Villager villager) {
        int interval = tickInterval(level, villager);
        return Math.floorMod(level.getGameTime(), interval) == 0;
    }
}
