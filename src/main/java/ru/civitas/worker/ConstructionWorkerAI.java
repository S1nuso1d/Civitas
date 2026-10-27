package ru.civitas.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.world.SettlementBuildingManager;

/** Makes a real villager travel to construction and work only when near the site. */
public final class ConstructionWorkerAI {
    private static final String ROLE_KEY = "civitas.worker_role";
    private ConstructionWorkerAI() {}

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || !event.level.dimension().equals(Level.OVERWORLD)
                || level.getGameTime() % 20 != 0) return;

        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        var orders = data.getConstructionOrders();
        if (orders.isEmpty()) return;

        // A builder completes one order at a time.
        CivitasSavedData.ConstructionOrder order = orders.get(0);
        BlockPos site = new BlockPos(order.x(), order.y(), order.z());
        if (!level.hasChunkAt(site)) return;

        Villager builder = null;
        double nearest = Double.MAX_VALUE;
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(site).inflate(64, 24, 64))) {
            if (!"builder".equals(villager.getPersistentData().getString(ROLE_KEY))) continue;
            double distance = villager.distanceToSqr(site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D);
            if (distance < nearest) { nearest = distance; builder = villager; }
        }
        if (builder == null) return; // Queue waits for a builder.
        builder.setCustomName(Component.literal("Строитель Civitas"));
        builder.setCustomNameVisible(true);
        builder.setPersistenceRequired();

        double distance = builder.distanceToSqr(site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D);
        if (distance > 25.0D) {
            if (builder.getNavigation().isDone()) {
                builder.getNavigation().moveTo(site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D, 0.85D);
            }
            return;
        }

        builder.getNavigation().stop();
        // Visually paced construction: a layer every three seconds while nearby.
        if (level.getGameTime() % 60 == 0) {
            SettlementBuildingManager.placeConstructionLayer(level, data, order);
        }
    }
}
