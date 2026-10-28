package ru.civitas.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import ru.civitas.data.CivitasSavedData;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionReservationTest {

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
}
