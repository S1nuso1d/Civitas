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
import ru.civitas.data.CivitasSavedData;

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
                                    SettlementWarehouseBlockEntity warehouse, BuildingType type, CivitasSavedData data) {
        BlockPos site = findSite(level, warehousePos, type, data);
        if (site == null) return new BuildResult(false,
                "Нет свободной площадки рядом с деревней. Освободи участок примерно 7×7 блоков.");

        if (data.hasBuildingNear(site, 5)) return new BuildResult(false, "Рядом уже зарегистрировано здание. Выбери другой участок.");
        Map<String, Integer> cost = cost(type);
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (count(warehouse, entry.getKey()) < entry.getValue()) {
                return new BuildResult(false, "Не хватает ресурсов на складе: нужно " + describeCosts(type) + ".");
            }
        }
        cost.forEach((id, amount) -> remove(warehouse, id, amount));
        data.addConstructionOrder(type.name().toLowerCase(java.util.Locale.ROOT), site, layerCount(type));
        return new BuildResult(true, "Заказ принят: " + label(type) + ". Материалы списаны со склада; строительство будет идти послойно. Статус: /civitas construction.");
    }

    public static BuildResult cancelFirstOrder(CivitasSavedData data, net.minecraft.server.level.ServerPlayer player) {
        return cancelOrder(data, player, 1);
    }

    public static BuildResult cancelOrder(CivitasSavedData data, net.minecraft.server.level.ServerPlayer player, int orderNumber) {
        var orders = data.getConstructionOrders();
        if (orderNumber < 1 || orderNumber > orders.size()) {
            return new BuildResult(false, "Заказ №" + orderNumber + " не найден. В очереди заказов: " + orders.size() + ".");
        }
        CivitasSavedData.ConstructionOrder order = orders.get(orderNumber - 1);
        if (order.progress() > 0) {
            return new BuildResult(false, "Заказ №" + orderNumber + " уже начат. Его нельзя отменить, чтобы не оставить недостроенное здание и не потерять материалы.");
        }
        Map<String, Integer> refund = switch (order.type()) {
            case "cabin" -> cost(BuildingType.CABIN);
            case "farm" -> cost(BuildingType.FARM);
            case "workshop" -> cost(BuildingType.WORKSHOP);
            default -> Map.of();
        };
        if (refund.isEmpty()) return new BuildResult(false, "Неизвестный тип заказа: " + order.type());
        if (!data.removeConstructionOrder(order)) return new BuildResult(false, "Заказ уже изменился. Обнови очередь строительства.");
        for (Map.Entry<String, Integer> entry : refund.entrySet()) {
            ResourceLocation id = ResourceLocation.parse(entry.getKey());
            if (!BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            int remaining = entry.getValue();
            while (remaining > 0) {
                int batch = Math.min(remaining, item.getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(item, batch);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                remaining -= batch;
            }
        }
        return new BuildResult(true, "Заказ №" + orderNumber + " отменён. Полный комплект материалов возвращён игроку; при заполненном инвентаре предметы выпали рядом.");
    }

    private static int layerCount(BuildingType type) { return switch (type) { case CABIN -> 5; case FARM -> 2; case WORKSHOP -> 4; }; }
    public static void placeConstructionLayer(ServerLevel level, CivitasSavedData data, CivitasSavedData.ConstructionOrder order) {
        BlockPos g = new BlockPos(order.x(), order.y(), order.z()); int y = order.progress() + 1;
        switch (order.type()) { case "cabin" -> cabinLayer(level,g,y); case "farm" -> farmLayer(level,g,y); case "workshop" -> workshopLayer(level,g,y); default -> { return; } }
        data.advanceConstructionOrder(order);
        if (y >= order.totalLayers()) data.registerBuilding(order.type(),g);
    }
    private static void cabinLayer(ServerLevel l, BlockPos g, int y) {
        if(y==1){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)set(l,g.offset(x,1,z),Blocks.OAK_PLANKS);set(l,g.offset(0,1,0),Blocks.CRAFTING_TABLE);set(l,g.offset(1,1,0),Blocks.BARREL);set(l,g.offset(0,1,2),Blocks.RED_BED);}
        else if(y<=4){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){if(Math.abs(x)!=2&&Math.abs(z)!=2)continue;if(y==2&&x==0&&z==-2)continue;if(y==3&&(Math.abs(x)==2&&z==0))set(l,g.offset(x,y,z),Blocks.GLASS);else set(l,g.offset(x,y,z),Blocks.OAK_LOG);}}
        else{for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)set(l,g.offset(x,5,z),Blocks.SPRUCE_PLANKS);set(l,g.offset(0,1,-2),Blocks.AIR);set(l,g.offset(0,2,-2),Blocks.AIR);}
    }
    private static void farmLayer(ServerLevel l, BlockPos g, int y) {
        if(y==1){for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){if(Math.abs(x)==3||Math.abs(z)==3)set(l,g.offset(x,1,z),Blocks.OAK_FENCE);else if(x==0&&z==0)set(l,g.offset(x,1,z),Blocks.WATER);else set(l,g.offset(x,1,z),Blocks.FARMLAND);}set(l,g.offset(0,1,-3),Blocks.AIR);set(l,g.offset(1,1,0),Blocks.COMPOSTER);set(l,g.offset(-1,1,0),Blocks.BARREL);}
        else{for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){if(Math.abs(x)==3||Math.abs(z)==3||(x==0&&z==0))continue;l.setBlock(g.offset(x,2,z),Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,0),3);}}
    }
    private static void workshopLayer(ServerLevel l, BlockPos g, int y) {
        if(y==1){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)set(l,g.offset(x,1,z),Blocks.COBBLESTONE);set(l,g.offset(0,1,0),Blocks.CRAFTING_TABLE);set(l,g.offset(1,1,0),Blocks.FURNACE);set(l,g.offset(-1,1,0),Blocks.BARREL);set(l,g.offset(0,1,1),Blocks.ANVIL);}
        else if(y<=3){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){if(Math.abs(x)!=2&&Math.abs(z)!=2)continue;if(y==2&&x==0&&z==-2)continue;set(l,g.offset(x,y,z),Blocks.COBBLESTONE);}}
        else{for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)set(l,g.offset(x,4,z),Blocks.OAK_PLANKS);set(l,g.offset(0,2,-2),Blocks.AIR);}
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

    private static BlockPos findSite(ServerLevel level, BlockPos center, BuildingType type, CivitasSavedData data) {
        BlockPos[] candidates = switch (type) {
            case CABIN -> new BlockPos[]{center.offset(9, 0, 0), center.offset(-9, 0, 0), center.offset(0, 0, 9), center.offset(0, 0, -9)};
            case FARM -> new BlockPos[]{center.offset(0, 0, 11), center.offset(11, 0, 0), center.offset(-11, 0, 0), center.offset(0, 0, -11)};
            case WORKSHOP -> new BlockPos[]{center.offset(-9, 0, 0), center.offset(0, 0, -9), center.offset(9, 0, 0), center.offset(0, 0, 9)};
        };
        for (BlockPos candidate : candidates) {
            boolean reserved = data.getConstructionOrders().stream().anyMatch(order ->
                    Math.abs(order.x() - candidate.getX()) <= 5 && Math.abs(order.z() - candidate.getZ()) <= 5);
            if (reserved || data.hasBuildingNear(candidate, 5)) continue;
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
