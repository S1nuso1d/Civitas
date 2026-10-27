package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.civitas.block.SettlementWarehouseBlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Validates a build site, charges the physical warehouse and places a small structure. */
public final class SettlementBuildingManager {
    public enum BuildingType { CABIN, FARM, WORKSHOP }
    public record Cost(String itemId, int count) {}
    public record BuildResult(boolean success, String message) {}

    private SettlementBuildingManager() {}

    public static String describeCosts(BuildingType type) {
        return switch (type) {
            case CABIN -> "32 oak planks, 8 oak logs, 4 glass";
            case FARM -> "49 dirt, 24 wheat seeds, 1 water bucket";
            case WORKSHOP -> "32 oak planks, 25 cobblestone, 1 crafting table";
        };
    }

    public static BuildResult build(ServerLevel level, BlockPos warehousePos,
                                    SettlementWarehouseBlockEntity warehouse, BuildingType type) {
        BlockPos site = findSite(level, warehousePos, type);
        if (site == null) return new BuildResult(false,
                "Нет свободной площадки рядом с деревней. Освободи участок примерно 7×7 блоков.");

        Map<String, Integer> cost = cost(type);
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (count(warehouse, entry.getKey()) < entry.getValue()) {
                return new BuildResult(false, "Не хватает ресурсов на складе: нужно " + describeCosts(type) + ".");
            }
        }
        // All checks happen before any resource is removed or block is placed.
        cost.forEach((id, amount) -> remove(warehouse, id, amount));
        switch (type) {
            case CABIN -> buildCabin(level, site);
            case FARM -> buildFarm(level, site);
            case WORKSHOP -> buildWorkshop(level, site);
        }
        return new BuildResult(true, "Построено: " + label(type) + ". Материалы списаны с физического склада.");
    }

    private static Map<String, Integer> cost(BuildingType type) {
        Map<String, Integer> cost = new LinkedHashMap<>();
        switch (type) {
            case CABIN -> { cost.put("minecraft:oak_planks", 32); cost.put("minecraft:oak_log", 8); cost.put("minecraft:glass", 4); }
            case FARM -> { cost.put("minecraft:dirt", 49); cost.put("minecraft:wheat_seeds", 24); cost.put("minecraft:water_bucket", 1); }
            case WORKSHOP -> { cost.put("minecraft:oak_planks", 32); cost.put("minecraft:cobblestone", 25); cost.put("minecraft:crafting_table", 1); }
        }
        return cost;
    }

    private static String label(BuildingType type) {
        return switch (type) { case CABIN -> "жилой дом"; case FARM -> "ферма"; case WORKSHOP -> "мастерская"; };
    }

    private static BlockPos findSite(ServerLevel level, BlockPos center, BuildingType type) {
        BlockPos[] candidates = switch (type) {
            case CABIN -> new BlockPos[]{center.offset(9, 0, 0), center.offset(-9, 0, 0), center.offset(0, 0, 9), center.offset(0, 0, -9)};
            case FARM -> new BlockPos[]{center.offset(0, 0, 11), center.offset(11, 0, 0), center.offset(-11, 0, 0), center.offset(0, 0, -11)};
            case WORKSHOP -> new BlockPos[]{center.offset(-9, 0, 0), center.offset(0, 0, -9), center.offset(9, 0, 0), center.offset(0, 0, 9)};
        };
        for (BlockPos candidate : candidates) {
            int x = candidate.getX(), z = candidate.getZ();
            if (!level.hasChunkAt(candidate)) continue;
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos ground = new BlockPos(x, groundY, z);
            if (!level.getBlockState(ground).isSolid()) continue;
            boolean clear = true;
            int radius = 3;
            int height = type == BuildingType.FARM ? 3 : 5;
            for (int dx = -radius; dx <= radius && clear; dx++) {
                for (int dz = -radius; dz <= radius && clear; dz++) {
                    for (int dy = 1; dy <= height; dy++) {
                        BlockPos p = ground.offset(dx, dy, dz);
                        if (!level.hasChunkAt(p) || !level.getBlockState(p).canBeReplaced()) { clear = false; break; }
                    }
                }
            }
            if (clear) return ground;
        }
        return null;
    }

    private static void buildCabin(ServerLevel level, BlockPos ground) {
        for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) {
            set(level, ground.offset(dx,1,dz), (Math.abs(dx)==2 || Math.abs(dz)==2) ? Blocks.OAK_PLANKS : Blocks.OAK_PLANKS);
        }
        for (int y=2; y<=4; y++) for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) {
            boolean wall = Math.abs(dx)==2 || Math.abs(dz)==2;
            if (!wall) continue;
            if (y==2 && dx==0 && dz==-2) continue; // door opening
            if (y==3 && ((dx==2 && dz==0)||(dx==-2 && dz==0))) set(level, ground.offset(dx,y,dz), Blocks.GLASS);
            else set(level, ground.offset(dx,y,dz), Blocks.OAK_LOG);
        }
        for (int dx=-3; dx<=3; dx++) for (int dz=-3; dz<=3; dz++) set(level, ground.offset(dx,5,dz), Blocks.SPRUCE_PLANKS);
        set(level, ground.offset(0,1,-2), Blocks.AIR);
        set(level, ground.offset(0,2,-2), Blocks.AIR);
        set(level, ground.offset(0,1,0), Blocks.CRAFTING_TABLE);
        set(level, ground.offset(1,1,0), Blocks.BARREL);
        set(level, ground.offset(0,1,2), Blocks.RED_BED);
    }

    private static void buildFarm(ServerLevel level, BlockPos ground) {
        for (int dx=-3; dx<=3; dx++) for (int dz=-3; dz<=3; dz++) {
            if (Math.abs(dx)==3 || Math.abs(dz)==3) set(level, ground.offset(dx,1,dz), Blocks.OAK_FENCE);
            else if (dx==0 && dz==0) set(level, ground.offset(dx,1,dz), Blocks.WATER);
            else {
                set(level, ground.offset(dx,1,dz), Blocks.FARMLAND);
                level.setBlock(ground.offset(dx,2,dz), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 0), 3);
            }
        }
        set(level, ground.offset(0,1,-3), Blocks.AIR);
        set(level, ground.offset(1,1,0), Blocks.COMPOSTER);
        set(level, ground.offset(-1,1,0), Blocks.BARREL);
    }

    private static void buildWorkshop(ServerLevel level, BlockPos ground) {
        for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) {
            set(level, ground.offset(dx,1,dz), Blocks.COBBLESTONE);
        }
        for (int y=2; y<=3; y++) for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) {
            if (Math.abs(dx)==2 || Math.abs(dz)==2) {
                if (y==2 && dx==0 && dz==-2) continue;
                set(level, ground.offset(dx,y,dz), Blocks.COBBLESTONE);
            }
        }
        for (int dx=-3; dx<=3; dx++) for (int dz=-3; dz<=3; dz++) set(level, ground.offset(dx,4,dz), Blocks.OAK_PLANKS);
        set(level, ground.offset(0,1,-2), Blocks.AIR);
        set(level, ground.offset(0,1,0), Blocks.CRAFTING_TABLE);
        set(level, ground.offset(1,1,0), Blocks.FURNACE);
        set(level, ground.offset(-1,1,0), Blocks.BARREL);
        set(level, ground.offset(0,1,1), Blocks.ANVIL);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
    }

    private static int count(SettlementWarehouseBlockEntity warehouse, String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        int total = 0;
        for (int slot=0; slot<warehouse.getContainerSize(); slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (!stack.isEmpty() && stack.getItem()==item) total += stack.getCount();
        }
        return total;
    }

    private static void remove(SettlementWarehouseBlockEntity warehouse, String id, int amount) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        int remaining=amount;
        for (int slot=0; slot<warehouse.getContainerSize() && remaining>0; slot++) {
            ItemStack stack=warehouse.getItem(slot);
            if (stack.isEmpty() || stack.getItem()!=item) continue;
            int removed=Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            if (stack.isEmpty()) warehouse.setItem(slot, ItemStack.EMPTY);
            else warehouse.setChanged();
            remaining-=removed;
        }
    }
}
