package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;

import java.util.Map;

/** Validates a build site, charges the physical warehouse and places a small structure. */
public final class SettlementBuildingManager {
    public record BuildResult(boolean success, String message) {}

    private SettlementBuildingManager() {}

    public static String describeCosts(BuildingDefinition.Type type) {
        return BuildingDefinition.get(type).describeCosts();
    }

    public static BuildResult build(ServerLevel level, BlockPos warehousePos,
                                    SettlementWarehouseBlockEntity warehouse, BuildingDefinition.Type type,
                                    CivitasSavedData data) {
        BlockPos site = findSite(level, warehousePos, type, data);
        if (site == null) {
            return new BuildResult(false,
                    "Нет свободной площадки рядом с деревней. Освободи участок примерно 7×7 блоков.");
        }
        BuildResult validation = validateManualSite(level, warehousePos, type, data, site, 0);
        if (!validation.success()) return validation;
        return queueAtSite(level, warehouse, type, data, site, 0);
    }

    /** Queues a player-selected build site with a validated 90-degree rotation. */
    public static BuildResult buildAt(ServerLevel level, BlockPos warehousePos,
                                      SettlementWarehouseBlockEntity warehouse, BuildingDefinition.Type type,
                                      CivitasSavedData data, BlockPos site, int rotationDegrees) {
        BuildResult validation = validateManualSite(level, warehousePos, type, data, site, rotationDegrees);
        if (!validation.success()) return validation;
        return queueAtSite(level, warehouse, type, data, site, rotationDegrees);
    }

