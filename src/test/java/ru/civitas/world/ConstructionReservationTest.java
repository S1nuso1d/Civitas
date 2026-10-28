package ru.civitas.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.civitas.data.CivitasSavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionReservationTest {
    @BeforeAll
    static void initializeMinecraftRegistries() {
        ru.civitas.MinecraftTestBootstrap.initialize();
    }


    private static Map<String, Integer> reservation() {
        Map<String, Integer> costs = new LinkedHashMap<>();
        costs.put("minecraft:oak_planks", 32);
        costs.put("minecraft:oak_log", 8);
        costs.put("minecraft:water_bucket", 1);
        return costs;
    }

    @Test
    void untouchedOrderRefundsAllReservedResources() {
        assertEquals(reservation(),
                SettlementBuildingManager.refundableResources(reservation(), 0, 4));
    }

    @Test
    void cancellationRefundsOnlyResourcesAllocatedToRemainingLayers() {
        Map<String, Integer> refund =
                SettlementBuildingManager.refundableResources(reservation(), 1, 4);

        assertEquals(24, refund.get("minecraft:oak_planks"));
        assertEquals(6, refund.get("minecraft:oak_log"));
        // A single indivisible item is allocated to the final layer.
        assertEquals(1, refund.get("minecraft:water_bucket"));
    }

    @Test
    void fullyCompletedOrderHasNoRefundableMaterials() {
        assertTrue(SettlementBuildingManager.refundableResources(reservation(), 4, 4).isEmpty());
    }

    @Test
    void progressIsClampedToValidLayerRange() {
        assertEquals(reservation(),
                SettlementBuildingManager.refundableResources(reservation(), -5, 4));
        assertTrue(SettlementBuildingManager.refundableResources(reservation(), 99, 4).isEmpty());
    }

    @Test
    void reservationSurvivesConstructionProgressUpdates() {
        CivitasSavedData data = new CivitasSavedData();
        data.addConstructionOrder("cabin", new BlockPos(5, 64, 7), 4, reservation());

        CivitasSavedData.ConstructionOrder before = data.getConstructionOrders().get(0);
        data.advanceConstructionOrder(before);

        CivitasSavedData.ConstructionOrder after = data.getConstructionOrders().get(0);
        assertEquals(1, after.progress());
        assertEquals(reservation(), after.reservedResources());
        assertEquals(24, after.remainingReservedResources().get("minecraft:oak_planks"));
    }

    @Test
    void constructionOrderCopiesAndProtectsItsReservation() {
        Map<String, Integer> mutable = reservation();
        CivitasSavedData.ConstructionOrder order =
                new CivitasSavedData.ConstructionOrder("cabin", 1, 64, 1, 1, 4, mutable);
        mutable.put("minecraft:diamond", 99);

        assertEquals(24, order.remainingReservedResources().get("minecraft:oak_planks"));
        assertEquals(6, order.remainingReservedResources().get("minecraft:oak_log"));
        assertEquals(1, order.remainingReservedResources().get("minecraft:water_bucket"));
        assertThrows(UnsupportedOperationException.class,
                () -> order.reservedResources().put("minecraft:diamond", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> order.remainingReservedResources().put("minecraft:diamond", 1));
    }

    @Test
    void exactLayerPlanRefundsOnlyMaterialsAssignedToUnbuiltLayers() {
        BuildingDefinition definition = BuildingDefinition.get(BuildingDefinition.Type.CABIN);
        CivitasSavedData data = new CivitasSavedData();
        data.addConstructionOrder(definition.id(), new BlockPos(5, 64, 7),
                definition.totalLayers(), definition.costs(), definition.layerCosts());

        CivitasSavedData.ConstructionOrder order = data.getConstructionOrders().get(0);
        assertTrue(order.hasValidLayerCosts());
        assertEquals(definition.costs(), order.remainingReservedResources());

        data.advanceConstructionOrder(order);
        order = data.getConstructionOrders().get(0);
        assertEquals(1, order.progress());
        assertEquals(Map.of(
                "minecraft:oak_planks", 20,
                "minecraft:oak_log", 8,
                "minecraft:glass", 4
        ), order.remainingReservedResources());

        data.advanceConstructionOrder(order);
        data.advanceConstructionOrder(data.getConstructionOrders().get(0));
        order = data.getConstructionOrders().get(0);
        assertEquals(3, order.progress());
        assertEquals(Map.of(
                "minecraft:oak_planks", 20,
                "minecraft:oak_log", 2,
                "minecraft:glass", 2
        ), order.remainingReservedResources());
    }

    @Test
    void stagePlanIsDeepCopiedAndPreservedWhenProgressAdvances() {
        BuildingDefinition definition = BuildingDefinition.get(BuildingDefinition.Type.FARM);
        List<Map<String, Integer>> originalPlan = definition.layerCosts();
        CivitasSavedData data = new CivitasSavedData();
        data.addConstructionOrder(definition.id(), new BlockPos(5, 64, 7),
                definition.totalLayers(), definition.costs(), originalPlan);

        CivitasSavedData.ConstructionOrder before = data.getConstructionOrders().get(0);
        assertThrows(UnsupportedOperationException.class,
                () -> before.layerCosts().get(0).put("minecraft:diamond", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> before.layerCosts().clear());

        data.advanceConstructionOrder(before);
        CivitasSavedData.ConstructionOrder after = data.getConstructionOrders().get(0);
        assertEquals(originalPlan, after.layerCosts());
        assertEquals(Map.of("minecraft:wheat_seeds", 24), after.remainingReservedResources());
    }

    @Test
    void warehouseReservationsPreventTwoOrdersFromClaimingTheSameStock() {
        BuildingDefinition definition = BuildingDefinition.get(BuildingDefinition.Type.CABIN);
        CivitasSavedData data = new CivitasSavedData();
        Map<String, Integer> stock = new LinkedHashMap<>(definition.costs());

        data.addConstructionOrder(definition.id(), new BlockPos(5, 64, 7),
                definition.totalLayers(), definition.costs(), definition.layerCosts(), true);

        assertEquals(definition.costs(), data.getWarehouseReservedResources());
        assertTrue(data.warehouseContainsAllReservations(stock));
        assertTrue(!data.canReserveWarehouseResources(stock, definition.costs()));

        Map<String, Integer> doubleStock = new LinkedHashMap<>();
        stock.forEach((itemId, amount) -> doubleStock.put(itemId, amount * 2));
        assertTrue(data.canReserveWarehouseResources(doubleStock, definition.costs()));
    }

    @Test
    void warehouseReservationShrinksWhenLayerCompletesAndReleasesOnCancellation() {
        BuildingDefinition definition = BuildingDefinition.get(BuildingDefinition.Type.FARM);
        CivitasSavedData data = new CivitasSavedData();
        data.addConstructionOrder(definition.id(), new BlockPos(5, 64, 7),
                definition.totalLayers(), definition.costs(), definition.layerCosts(), true);

        CivitasSavedData.ConstructionOrder first = data.getConstructionOrders().get(0);
        data.advanceConstructionOrder(first);

        assertEquals(Map.of("minecraft:wheat_seeds", 24), data.getWarehouseReservedResources());
        CivitasSavedData.ConstructionOrder active = data.getConstructionOrders().get(0);
        assertTrue(active.materialsReservedInWarehouse());
        assertTrue(data.removeConstructionOrder(active));
        assertTrue(data.getWarehouseReservedResources().isEmpty());
    }

    @Test
    void legacyUpfrontPaidOrdersDoNotReservePhysicalWarehouseStock() {
        BuildingDefinition definition = BuildingDefinition.get(BuildingDefinition.Type.CABIN);
        CivitasSavedData data = new CivitasSavedData();
        data.addConstructionOrder(definition.id(), new BlockPos(5, 64, 7),
                definition.totalLayers(), definition.costs(), definition.layerCosts(), false);

        assertTrue(data.getWarehouseReservedResources().isEmpty());
        assertTrue(!data.getConstructionOrders().get(0).materialsReservedInWarehouse());
    }

    @Test
    void legacyReservationWithoutLayerPlanKeepsProportionalRefundBehavior() {
        CivitasSavedData.ConstructionOrder legacy =
                new CivitasSavedData.ConstructionOrder("cabin", 1, 64, 1, 1, 4, reservation());

        assertTrue(!legacy.hasValidLayerCosts());
        assertEquals(24, legacy.remainingReservedResources().get("minecraft:oak_planks"));
        assertEquals(6, legacy.remainingReservedResources().get("minecraft:oak_log"));
        assertEquals(1, legacy.remainingReservedResources().get("minecraft:water_bucket"));
    }

}
