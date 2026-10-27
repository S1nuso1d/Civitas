package ru.civitas;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ru.civitas.block.ModBlocks;
import ru.civitas.command.CivitasCommands;
import ru.civitas.item.ModItems;

@Mod(CivitasMod.MOD_ID)
public final class CivitasMod {
    public static final String MOD_ID = "civitas";
    private static final String CODEX_RECEIVED_KEY = "civitas.codex_received";

    public CivitasMod(FMLJavaModLoadingContext context) {
        ModBlocks.BLOCKS.register(context.getModEventBus());
        ModItems.ITEMS.register(context.getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getPersistentData().getBoolean(CODEX_RECEIVED_KEY)) {
            return;
        }

        ItemStack codex = ModItems.CIVILIZATION_CODEX.get().getDefaultInstance();
        if (!player.getInventory().add(codex)) {
            player.drop(codex, false);
        }
        player.getPersistentData().putBoolean(CODEX_RECEIVED_KEY, true);
        player.sendSystemMessage(Component.translatable("message.civitas.welcome"));
        player.sendSystemMessage(Component.literal("Подсказка: используй Кодекс цивилизации в инвентаре, чтобы открыть панель управления."));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CivitasCommands.register(event);
    }
}
