package ru.civitas.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import ru.civitas.data.CivitasSavedData;

/**
 * Enforces the settlement's claimed-chunk protection on the logical server.
 *
 * Claims protect player edits and explosion damage, not arbitrary world simulation:
 * mob griefing, fire, fluids and third-party mod changes remain governed by their
 * own game rules/events.
 */
public final class TerritoryProtectionHandler {
    private static final String LAST_WARNING_TICK = "civitas.territory_protection.last_warning_tick";
    private static final int WARNING_COOLDOWN_TICKS = 40;

    private TerritoryProtectionHandler() { }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && deny(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && deny(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && deny(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Prevent explosions from removing blocks inside claimed overworld chunks. */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        event.getAffectedBlocks().removeIf(data::isChunkClaimed);
    }

    private static boolean deny(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return false;
        }

        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.isChunkClaimed(pos) || data.isOwner(player)) {
            return false;
        }

        // Right-click events may fire repeatedly (e.g. while using an item).
        // Keep the protection effective without flooding the action bar/chat.
        long now = level.getGameTime();
        boolean hasWarned = player.getPersistentData().contains(LAST_WARNING_TICK);
        long last = player.getPersistentData().getLong(LAST_WARNING_TICK);
        if (!hasWarned || now - last >= WARNING_COOLDOWN_TICKS) {
            player.getPersistentData().putLong(LAST_WARNING_TICK, now);
            player.sendSystemMessage(Component.literal(
                    "Эта территория принадлежит поселению Civitas. Только владелец может изменять блоки и взаимодействовать с ними."));
        }
        return true;
    }
}
