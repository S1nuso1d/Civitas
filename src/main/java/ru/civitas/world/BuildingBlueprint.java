package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical, data-only block layout shared by construction and safe cancellation.
 * The block registry is resolved only when the blueprint is used in a live world,
 * so layout tests do not need to start the complete Minecraft runtime.
 */
public final class BuildingBlueprint {
    public record Placement(BlockPos relativePosition, String blockId, int cropAge) {
        public Placement {
            if (relativePosition == null || blockId == null || blockId.isBlank()) {
                throw new IllegalArgumentException("Placement position and block ID are required");
            }
            if (cropAge < -1) throw new IllegalArgumentException("cropAge must be -1 or greater");
        }

        public boolean clearsBlock() {
            return "minecraft:air".equals(blockId);
        }
    }

    private BuildingBlueprint() {}

    public static List<Placement> layer(BuildingDefinition.Type type, int layer) {
        return layer(type, layer, 0);
    }

    /** Returns a blueprint layer rotated clockwise by 0, 90, 180 or 270 degrees. */
    public static List<Placement> layer(BuildingDefinition.Type type, int layer, int rotationDegrees) {
        int normalizedRotation = Math.floorMod(rotationDegrees, 360);
        if (normalizedRotation % 90 != 0) {
            throw new IllegalArgumentException("Blueprint rotation must be 0, 90, 180 or 270 degrees");
        }
        if (type == null) throw new IllegalArgumentException("Building type must not be null");
        BuildingDefinition definition = BuildingDefinition.get(type);
        if (layer < 1 || layer > definition.totalLayers())
            throw new IllegalArgumentException("Layer out of range for " + type.id() + ": " + layer);

        Map<BlockPos, Placement> blocks = new LinkedHashMap<>();
        switch (type) {
            case CABIN -> cabin(blocks, layer);
            case FARM -> farm(blocks, layer);
            case WORKSHOP -> workshop(blocks, layer);
        }
        if (normalizedRotation == 0) {
            return Collections.unmodifiableList(new ArrayList<>(blocks.values()));
        }
        List<Placement> rotated = new ArrayList<>(blocks.size());
        for (Placement placement : blocks.values()) {
            BlockPos pos = placement.relativePosition();
            BlockPos turned = switch (normalizedRotation) {
                case 90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
                case 180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
                case 270 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
                default -> throw new IllegalStateException("Unexpected normalized rotation");
            };
            rotated.add(new Placement(turned, placement.blockId(), placement.cropAge()));
        }
        return Collections.unmodifiableList(rotated);
    }

    /** Check a whole layer before resources are charged. */
    public static boolean canPlaceLayer(ServerLevel level, BlockPos ground,
                                        BuildingDefinition.Type type, int layer) {
        return canPlaceLayer(level, ground, type, layer, 0);
    }

    public static boolean canPlaceLayer(ServerLevel level, BlockPos ground,
                                        BuildingDefinition.Type type, int layer, int rotationDegrees) {
        for (Placement p : layer(type, layer, rotationDegrees)) {
            BlockPos target = ground.offset(p.relativePosition());
            if (!level.hasChunkAt(target)) return false;
            BlockState current = level.getBlockState(target);
            if (p.clearsBlock()) {
                // Air placements are doorway/entrance gaps, not permission to
                // erase a solid block a player put there after ordering a build.
                if (!current.isAir() && !current.canBeReplaced() && !current.is(BlockTags.LEAVES)) {
                    return false;
                }
                if (!level.getFluidState(target).isEmpty()) return false;
                continue;
            }
            Block expected = resolveBlock(p.blockId());
            if (!current.canBeReplaced() && !current.is(expected)) return false;
        }
        return true;
    }

