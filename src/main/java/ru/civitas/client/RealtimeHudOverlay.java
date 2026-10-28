package ru.civitas.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the realtime HUD using the Forge 1.21.1 layered GUI API. */
@Mod.EventBusSubscriber(modid = "civitas", bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RealtimeHudOverlay {
    private RealtimeHudOverlay() { }

    @SubscribeEvent
    public static void addLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                ResourceLocation.fromNamespaceAndPath("civitas", "realtime_hud"),
                RealtimeHud::render
        );
    }
}
