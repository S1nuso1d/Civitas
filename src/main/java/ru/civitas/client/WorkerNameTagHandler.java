package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reduces name-tag clutter locally without changing server-side entity data. */
@Mod.EventBusSubscriber(modid = "civitas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorkerNameTagHandler {
    private static final double CLOSE_DISTANCE_SQUARED = 16.0D;

    private WorkerNameTagHandler() {
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.player.distanceToSqr(villager) <= CLOSE_DISTANCE_SQUARED) {
            return;
        }
        if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() == villager) {
            return;
        }

        // RenderNameTagEvent is not cancelable in this Forge version.
        // Use its supported visibility flag instead of Event#setCanceled().
        event.setCanRender(false);
    }
}
