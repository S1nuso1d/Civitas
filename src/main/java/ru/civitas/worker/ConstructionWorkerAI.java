package ru.civitas.worker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.world.SettlementBuildingManager;
/** Advances persisted construction orders one layer per second. */
public final class ConstructionWorkerAI {
    private ConstructionWorkerAI() {}
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if(event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || event.level.dimension()!=Level.OVERWORLD) return;
        if(level.getGameTime()%20!=0)return;
        CivitasSavedData data=CivitasSavedData.get(level.getServer());
        for(CivitasSavedData.ConstructionOrder order:data.getConstructionOrders()) {
            if(level.hasChunkAt(new BlockPos(order.x(),order.y(),order.z()))) SettlementBuildingManager.placeConstructionLayer(level,data,order);
        }
    }
}