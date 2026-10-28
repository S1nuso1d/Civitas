package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.trade.NeighborCivilization;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a small, persistent, physically visitable neighbor outpost.
 * Candidate sites are checked before any block is changed; player-built solid
 * structures are never cleared to make room for the settlement.
 */
public final class NeighborSettlementGenerator {
    private NeighborSettlementGenerator() { }

    public static BlockPos ensureGenerated(ServerLevel level, CivitasSavedData data,
                                           NeighborCivilization civilization, BlockPos origin) {
        if (level == null || data == null || civilization == null || origin == null) return null;
        if (civilization.siteGenerated() && civilization.hasSite()) return civilization.site();
        if (civilization.hasSite() && level.hasChunkAt(civilization.site())
                && level.getBlockState(civilization.site().above()).is(Blocks.BELL)) {
            civilization.markSiteGenerated();
            data.markTradeDataDirty();
            return civilization.site();
        }

        BlockPos site = findSafeSite(level, civilization, origin);
        if (site == null) return null;
        civilization.setSite(site, false);
        data.markTradeDataDirty();
        if (!canBuildAt(level, site)) return null;

        buildCabin(level, site.offset(-4, 0, 0));
        buildCabin(level, site.offset(4, 0, 0));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos path = site.offset(x, 0, z);
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    set(level, path, Blocks.GRAVEL);
                }
            }
        }
        set(level, site.above(), Blocks.BELL);
        set(level, site.above(2), Blocks.AIR);
        spawnVillagers(level, site, civilization);
        civilization.markSiteGenerated();
        data.markTradeDataDirty();
        return site;
    }

    private static BlockPos findSafeSite(ServerLevel level, NeighborCivilization civilization, BlockPos origin) {
        int seed = civilization.id().hashCode();
        double baseAngle = (Math.floorMod(seed, 360)) * Math.PI / 180.0D;
        // Search a broad deterministic spiral; this improves the chance of
        // finding a flat, unobstructed site without deleting trees or buildings.
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = baseAngle + attempt * Math.PI * (3.0D - Math.sqrt(5.0D));
            int radius = 192 + attempt * 16;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            // Load only chunks intersecting the candidate footprint; otherwise
            // hasChunkAt would reject every site crossing an unloaded boundary.
            for (int dx : new int[]{-7, 7}) {
                for (int dz : new int[]{-7, 7}) {
                    level.getChunk((x + dx) >> 4, (z + dz) >> 4);
                }
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos candidate = new BlockPos(x, y, z);
            if (canBuildAt(level, candidate)) return candidate;
        }
        return null;
    }

    private static boolean canBuildAt(ServerLevel level, BlockPos center) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                BlockPos ground = center.offset(x, 0, z);
                if (!level.hasChunkAt(ground)) return false;
                BlockState floor = level.getBlockState(ground);
                if (!floor.isSolid() || !level.getFluidState(ground).isEmpty()) return false;
                for (int y = 1; y <= 6; y++) {
                    BlockPos above = ground.above(y);
                    if (!level.hasChunkAt(above)) return false;
                    BlockState state = level.getBlockState(above);
                    if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)) return false;
                    if (!level.getFluidState(above).isEmpty()) return false;
                }
            }
        }
        return true;
    }

    private static void buildCabin(ServerLevel level, BlockPos ground) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                set(level, ground.offset(x, 0, z), Blocks.OAK_PLANKS);
                for (int y = 1; y <= 3; y++) {
                    boolean cornerOrWall = Math.abs(x) == 2 || Math.abs(z) == 2;
                    if (!cornerOrWall) continue;
                    boolean doorway = z == 2 && x == 0 && y <= 2;
                    boolean window = y == 2 && ((x == -2 && z == 0) || (x == 2 && z == 0));
                    set(level, ground.offset(x, y, z), doorway ? Blocks.AIR : window ? Blocks.GLASS_PANE : Blocks.OAK_LOG);
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) set(level, ground.offset(x, 4, z), Blocks.OAK_PLANKS);
        }
        set(level, ground.offset(0, 1, -1), Blocks.TORCH);
        set(level, ground.offset(0, 1, 1), Blocks.CRAFTING_TABLE);
        set(level, ground.offset(1, 1, 1), Blocks.BARREL);
    }

    private static void spawnVillagers(ServerLevel level, BlockPos site, NeighborCivilization civilization) {
        for (int i = 0; i < 3; i++) {
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) continue;
            double x = site.getX() + (i - 1) * 1.25D + 0.5D;
            double z = site.getZ() + 3.5D;
            villager.moveTo(x, site.getY() + 1.0D, z, i * 120.0F, 0.0F);
            villager.setCustomName(net.minecraft.network.chat.Component.literal(civilization.name() + " — житель"));
            villager.setCustomNameVisible(i == 0);
            villager.setPersistenceRequired();
            villager.getPersistentData().putBoolean("civitas.neighbor_resident", true);
            villager.getPersistentData().putString("civitas.neighbor_id", civilization.id().toString());
            level.addFreshEntity(villager);
        }
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    public static boolean visit(ServerPlayer player, CivitasSavedData data, NeighborCivilization civilization) {
        if (player == null || data == null || civilization == null || player.getServer() == null) return false;
        ServerLevel level = player.getServer().overworld();
        BlockPos origin = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        BlockPos site = ensureGenerated(level, data, civilization, origin);
        if (site == null || !civilization.siteGenerated()) return false;
        var tag = player.getPersistentData();
        if (!tag.getBoolean("civitas.return_saved")) {
            tag.putBoolean("civitas.return_saved", true);
            tag.putInt("civitas.return_x", player.blockPosition().getX());
            tag.putInt("civitas.return_y", player.blockPosition().getY());
            tag.putInt("civitas.return_z", player.blockPosition().getZ());
            tag.putFloat("civitas.return_yaw", player.getYRot());
            tag.putFloat("civitas.return_pitch", player.getXRot());
        }
        player.teleportTo(level, site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 6.0D,
                player.getYRot(), player.getXRot());
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Ты прибыл в поселение «" + civilization.name() + "». Вернуться можно командой /civitas return."));
        return true;
    }

    public static boolean returnToSavedPosition(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        var tag = player.getPersistentData();
        if (!tag.getBoolean("civitas.return_saved")) return false;
        ServerLevel level = player.getServer().overworld();
        int x = tag.getInt("civitas.return_x"), y = tag.getInt("civitas.return_y"), z = tag.getInt("civitas.return_z");
        float yaw = tag.getFloat("civitas.return_yaw"), pitch = tag.getFloat("civitas.return_pitch");
        tag.putBoolean("civitas.return_saved", false);
        player.teleportTo(level, x + 0.5D, y + 0.1D, z + 0.5D, yaw, pitch);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Ты вернулся из соседнего поселения."));
        return true;
    }
}