    public static void placeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type, int layer) {
        placeLayer(level, ground, type, layer, 0);
    }

    public static void placeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type,
                                  int layer, int rotationDegrees) {
        List<BlockPos> deferredDoorUpdates = new ArrayList<>();
        List<BlockPos> deferredBedUpdates = new ArrayList<>();
        for (Placement p : layer(type, layer, rotationDegrees)) {
            BlockPos target = ground.offset(p.relativePosition());
            if (!level.hasChunkAt(target)) continue;
            BlockState state = resolveState(p, rotationDegrees);
            // Door halves must exist together before neighbour updates run, otherwise
            // Minecraft may immediately drop the first half while the second is air.
            if (state.getBlock() instanceof DoorBlock) {
                level.setBlock(target, state, 2);
                deferredDoorUpdates.add(target.immutable());
            } else if (state.getBlock() instanceof BedBlock) {
                // Beds have two halves too. Place both without neighbour updates,
                // then notify neighbours only after the foot and head are present.
                level.setBlock(target, state, 2);
                deferredBedUpdates.add(target.immutable());
            } else {
                level.setBlock(target, state, 3);
            }
        }
        for (BlockPos doorPos : deferredDoorUpdates) {
            level.updateNeighborsAt(doorPos, level.getBlockState(doorPos).getBlock());
        }
        for (BlockPos bedPos : deferredBedUpdates) {
            level.updateNeighborsAt(bedPos, level.getBlockState(bedPos).getBlock());
        }
    }

    public static void removeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type, int layer) {
        removeLayer(level, ground, type, layer, 0);
    }

    public static void removeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type,
                                   int layer, int rotationDegrees) {
        for (Placement p : layer(type, layer, rotationDegrees)) {
            if (p.clearsBlock()) continue;
            BlockPos target = ground.offset(p.relativePosition());
            if (level.hasChunkAt(target) && level.getBlockState(target).is(resolveBlock(p.blockId()))) {
                level.setBlock(target, resolveState(new Placement(p.relativePosition(), "minecraft:air", -1)), 3);
            }
        }
    }

    private static Block resolveBlock(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalArgumentException("Unknown block in Civitas blueprint: " + id);
        }
        return BuiltInRegistries.BLOCK.get(key);
    }

    private static BlockState resolveState(Placement placement) {
        return resolveState(placement, 0);
    }

    private static BlockState resolveState(Placement placement, int rotationDegrees) {
        Block block = resolveBlock(placement.blockId());
        BlockState state = block.defaultBlockState();
        if (placement.cropAge() >= 0) {
            if (!(block instanceof CropBlock crop) || !state.hasProperty(CropBlock.AGE)) {
                throw new IllegalArgumentException("Crop age set for a non-crop block: " + placement.blockId());
            }
            state = state.setValue(CropBlock.AGE, Math.min(placement.cropAge(), crop.getMaxAge()));
        }
        // Blueprint doors are placed as a complete two-block pair in one layer,
        // preventing the lower half from being removed before the upper half is built.
        if (block instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
            state = state.setValue(DoorBlock.HALF,
                    placement.relativePosition().getY() == 3 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        }
        if (block instanceof BedBlock && state.hasProperty(BedBlock.PART)
                && state.hasProperty(BedBlock.FACING)) {
            BlockPos pos = placement.relativePosition();
            boolean foot = switch (Math.floorMod(rotationDegrees, 360)) {
                case 0 -> pos.getX() == -1 && pos.getZ() == 1;
                case 90 -> pos.getX() == -1 && pos.getZ() == -1;
                case 180 -> pos.getX() == 1 && pos.getZ() == -1;
                case 270 -> pos.getX() == 1 && pos.getZ() == 1;
                default -> false;
            };
            state = state.setValue(BedBlock.PART, foot ? BedPart.FOOT : BedPart.HEAD);
            // Facing is set before the generic horizontal-state rotation below.
            state = state.setValue(BedBlock.FACING, Direction.NORTH);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Rotation rotation = switch (Math.floorMod(rotationDegrees, 360)) {
                case 0 -> Rotation.NONE;
                case 90 -> Rotation.CLOCKWISE_90;
                case 180 -> Rotation.CLOCKWISE_180;
                case 270 -> Rotation.COUNTERCLOCKWISE_90;
                default -> throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees");
            };
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        return state;
    }

    /**
     * Adds a visible exterior upgrade to an existing cabin without replacing
     * the original shell or furnishings. Only upgrade-specific positions are touched.
     */
    public static List<Placement> houseUpgrade(int newLevel, int rotationDegrees) {
        if (newLevel < 2 || newLevel > 3) {
            throw new IllegalArgumentException("House upgrade level must be 2 or 3");
        }
        int rotation = Math.floorMod(rotationDegrees, 360);
        if (rotation % 90 != 0) {
            throw new IllegalArgumentException("House upgrade rotation must be a multiple of 90 degrees");
        }
        Map<BlockPos, Placement> blocks = new LinkedHashMap<>();
        if (newLevel == 2) {
            for (int x = -1; x <= 1; x++) {
                put(blocks, x, 1, -3, "minecraft:oak_slab");
            }
            put(blocks, -2, 2, -3, "minecraft:oak_fence");
            put(blocks, 2, 2, -3, "minecraft:oak_fence");
            put(blocks, -1, 2, -3, "minecraft:oak_fence");
            put(blocks, 1, 2, -3, "minecraft:oak_fence");
            put(blocks, 0, 2, -3, "minecraft:oak_stairs");
            put(blocks, 0, 3, -3, "minecraft:lantern");
        } else {
            for (int y = 1; y <= 4; y++) {
                put(blocks, 3, y, 2, "minecraft:bricks");
            }
            put(blocks, 3, 4, 1, "minecraft:lantern");
            put(blocks, -3, 4, 2, "minecraft:lantern");
        }
        List<Placement> rotated = new ArrayList<>(blocks.size());
        for (Placement placement : blocks.values()) {
            BlockPos pos = placement.relativePosition();
            BlockPos turned = switch (rotation) {
                case 0 -> pos;
                case 90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
                case 180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
                case 270 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
                default -> throw new IllegalStateException("Unexpected normalized rotation");
            };
            rotated.add(new Placement(turned, placement.blockId(), placement.cropAge()));
        }
        return Collections.unmodifiableList(rotated);
    }

    public static boolean canPlaceHouseUpgrade(ServerLevel level, BlockPos ground,
                                                int newLevel, int rotationDegrees) {
        for (Placement placement : houseUpgrade(newLevel, rotationDegrees)) {
            BlockPos target = ground.offset(placement.relativePosition());
            if (!level.hasChunkAt(target)) return false;
            BlockState current = level.getBlockState(target);
            if (!current.isAir() && !current.canBeReplaced()) return false;
            if (!level.getFluidState(target).isEmpty()) return false;
        }
        return true;
    }

    public static void placeHouseUpgrade(ServerLevel level, BlockPos ground,
                                         int newLevel, int rotationDegrees) {
        for (Placement placement : houseUpgrade(newLevel, rotationDegrees)) {
            BlockPos target = ground.offset(placement.relativePosition());
            level.setBlock(target, resolveState(placement, rotationDegrees), 3);
        }
    }

    private static void put(Map<BlockPos, Placement> b, int x, int y, int z, String blockId) {
        b.put(new BlockPos(x, y, z), new Placement(new BlockPos(x, y, z), blockId, -1));
    }

    private static void putCrop(Map<BlockPos, Placement> b, int x, int y, int z, int age) {
        BlockPos pos = new BlockPos(x, y, z);
        b.put(pos, new Placement(pos, "minecraft:wheat", age));
    }

    private static void cabin(Map<BlockPos, Placement> b, int y) {
        if (y == 1) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) put(b,x,1,z,"minecraft:oak_planks");
            // Furnishings sit on top of the plank floor, not in the floor layer.
            put(b,1,2,0,"minecraft:crafting_table");
            put(b,1,2,1,"minecraft:barrel");
            // One complete two-block bed: foot south, head north, both at y=2.
            put(b,-1,2,1,"minecraft:red_bed");
            put(b,-1,2,0,"minecraft:red_bed");
        } else if (y <= 4) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
                if (Math.abs(x)!=2 && Math.abs(z)!=2) continue;
                if (x==0 && z==-2) continue;
                put(b,x,y,z,y==3 && Math.abs(x)==2 && z==0 ? "minecraft:glass" : "minecraft:oak_log");
            }
            if (y == 2) {
                put(b,0,2,-2,"minecraft:oak_door");
                put(b,0,3,-2,"minecraft:oak_door");
            }
        } else {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) put(b,x,5,z,"minecraft:oak_planks");
        }
    }

    private static void farm(Map<BlockPos, Placement> b, int y) {
        if (y==1) {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
                if (Math.abs(x)==3 || Math.abs(z)==3) put(b,x,1,z,"minecraft:oak_fence");
                else if (x==0 && z==0) put(b,x,1,z,"minecraft:water");
                else put(b,x,1,z,"minecraft:farmland");
            }
            put(b,0,1,-3,"minecraft:air");
            put(b,1,1,0,"minecraft:composter");
            put(b,-1,1,0,"minecraft:barrel");
        } else {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
                if (Math.abs(x)==3 || Math.abs(z)==3 || (x==0 && z==0)) continue;
                putCrop(b,x,2,z,0);
            }
        }
    }

    private static void workshop(Map<BlockPos, Placement> b, int y) {
        if (y==1) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) put(b,x,1,z,"minecraft:cobblestone");
            put(b,0,1,0,"minecraft:crafting_table");
            put(b,1,1,0,"minecraft:furnace");
            put(b,-1,1,0,"minecraft:barrel");
            put(b,0,1,1,"minecraft:anvil");
        } else if (y<=3) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
                if (Math.abs(x)!=2 && Math.abs(z)!=2) continue;
                if (x==0 && z==-2) continue;
                put(b,x,y,z,"minecraft:cobblestone");
            }
            if (y == 2) {
                put(b,0,2,-2,"minecraft:oak_door");
                put(b,0,3,-2,"minecraft:oak_door");
            }
        } else {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) put(b,x,4,z,"minecraft:oak_planks");
        }
    }
}
