package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

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
        return Collections.unmodifiableList(new ArrayList<>(blocks.values()));
    }

    /** Check a whole layer before resources are charged. */
    public static boolean canPlaceLayer(ServerLevel level, BlockPos ground,
                                        BuildingDefinition.Type type, int layer) {
        for (Placement p : layer(type, layer)) {
            BlockPos target = ground.offset(p.relativePosition());
            if (!level.hasChunkAt(target)) return false;
            if (p.clearsBlock()) continue;
            Block expected = resolveBlock(p.blockId());
            BlockState current = level.getBlockState(target);
            if (!current.canBeReplaced() && !current.is(expected)) return false;
        }
        return true;
    }

    public static void placeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type, int layer) {
        List<BlockPos> deferredDoorUpdates = new ArrayList<>();
        for (Placement p : layer(type, layer)) {
            BlockPos target = ground.offset(p.relativePosition());
            if (!level.hasChunkAt(target)) continue;
            BlockState state = resolveState(p);
            // Door halves must exist together before neighbour updates run, otherwise
            // Minecraft may immediately drop the first half while the second is air.
            if (state.getBlock() instanceof DoorBlock) {
                level.setBlock(target, state, 2);
                deferredDoorUpdates.add(target.immutable());
            } else {
                level.setBlock(target, state, 3);
            }
        }
        for (BlockPos doorPos : deferredDoorUpdates) {
            level.updateNeighborsAt(doorPos, level.getBlockState(doorPos).getBlock());
        }
    }

    public static void removeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type, int layer) {
        for (Placement p : layer(type, layer)) {
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
        return state;
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
            put(b,0,1,0,"minecraft:crafting_table");
            put(b,1,1,0,"minecraft:barrel");
            put(b,0,1,2,"minecraft:red_bed");
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
