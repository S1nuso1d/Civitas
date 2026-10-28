package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical block layout shared by construction and safe cancellation. */
public final class BuildingBlueprint {
    public record Placement(BlockPos relativePosition, BlockState state) {}

    private BuildingBlueprint() {}

    public static List<Placement> layer(BuildingDefinition.Type type, int layer) {
        if (type == null) throw new IllegalArgumentException("Building type must not be null");
        BuildingDefinition definition = BuildingDefinition.get(type);
        if (layer < 1 || layer > definition.totalLayers())
            throw new IllegalArgumentException("Layer out of range for " + type.id() + ": " + layer);

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        switch (type) {
            case CABIN -> cabin(blocks, layer);
            case FARM -> farm(blocks, layer);
            case WORKSHOP -> workshop(blocks, layer);
        }
        List<Placement> result = new ArrayList<>(blocks.size());
        blocks.forEach((pos, state) -> result.add(new Placement(pos, state)));
        return Collections.unmodifiableList(result);
    }

    public static void placeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type, int layer) {
        for (Placement p : layer(type, layer)) {
            BlockPos target = ground.offset(p.relativePosition());
            if (level.hasChunkAt(target)) level.setBlock(target, p.state(), 3);
        }
    }

    public static void removeLayer(ServerLevel level, BlockPos ground, BuildingDefinition.Type type, int layer) {
        for (Placement p : layer(type, layer)) {
            if (p.state().isAir()) continue;
            BlockPos target = ground.offset(p.relativePosition());
            if (level.hasChunkAt(target) && level.getBlockState(target).is(p.state().getBlock()))
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void put(Map<BlockPos, BlockState> b, int x, int y, int z, Block block) {
        b.put(new BlockPos(x, y, z), block.defaultBlockState());
    }

    private static void cabin(Map<BlockPos, BlockState> b, int y) {
        if (y == 1) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) put(b,x,1,z,Blocks.OAK_PLANKS);
            put(b,0,1,0,Blocks.CRAFTING_TABLE); put(b,1,1,0,Blocks.BARREL); put(b,0,1,2,Blocks.RED_BED);
        } else if (y <= 4) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
                if (Math.abs(x)!=2 && Math.abs(z)!=2) continue;
                if (y==2 && x==0 && z==-2) continue;
                put(b,x,y,z,y==3 && Math.abs(x)==2 && z==0 ? Blocks.GLASS : Blocks.OAK_LOG);
            }
        } else {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) put(b,x,5,z,Blocks.OAK_PLANKS);
            put(b,0,1,-2,Blocks.AIR); put(b,0,2,-2,Blocks.AIR);
        }
    }

    private static void farm(Map<BlockPos, BlockState> b, int y) {
        if (y==1) {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
                if (Math.abs(x)==3 || Math.abs(z)==3) put(b,x,1,z,Blocks.OAK_FENCE);
                else if (x==0 && z==0) put(b,x,1,z,Blocks.WATER);
                else put(b,x,1,z,Blocks.FARMLAND);
            }
            put(b,0,1,-3,Blocks.AIR); put(b,1,1,0,Blocks.COMPOSTER); put(b,-1,1,0,Blocks.BARREL);
        } else {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
                if (Math.abs(x)==3 || Math.abs(z)==3 || (x==0 && z==0)) continue;
                b.put(new BlockPos(x,2,z), Blocks.WHEAT.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.CropBlock.AGE,0));
            }
        }
    }

    private static void workshop(Map<BlockPos, BlockState> b, int y) {
        if (y==1) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) put(b,x,1,z,Blocks.COBBLESTONE);
            put(b,0,1,0,Blocks.CRAFTING_TABLE); put(b,1,1,0,Blocks.FURNACE);
            put(b,-1,1,0,Blocks.BARREL); put(b,0,1,1,Blocks.ANVIL);
        } else if (y<=3) {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
                if (Math.abs(x)!=2 && Math.abs(z)!=2) continue;
                if (y==2 && x==0 && z==-2) continue;
                put(b,x,y,z,Blocks.COBBLESTONE);
            }
        } else {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) put(b,x,4,z,Blocks.OAK_PLANKS);
            put(b,0,2,-2,Blocks.AIR);
        }
    }
}
