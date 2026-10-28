package ru.civitas.trade;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import ru.civitas.data.CivitasSavedData;

/** Bounded daily simulation for distant societies; never spawns entities or loads chunks. */
public final class NeighborCivilizationManager {
    private NeighborCivilizationManager() { }
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || level.getGameTime() % 1200L != 0L) return;
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.isFounded() || data.getNeighborCivilizations().isEmpty()) return;
        long day = Math.max(0L, level.getDayTime() / 24000L);
        boolean changed = false;
        for (NeighborCivilization civilization : data.getNeighborCivilizations().values()) {
            if (civilization.advanceToDay(day) > 0) changed = true;
        }
        if (changed) data.markTradeDataDirty();
    }
}