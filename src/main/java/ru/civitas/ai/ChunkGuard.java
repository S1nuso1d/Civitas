package ru.civitas.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Prevents worker AI from interpreting unloaded targets as missing blocks.
 * A blocked target is deliberately retained; vanilla navigation and the worker
 * routine may resume once the chunk is loaded again.
 */
public final class ChunkGuard {
    private ChunkGuard() {
    }

    public static boolean targetAvailable(ServerLevel level, Villager villager, BlockPos target, String statusKey) {
        if (target == null || level.hasChunkAt(target)) {
            return true;
        }
        villager.getNavigation().stop();
        villager.getPersistentData().putString(statusKey, "Ждёт загрузки целевого чанка");
        return false;
    }
}
