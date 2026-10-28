package ru.civitas.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Navigation watchdog for assigned workers.
 *
 * It only observes villagers that are actively navigating. Workers standing
 * still to chop, harvest or build are not considered stuck. Recovery is a
 * navigation restart rather than a teleport, avoiding movement through walls
 * and preserving the worker's saved target and cargo.
 */
public final class StuckDetector {
    private static final String LAST_POS_KEY = "civitas.stuck.last_pos";
    private static final String STUCK_TICKS_KEY = "civitas.stuck.ticks";
    private static final String RECOVERIES_KEY = "civitas.stuck.recoveries";
    private static final String COOLDOWN_UNTIL_KEY = "civitas.stuck.cooldown_until";
    private static final String ISSUE_KEY = "civitas.stuck.issue";
    private static final int POSITION_TIMEOUT = 200;
    private static final int MAX_RECOVERIES = 3;
    private static final int RECOVERY_COOLDOWN = 40;
    private static final int EXHAUSTED_COOLDOWN = 200;

    private StuckDetector() {
    }

    /**
     * Call from the server-side villager tick before role AI. Returns true while
     * a recovery cooldown is active; normal worker logic resumes afterwards.
     */
    public static boolean check(ServerLevel level, Villager villager) {
        var tag = villager.getPersistentData();
        long now = level.getGameTime();
        long cooldownUntil = tag.getLong(COOLDOWN_UNTIL_KEY);
        if (now < cooldownUntil) {
            villager.getNavigation().stop();
            return true;
        }
        if (cooldownUntil != 0L) {
            tag.remove(COOLDOWN_UNTIL_KEY);
        }

        // Do not flag intentional stationary work or workers that have no route.
        if (villager.getNavigation().isDone()) {
            tag.putLong(LAST_POS_KEY, villager.blockPosition().asLong());
            tag.putInt(STUCK_TICKS_KEY, 0);
            return false;
        }

        BlockPos current = villager.blockPosition();
        long previousPacked = tag.getLong(LAST_POS_KEY);
        if (previousPacked == 0L) {
            tag.putLong(LAST_POS_KEY, current.asLong());
            tag.putInt(STUCK_TICKS_KEY, 0);
            return false;
        }

        if (!BlockPos.of(previousPacked).equals(current)) {
            tag.putLong(LAST_POS_KEY, current.asLong());
            tag.putInt(STUCK_TICKS_KEY, 0);
            tag.putInt(RECOVERIES_KEY, 0);
            tag.remove(ISSUE_KEY);
            return false;
        }

        int stuckTicks = tag.getInt(STUCK_TICKS_KEY) + 1;
        tag.putInt(STUCK_TICKS_KEY, stuckTicks);
        if (stuckTicks < POSITION_TIMEOUT) {
            return false;
        }

        int recoveries = tag.getInt(RECOVERIES_KEY);
        villager.getNavigation().stop();
        tag.putInt(STUCK_TICKS_KEY, 0);
        tag.putLong(LAST_POS_KEY, current.asLong());
        if (recoveries >= MAX_RECOVERIES) {
            tag.putString(ISSUE_KEY, "Маршрут не найден; повторная попытка через 10 секунд");
            tag.putLong(COOLDOWN_UNTIL_KEY, now + EXHAUSTED_COOLDOWN);
            tag.putInt(RECOVERIES_KEY, 0);
        } else {
            tag.putInt(RECOVERIES_KEY, recoveries + 1);
            tag.putString(ISSUE_KEY, "Перезапуск маршрута (" + (recoveries + 1) + "/" + MAX_RECOVERIES + ")");
            tag.putLong(COOLDOWN_UNTIL_KEY, now + RECOVERY_COOLDOWN);
        }
        return true;
    }
}