    /**
     * Draws the blueprint as short-lived particles. End rods mean the selected
     * plot is valid; smoke means a building/terrain/clearance check failed.
     */
    public static BuildResult preview(ServerLevel level, BlockPos warehousePos,
                                      BuildingDefinition.Type type, CivitasSavedData data,
                                      BlockPos site, int rotationDegrees) {
        BuildResult validation = validateManualSite(level, warehousePos, type, data, site, rotationDegrees);
        boolean valid = validation.success();
        BuildingDefinition definition = BuildingDefinition.get(type);
        for (int layer = 1; layer <= definition.totalLayers(); layer++) {
            for (BuildingBlueprint.Placement placement : BuildingBlueprint.layer(type, layer,
                    validRotationForPreview(rotationDegrees))) {
                if (placement.clearsBlock()) continue;
                BlockPos target = site.offset(placement.relativePosition());
                if (!level.hasChunkAt(target)) continue;
                level.sendParticles(valid ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE,
                        target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        return validation;
    }

    private static int validRotationForPreview(int rotationDegrees) {
        if (rotationDegrees >= 0 && rotationDegrees < 360 && rotationDegrees % 90 == 0) {
            return rotationDegrees;
        }
        return 0;
    }

    private static BuildResult validateManualSite(ServerLevel level, BlockPos villageCenter,
                                                   BuildingDefinition.Type type, CivitasSavedData data,
                                                   BlockPos site, int rotationDegrees) {
        if (site == null) {
            return new BuildResult(false, "Не задан центр площадки.");
        }
        if (rotationDegrees < 0 || rotationDegrees >= 360 || rotationDegrees % 90 != 0) {
            return new BuildResult(false, "Угол поворота должен быть 0, 90, 180 или 270 градусов.");
        }
        if (!level.hasChunkAt(site)) {
            return new BuildResult(false, "Площадка находится в незагруженном чанке. Подойди ближе и повтори.");
        }
        // Current residents and their pathfinding are local to the settlement.
        // Reject unreachable far-away orders instead of reserving materials forever.
        if (Math.abs(site.getX() - villageCenter.getX()) > 64
                || Math.abs(site.getZ() - villageCenter.getZ()) > 64
                || Math.abs(site.getY() - villageCenter.getY()) > 24) {
            return new BuildResult(false,
                    "Участок слишком далеко от поселения для текущих строителей. Выбери место в пределах 64 блоков по горизонтали и 24 по высоте.");
        }

        BuildingDefinition definition = BuildingDefinition.get(type);
        BlockState groundState = level.getBlockState(site);
        if (!groundState.isSolid() || !level.getFluidState(site).isEmpty()) {
            return new BuildResult(false,
                    "Укажи координаты твёрдого блока земли под зданием. Например, стоя на земле: ~ ~-1 ~.");
        }

        // Player-selected construction must not overlap the protected 29x29 starter village.
        BlockPos protectedCenter = data.hasStarterVillage() ? data.getStarterVillageCenter() : villageCenter;
        int footprintRadiusX = (rotationDegrees == 90 || rotationDegrees == 270)
                ? definition.radiusZ() : definition.radiusX();
        int footprintRadiusZ = (rotationDegrees == 90 || rotationDegrees == 270)
                ? definition.radiusX() : definition.radiusZ();
        if (data.hasStarterVillage()
                && Math.abs(site.getX() - protectedCenter.getX()) <= 14 + footprintRadiusX
                && Math.abs(site.getZ() - protectedCenter.getZ()) <= 14 + footprintRadiusZ) {
            return new BuildResult(false,
                    "Площадка пересекает стартовую деревню или её дорожки. Выбери участок за её пределами.");
        }

        int exclusionRadius = Math.max(definition.radiusX(), definition.radiusZ()) * 2 + 1;
        if (data.hasBuildingNear(site, exclusionRadius)) {
            return new BuildResult(false, "Площадка пересекается с уже зарегистрированным зданием.");
        }
        for (CivitasSavedData.ConstructionOrder order : data.getConstructionOrders()) {
            if (Math.abs(order.x() - site.getX()) <= exclusionRadius
                    && Math.abs(order.z() - site.getZ()) <= exclusionRadius) {
                return new BuildResult(false, "Площадка пересекается с другим заказом строительства.");
            }
        }

        // Validate the rotated full footprint, not only the anchor. Every column
        // must stay inside influence and a claimed chunk before materials are reserved.
        for (int dx = -footprintRadiusX; dx <= footprintRadiusX; dx++) {
            for (int dz = -footprintRadiusZ; dz <= footprintRadiusZ; dz++) {
                BlockPos floorPos = site.offset(dx, 0, dz);
                if (!level.hasChunkAt(floorPos)) {
                    return new BuildResult(false, "Не все чанки площадки загружены.");
                }
                if (!data.isWithinTerritory(floorPos)) {
                    return new BuildResult(false, "Весь контур здания должен находиться внутри зоны влияния поселения.");
                }
                if (!data.isChunkClaimed(floorPos)) {
                    return new BuildResult(false, "Весь контур здания должен находиться в занятых чанках. Расширь территорию командой /civitas territory claim.");
                }
                BlockState floor = level.getBlockState(floorPos);
                if (!floor.isSolid() || !level.getFluidState(floorPos).isEmpty()) {
                    return new BuildResult(false, "Площадка должна быть ровной, сухой и иметь твёрдую землю.");
                }
                for (int dy = 1; dy <= definition.height(); dy++) {
                    BlockPos clearancePos = site.offset(dx, dy, dz);
                    if (!level.hasChunkAt(clearancePos)) {
                        return new BuildResult(false, "Не все чанки объёма здания загружены.");
                    }
                    BlockState existing = level.getBlockState(clearancePos);
                    if (existing.isAir()) continue;
                    if ((!existing.canBeReplaced() && !existing.is(BlockTags.LEAVES))
                            || !level.getFluidState(clearancePos).isEmpty()) {
                        return new BuildResult(false, "Объём здания занят: "
                                + clearancePos.getX() + ", " + clearancePos.getY() + ", "
                                + clearancePos.getZ() + ". Убери твёрдое препятствие.");
                    }
                }
            }
        }
        return new BuildResult(true, "Площадка подходит.");
    }

    private static BuildResult queueAtSite(ServerLevel level, SettlementWarehouseBlockEntity warehouse,
                                            BuildingDefinition.Type type, CivitasSavedData data,
                                            BlockPos site, int rotationDegrees) {
        BuildingDefinition definition = BuildingDefinition.get(type);
        if (warehouse == null) {
            return new BuildResult(false, "Центральный физический склад не найден.");
        }

        Map<String, Integer> cost = definition.costs();
        Map<String, Integer> stock = inventoryCounts(warehouse);
        Map<String, Integer> reserved = data.getWarehouseReservedResources();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            int stored = stock.getOrDefault(entry.getKey(), 0);
            int alreadyReserved = reserved.getOrDefault(entry.getKey(), 0);
            int free = Math.max(0, stored - alreadyReserved);
            if (stored < alreadyReserved) {
                return new BuildResult(false, "На складе не хватает " + entry.getKey()
                        + " для уже зарезервированных заказов. Пополни склад перед новой стройкой.");
            }
            if (free < entry.getValue()) {
                return new BuildResult(false, "Недостаточно свободных ресурсов: " + entry.getKey()
                        + " — доступно " + free + ", нужно ещё " + (entry.getValue() - free)
                        + ". Всего требуется: " + definition.describeCosts() + ".");
            }
        }
        if (!data.canReserveWarehouseResources(stock, cost)) {
            return new BuildResult(false,
                    "Нельзя зарезервировать материалы: часть складских запасов уже обещана другим заказам.");
        }

        clearReplaceableVolume(level, site, definition, rotationDegrees);
        data.addConstructionOrder(definition.id(), site, definition.totalLayers(),
                definition.costs(), definition.layerCosts(), true, rotationDegrees);
        return new BuildResult(true, "Заказ принят: " + definition.displayName()
                + " (поворот " + rotationDegrees + "°). Резерв материалов создан на складе. "
                + "Растительность в объёме здания очищена; твёрдые блоки не удаляются. "
                + "Статус: /civitas construction.");
    }

    private static void clearReplaceableVolume(ServerLevel level, BlockPos site, BuildingDefinition definition, int rotationDegrees) {
        int radiusX = (rotationDegrees == 90 || rotationDegrees == 270) ? definition.radiusZ() : definition.radiusX();
        int radiusZ = (rotationDegrees == 90 || rotationDegrees == 270) ? definition.radiusX() : definition.radiusZ();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = 1; dy <= definition.height(); dy++) {
                    BlockPos target = site.offset(dx, dy, dz);
                    BlockState existing = level.getBlockState(target);
                    if (!existing.isAir()
                            && (existing.canBeReplaced() || existing.is(BlockTags.LEAVES))
                            && level.getFluidState(target).isEmpty()) {
                        level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
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
        BuildingDefinition definition = BuildingDefinition.find(order.type()).orElse(null);
        if (definition == null) return new BuildResult(false, "Неизвестный тип заказа: " + order.type());

        ServerLevel level = player.getServer().overworld();
        BlockPos site = new BlockPos(order.x(), order.y(), order.z());
        if (order.progress() > 0
                && !areConstructedLayersLoaded(level, site, definition, order.progress())) {
            return new BuildResult(false,
                    "Чтобы безопасно отменить начатое строительство, подойди к площадке и загрузи всю постройку.");
        }

        // Pre-0.15 orders had already paid the full cost, so refund their
        // unconsumed portion to the player. New orders keep those items in the
        // warehouse; cancelling simply releases their reservation.
        Map<String, Integer> refund = order.materialsReservedInWarehouse()
                ? Map.of()
                : order.reservedResources().isEmpty()
                        ? refundableResources(definition.costs(), order.progress(), order.totalLayers())
                        : order.remainingReservedResources();

        // Remove only the block types that Civitas placed in completed layers.
        // Player replacements using other block types are intentionally preserved.
        if (order.progress() > 0) {
            removeConstructedLayers(level, order);
        }
        if (!data.removeConstructionOrder(order)) {
            return new BuildResult(false, "Заказ уже изменился. Обнови очередь строительства.");
        }

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
        String refundMessage;
        if (order.materialsReservedInWarehouse()) {
            refundMessage = "Неиспользованные материалы остались на центральном складе; их резерв освобождён.";
        } else if (refund.isEmpty()) {
            refundMessage = "Возвращать материалы больше не требуется.";
        } else {
            refundMessage = "Неиспользованные материалы возвращены игроку; при заполненном инвентаре предметы выпали рядом.";
        }
        return new BuildResult(true, "Заказ №" + orderNumber + " отменён. "
                + (order.progress() > 0
                ? "Построенные Civitas слои разобраны; заменённые другими блоками детали сохранены. "
                : "")
                + refundMessage);
    }

    /**
     * Splits an order's original resource reservation across its construction
     * layers. Resources allocated to completed layers are considered consumed.
     */
    static Map<String, Integer> refundableResources(
            Map<String, Integer> reservation, int progress, int totalLayers) {
        if (reservation == null || reservation.isEmpty()) return Map.of();

        int safeLayers = Math.max(1, totalLayers);
        int completedLayers = Math.max(0, Math.min(progress, safeLayers));
        Map<String, Integer> refund = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : reservation.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            int amount = Math.max(0, entry.getValue());
            int consumed = (int) (((long) amount * completedLayers) / safeLayers);
            int remaining = amount - consumed;
            if (remaining > 0) refund.put(entry.getKey(), remaining);
        }
        return java.util.Collections.unmodifiableMap(refund);
    }

    /**
     * Removes up to the completed construction layers. A block is removed only
     * when its current type belongs to that Civitas template layer.
     */
    public static void removeConstructedLayers(ServerLevel level, CivitasSavedData.ConstructionOrder order) {
        BuildingDefinition.Type type = BuildingDefinition.Type.byId(order.type()).orElse(null);
        if (type == null) return;

        BlockPos ground = new BlockPos(order.x(), order.y(), order.z());
        int progress = Math.max(0, Math.min(order.progress(), order.totalLayers()));
        for (int layer = progress; layer >= 1; layer--) {
            BuildingBlueprint.removeLayer(level, ground, type, layer, order.rotationDegrees());
        }
    }

    private static boolean areConstructedLayersLoaded(
            ServerLevel level, BlockPos ground, BuildingDefinition definition, int completedLayers) {
        int radiusX = definition.radiusX();
        int radiusZ = definition.radiusZ();
        int height = Math.min(Math.max(0, completedLayers), definition.height());
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                for (int y = 1; y <= height; y++) {
                    if (!level.hasChunkAt(ground.offset(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Builds exactly one layer. For Alpha 0.15+ orders, every outstanding
     * warehouse reservation must still exist before any reserved item is spent.
     * Orders from older versions were paid upfront and therefore skip withdrawal.
     */
    public static BuildResult placeConstructionLayer(
            ServerLevel level, CivitasSavedData data, CivitasSavedData.ConstructionOrder order) {
        if (order == null || !data.getConstructionOrders().contains(order)) {
            return new BuildResult(false, "Заказ строительства больше не активен.");
        }
        BuildingDefinition.Type type = BuildingDefinition.Type.byId(order.type()).orElse(null);
        if (type == null) {
            return new BuildResult(false, "Неизвестный тип постройки: " + order.type());
        }

        BlockPos ground = new BlockPos(order.x(), order.y(), order.z());
        int layer = order.progress() + 1;
        if (layer < 1 || layer > BuildingDefinition.get(type).totalLayers()) {
            return new BuildResult(false, "У заказа некорректный номер слоя; строительство остановлено без списания ресурсов.");
        }

        // Re-check ownership of the complete plot on every layer. Claims can
        // change after an order is queued, and a saved order must never continue
        // constructing outside the settlement's current territory.
        BuildResult territoryCheck = validateOrderTerritory(level, data, ground,
                BuildingDefinition.get(type), order.rotationDegrees());
        if (!territoryCheck.success()) return territoryCheck;

        if (!BuildingBlueprint.canPlaceLayer(level, ground, type, layer, order.rotationDegrees())) {
            return new BuildResult(false,
                    "Слой " + layer + " пока нельзя безопасно построить: участок занят или часть чанков не загружена. Материалы не списаны.");
        }
        if (order.materialsReservedInWarehouse()) {
            if (!order.hasValidLayerCosts() || order.progress() < 0
                    || order.progress() >= order.layerCosts().size()) {
                return new BuildResult(false, "У заказа повреждена послойная смета; строительство остановлено без списания ресурсов.");
            }
            BlockPos warehousePos = data.hasStarterVillage()
                    ? data.getStarterVillageCenter() : data.getCenter();
            if (!level.hasChunkAt(warehousePos)
                    || !(level.getBlockEntity(warehousePos) instanceof SettlementWarehouseBlockEntity warehouse)) {
                return new BuildResult(false, "Центральный склад не загружен или отсутствует; слой не построен.");
            }

            Map<String, Integer> stock = inventoryCounts(warehouse);
            if (!data.warehouseContainsAllReservations(stock)) {
                return new BuildResult(false, "На складе меньше материалов, чем зарезервировано. Пополни склад; строительство приостановлено.");
            }

            Map<String, Integer> layerCost = order.layerCosts().get(order.progress());
            for (Map.Entry<String, Integer> entry : layerCost.entrySet()) {
                if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(entry.getKey()))
                        || stock.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    return new BuildResult(false, "Не хватает материалов для слоя " + layer + ": "
                            + entry.getKey() + " × " + entry.getValue() + ". Слой не построен.");
                }
            }

            // Snapshot the complete inventory before changing it. A failed
            // deduction restores every slot exactly, so a partial transaction
            // cannot consume materials without placing the corresponding layer.
            if (!removeLayerCostsAtomically(warehouse, layerCost)) {
                return new BuildResult(false,
                        "Не удалось атомарно списать материалы для слоя " + layer
                                + ". Склад восстановлен; слой не построен.");
            }
        }

        BuildingBlueprint.placeLayer(level, ground, type, layer, order.rotationDegrees());
        data.advanceConstructionOrder(order);
        if (layer >= BuildingDefinition.get(type).totalLayers()) {
            data.registerBuilding(order.type(), ground, order.rotationDegrees());
        }
        return new BuildResult(true, "Слой " + layer + " завершён.");
    }
    /**
     * Upgrades a registered cabin in place. Costs are paid from free warehouse
     * stock only; reserved construction materials are never consumed.
     */
    public static BuildResult upgradeHouse(ServerLevel level, CivitasSavedData data,
                                            SettlementWarehouseBlockEntity warehouse, int buildingIndex) {
        if (warehouse == null) {
            return new BuildResult(false, "Центральный склад не найден или не загружен.");
        }
        var buildings = data.getBuildings();
        if (buildingIndex < 1 || buildingIndex > buildings.size()) {
            return new BuildResult(false, "Дом №" + buildingIndex + " не найден. Проверь /civitas buildings.");
        }
        CivitasSavedData.BuildingRecord building = buildings.get(buildingIndex - 1);
        if (!"cabin".equals(building.type())) {
            return new BuildResult(false, "Улучшать можно только жилые дома.");
        }
        if (building.upgradeLevel() >= 3) {
            return new BuildResult(false, "Этот дом уже достиг максимального уровня (3).");
        }

        int nextLevel = building.upgradeLevel() + 1;
        Map<String, Integer> costs = nextLevel == 2
                ? Map.of("minecraft:oak_slab", 5, "minecraft:oak_fence", 4,
                        "minecraft:oak_stairs", 1, "minecraft:lantern", 1)
                : Map.of("minecraft:bricks", 6);
        Map<String, Integer> stock = inventoryCounts(warehouse);
        Map<String, Integer> reserved = data.getWarehouseReservedResources();
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            int free = stock.getOrDefault(entry.getKey(), 0) - reserved.getOrDefault(entry.getKey(), 0);
            if (free < entry.getValue()) {
                return new BuildResult(false, "Для улучшения дома до уровня " + nextLevel
                        + " не хватает " + entry.getKey() + " × " + (entry.getValue() - Math.max(0, free))
                        + ". Зарезервированные материалы недоступны.");
            }
        }

        BlockPos ground = new BlockPos(building.x(), building.y(), building.z());
        for (BuildingBlueprint.Placement placement : BuildingBlueprint.houseUpgrade(nextLevel, building.rotationDegrees())) {
            BlockPos target = ground.offset(placement.relativePosition());
            if (!data.isWithinTerritory(target) || !data.isChunkClaimed(target)) {
                return new BuildResult(false,
                        "Улучшение выходит за границы зоны влияния или занятых чанков поселения.");
            }
        }
        if (!BuildingBlueprint.canPlaceHouseUpgrade(level, ground, nextLevel, building.rotationDegrees())) {
            return new BuildResult(false,
                    "Нельзя улучшить дом: внешние блоки улучшения заняты, затоплены или чанки не загружены.");
        }

        if (!removeLayerCostsAtomically(warehouse, costs)) {
            return new BuildResult(false, "Не удалось списать материалы улучшения; склад восстановлен.");
        }
        try {
            BuildingBlueprint.placeHouseUpgrade(level, ground, nextLevel, building.rotationDegrees());
            if (!data.upgradeBuilding(buildingIndex - 1, building.upgradeLevel())) {
                BuildingBlueprint.removeHouseUpgrade(level, ground, nextLevel, building.rotationDegrees());
                for (Map.Entry<String, Integer> entry : costs.entrySet()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
                    insertWarehouseItems(warehouse, item, entry.getValue());
                }
                return new BuildResult(false, "Дом изменился во время улучшения; изменения отменены, материалы возвращены на склад.");
            }
        } catch (RuntimeException exception) {
            // Roll back only the exact blocks from this upgrade, preserving any
            // player replacement that no longer matches the blueprint.
            BuildingBlueprint.removeHouseUpgrade(level, ground, nextLevel, building.rotationDegrees());
            for (Map.Entry<String, Integer> entry : costs.entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
                insertWarehouseItems(warehouse, item, entry.getValue());
            }
            return new BuildResult(false, "Улучшение не завершено: " + exception.getMessage());
        }
        return new BuildResult(true, "Дом №" + buildingIndex + " улучшен до уровня " + nextLevel + ".");
    }

    private static void insertWarehouseItems(SettlementWarehouseBlockEntity warehouse, Item item, int count) {
        int remaining = count;
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = warehouse.getItem(slot);
            if (existing.isEmpty()) {
                int amount = Math.min(remaining, item.getDefaultMaxStackSize());
                warehouse.setItem(slot, new ItemStack(item, amount));
                remaining -= amount;
            } else if (existing.is(item) && existing.getCount() < existing.getMaxStackSize()) {
                int amount = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
                existing.grow(amount);
                warehouse.setChanged();
                remaining -= amount;
            }
        }
        // All upgrades preflight available resources, so rollback normally fits
        // in the slots just freed by the corresponding deduction.
        if (remaining > 0) {
            levelDropFallback(warehouse, item, remaining);
        }
    }

    private static void levelDropFallback(SettlementWarehouseBlockEntity warehouse, Item item, int remaining) {
        // A defensive fallback for a concurrently changed inventory; avoid item loss.
        var level = warehouse.getLevel();
        var pos = warehouse.getBlockPos();
        if (level == null || level.isClientSide) return;
        while (remaining > 0) {
            int amount = Math.min(remaining, item.getDefaultMaxStackSize());
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    new ItemStack(item, amount)));
            remaining -= amount;
        }
    }

    private static BuildResult validateOrderTerritory(
            ServerLevel level, CivitasSavedData data, BlockPos ground,
            BuildingDefinition definition, int rotationDegrees) {
        if (rotationDegrees < 0 || rotationDegrees >= 360 || rotationDegrees % 90 != 0) {
            return new BuildResult(false, "У заказа некорректный поворот; строительство приостановлено без списания ресурсов.");
        }

        boolean swapAxes = rotationDegrees == 90 || rotationDegrees == 270;
        int radiusX = swapAxes ? definition.radiusZ() : definition.radiusX();
        int radiusZ = swapAxes ? definition.radiusX() : definition.radiusZ();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                BlockPos column = ground.offset(dx, 0, dz);
                if (!level.hasChunkAt(column)) {
                    return new BuildResult(false, "Строительство приостановлено: часть площадки не загружена.");
                }
                if (!data.isWithinTerritory(column)) {
                    return new BuildResult(false, "Строительство приостановлено: часть здания вышла за зону влияния поселения.");
                }
                if (!data.isChunkClaimed(column)) {
                    return new BuildResult(false, "Строительство приостановлено: часть здания больше не находится в занятых чанках. Верни чанк под контроль командой /civitas territory claim.");
                }
            }
        }
        return new BuildResult(true, "Территория подтверждена.");
    }

    private static BlockPos findSite(ServerLevel level, BlockPos center, BuildingDefinition.Type type, CivitasSavedData data) {
        BuildingDefinition definition = BuildingDefinition.get(type);
        // Automatic sites stay outside the 29x29 starter village, its four
        // houses and the paths connecting them. Manual coordinates are available
        // in the construction screen when the player wants a different location.
        BlockPos[] candidates = switch (type) {
            case CABIN -> new BlockPos[]{center.offset(18, 0, 0), center.offset(-18, 0, 0),
                    center.offset(0, 0, 18), center.offset(0, 0, -18)};
            case FARM -> new BlockPos[]{center.offset(0, 0, 20), center.offset(20, 0, 0),
                    center.offset(-20, 0, 0), center.offset(0, 0, -20)};
            case WORKSHOP -> new BlockPos[]{center.offset(-18, 0, 0), center.offset(0, 0, -18),
                    center.offset(18, 0, 0), center.offset(0, 0, 18)};
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
            int radiusX = definition.radiusX();
            int radiusZ = definition.radiusZ();
            int height = definition.height();
            for (int dx = -radiusX; dx <= radiusX && clear; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ && clear; dz++) {
                    BlockPos floorPos = ground.offset(dx, 0, dz);
                    if (!level.hasChunkAt(floorPos)
                            || !level.getBlockState(floorPos).isSolid()
                            || !level.getFluidState(floorPos).isEmpty()) {
                        clear = false;
                        break;
                    }
                    for (int dy = 1; dy <= height; dy++) {
                        BlockPos p = ground.offset(dx, dy, dz);
                        if (!level.hasChunkAt(p)) {
                            clear = false;
                            break;
                        }
                        BlockState existing = level.getBlockState(p);
                        if ((!existing.isAir() && !existing.canBeReplaced() && !existing.is(BlockTags.LEAVES))
                                || !level.getFluidState(p).isEmpty()) {
                            clear = false;
                            break;
                        }
                    }
                }
            }
            if (clear) return ground;
        }
        return null;
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
    }

    /** Snapshot of normal item counts in the physical warehouse. */
    public static Map<String, Integer> inventoryCounts(SettlementWarehouseBlockEntity warehouse) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        if (warehouse == null) return java.util.Collections.unmodifiableMap(counts);
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return java.util.Collections.unmodifiableMap(counts);
    }

    private static int count(SettlementWarehouseBlockEntity warehouse, String id) {
        return inventoryCounts(warehouse).getOrDefault(id, 0);
    }

    /**
     * Removes a layer's material cost as one transaction. If any individual
     * removal fails, every warehouse slot is restored from a pre-operation
     * snapshot before the caller can attempt to place blocks.
     */
    static boolean removeLayerCostsAtomically(
            SettlementWarehouseBlockEntity warehouse, Map<String, Integer> costs) {
        if (warehouse == null || costs == null || costs.isEmpty()) {
            return costs != null && costs.isEmpty();
        }

        ItemStack[] snapshot = new ItemStack[warehouse.getContainerSize()];
        for (int slot = 0; slot < snapshot.length; slot++) {
            snapshot[slot] = warehouse.getItem(slot).copy();
        }

        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() < 0
                    || !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(entry.getKey()))) {
                restoreWarehouseSnapshot(warehouse, snapshot);
                return false;
            }
            if (entry.getValue() > 0 && !remove(warehouse, entry.getKey(), entry.getValue())) {
                restoreWarehouseSnapshot(warehouse, snapshot);
                return false;
            }
        }
        return true;
    }

    private static void restoreWarehouseSnapshot(
            SettlementWarehouseBlockEntity warehouse, ItemStack[] snapshot) {
        for (int slot = 0; slot < snapshot.length; slot++) {
            warehouse.setItem(slot, snapshot[slot].copy());
        }
        warehouse.setChanged();
    }

    private static boolean remove(SettlementWarehouseBlockEntity warehouse, String id, int amount) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        int remaining = amount;
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            if (stack.isEmpty()) warehouse.setItem(slot, ItemStack.EMPTY);
            else warehouse.setChanged();
            remaining -= removed;
        }
        return remaining == 0;
    }
}
