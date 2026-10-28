package ru.civitas.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import ru.civitas.data.CivitasSavedData;

public final class TerritoryProtectionHandler {
    private TerritoryProtectionHandler() { }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && deny(player, event.getPos())) event.setCanceled(true);
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && deny(player, event.getPos())) event.setCanceled(true);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && deny(player, event.getPos())) event.setCanceled(true);
    }

    private static boolean deny(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return false;
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.isChunkClaimed(pos) || data.isOwner(player)) return false;
        player.sendSystemMessage(Component.literal("Эта территория принадлежит поселению Civitas. Только владелец может изменять блоки и взаимодействовать с ними."));
        return true;
    }
}
