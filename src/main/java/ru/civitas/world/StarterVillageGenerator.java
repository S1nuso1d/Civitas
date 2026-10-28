package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import ru.civitas.block.ModBlocks;

/**
 * Creates one compact starter village in the Overworld. The location is stored
 * in CivitasSavedData so the village is not rebuilt on every login.
 */
public final class StarterVillageGenerator {
    private static final int SEARCH_RADIUS = 96;
    private static final int SEARCH_STEP = 8;
    private static final int HALF_SIZE = 14;

    private StarterVillageGenerator() {
    }

    public static BlockPos create(ServerLevel level, BlockPos origin) {
        BlockPos site = findSite(level, origin);
        int baseY = site.getY();
        int centerX = site.getX();
        int centerZ = site.getZ();
        BlockPos center = new BlockPos(centerX, baseY, centerZ);

        prepareGround(level, center);
        buildRoads(level, center);

        placeWarehouse(level, center);
        buildHouse(level, center, -7, -7, Blocks.COMPOSTER);
        buildHouse(level, center, 7, -7, Blocks.FLETCHING_TABLE);
        buildHouse(level, center, -7, 7, Blocks.CARTOGRAPHY_TABLE);
        buildHouse(level, center, 7, 7, Blocks.SMITHING_TABLE);
        placeVillageDecor(level, center);
        spawnVillagers(level, center);

        // Make the generated village the world's actual spawn, not merely a
        // one-time teleport destination for the first player.
        level.setDefaultSpawnPos(center.offset(0, 1, 2), 0.0F);
        return center;
    }

    private static BlockPos findSite(ServerLevel level, BlockPos origin) {
        for (int radius = 0; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += SEARCH_STEP) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (isSuitableSite(level, new BlockPos(x, y, z))) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }

        // Vanilla chooses a land spawn for a new world. This fallback ensures
        // a village can still be created in unusual custom terrain.
        int x = origin.getX();
        int z = origin.getZ();
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static boolean isSuitableSite(ServerLevel level, BlockPos center) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = -HALF_SIZE; dx <= HALF_SIZE; dx++) {
            for (int dz = -HALF_SIZE; dz <= HALF_SIZE; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                minY = Math.min(minY, surfaceY);
                maxY = Math.max(maxY, surfaceY);
                if (maxY - minY > 1) {
                    return false;
                }

                BlockPos floorPos = new BlockPos(x, surfaceY - 1, z);
                BlockState floor = level.getBlockState(floorPos);
                if (level.getFluidState(floorPos).isEmpty() == false || !isNaturalGround(floor)) {
                    return false;
                }

                for (int dy = 0; dy < 5; dy++) {
                    BlockState above = level.getBlockState(new BlockPos(x, surfaceY + dy, z));
                    if (!above.isAir() && !above.canBeReplaced() && !above.is(BlockTags.LEAVES)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND);
    }

    private static void prepareGround(ServerLevel level, BlockPos center) {
        for (int dx = -HALF_SIZE; dx <= HALF_SIZE; dx++) {
            for (int dz = -HALF_SIZE; dz <= HALF_SIZE; dz++) {
                BlockPos floor = center.offset(dx, -1, dz);
                level.setBlock(floor, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                for (int dy = 0; dy <= 7; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void buildRoads(ServerLevel level, BlockPos center) {
        for (int offset = -11; offset <= 11; offset++) {
            // Heightmap returns the first air block above the terrain. The path block
            // must replace the ground block at y = -1, not float in that air space.
            level.setBlock(center.offset(0, -1, offset), Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(center.offset(offset, -1, 0), Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (int signX : new int[]{-1, 1}) {
            for (int signZ : new int[]{-1, 1}) {
                int houseX = signX * 7;
                int entranceZ = signZ * 7 - 2;
                for (int x = 0; x <= 7; x++) {
                    level.setBlock(center.offset(signX * x, -1, entranceZ), Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
                }
                for (int z = 0; z <= Math.abs(entranceZ); z++) {
                    level.setBlock(center.offset(houseX, -1, Integer.signum(entranceZ) * z), Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void placeWarehouse(ServerLevel level, BlockPos center) {
        level.setBlock(center, ModBlocks.WAREHOUSE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(1, 0, 0), Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(2, 0, 0), Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

        if (level.getBlockEntity(center) instanceof ru.civitas.block.SettlementWarehouseBlockEntity warehouse) {
            warehouse.setItem(0, new ItemStack(Items.OAK_LOG, 16));
            warehouse.setItem(1, new ItemStack(Items.BREAD, 16));
            warehouse.setItem(2, new ItemStack(Items.WHEAT_SEEDS, 16));
            warehouse.setItem(3, new ItemStack(Items.COBBLESTONE, 32));
            warehouse.setItem(4, new ItemStack(Items.TORCH, 16));
            warehouse.setItem(5, new ItemStack(Items.OAK_SAPLING, 4));
        }
    }

    private static void buildHouse(ServerLevel level, BlockPos center, int offsetX, int offsetZ, Block jobSite) {
        BlockPos house = center.offset(offsetX, 0, offsetZ);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(house.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        for (int y = 1; y <= 3; y++) {
            for (int edge = -2; edge <= 2; edge++) {
                setHouseWall(level, house.offset(edge, y, -2), edge, y, true);
                setHouseWall(level, house.offset(edge, y, 2), edge, y, false);
                setHouseWall(level, house.offset(-2, y, edge), edge, y, false);
                setHouseWall(level, house.offset(2, y, edge), edge, y, false);
            }
        }

        // A simple, open doorway keeps the starter buildings walkable.
        level.setBlock(house.offset(0, 1, -2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(house.offset(0, 2, -2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        level.setBlock(house.offset(-2, 2, 0), Blocks.GLASS_PANE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(house.offset(2, 2, 0), Blocks.GLASS_PANE.defaultBlockState(), Block.UPDATE_ALL);

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setBlock(house.offset(x, 4, z), Blocks.OAK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        // Two-block bed: the head is one block north of the foot.
        BlockPos bedFoot = house.offset(-1, 1, 1);
        BlockState foot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(BedBlock.FACING, Direction.NORTH);
        BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
        level.setBlock(bedFoot, foot, Block.UPDATE_ALL);
        level.setBlock(bedFoot.north(), head, Block.UPDATE_ALL);

        level.setBlock(house.offset(1, 1, 1), jobSite.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(house.offset(-1, 2, -1), Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void setHouseWall(ServerLevel level, BlockPos pos, int edge, int y, boolean frontOrBack) {
        boolean corner = Math.abs(edge) == 2;
        if (corner) {
            level.setBlock(pos, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
        } else if (y == 2 && !frontOrBack) {
            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void placeVillageDecor(ServerLevel level, BlockPos center) {
        level.setBlock(center.offset(-3, 0, 0), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(-3, 1, 0), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(3, 0, 0), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(3, 1, 0), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(0, 0, 3), Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(center.offset(0, 0, -3), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void spawnVillagers(ServerLevel level, BlockPos center) {
        int[][] positions = {
                {-7, -7}, {7, -7}, {-7, 7}, {7, 7}
        };
        for (int[] offset : positions) {
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                continue;
            }
            BlockPos house = center.offset(offset[0], 1, offset[1]);
            villager.moveTo(house.getX() + 0.5D, house.getY(), house.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(villager);
        }
    }
}
