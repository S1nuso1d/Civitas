package ru.civitas.world;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementBuildingManagerTest {
    @Test
    void legacyCancellationRefundsOnlyTheUnconsumedProportion() {
        Map<String, Integer> original = Map.of(
                "minecraft:oak_planks", 32,
                "minecraft:oak_log", 8,
                "minecraft:glass", 4
        );

        Map<String, Integer> refund =
                SettlementBuildingManager.refundableResources(original, 2, 5);

        assertEquals(Map.of(
                "minecraft:oak_planks", 20,
                "minecraft:oak_log", 5,
                "minecraft:glass", 3
        ), refund);
    }

    @Test
    void completedLegacyOrderHasNoRefund() {
        assertTrue(SettlementBuildingManager.refundableResources(
                Map.of("minecraft:oak_planks", 32), 5, 5).isEmpty());
    }

    @Test
    void cancellationRefundClampsProgressAndLayerCountSafely() {
        assertEquals(Map.of("minecraft:oak_planks", 32),
                SettlementBuildingManager.refundableResources(
                        Map.of("minecraft:oak_planks", 32), -4, 0));
        assertTrue(SettlementBuildingManager.refundableResources(
                Map.of("minecraft:oak_planks", 32), 99, 5).isEmpty());
    }

    @Test
    void cancellationRefundResultIsImmutable() {
        Map<String, Integer> refund = SettlementBuildingManager.refundableResources(
                Map.of("minecraft:oak_planks", 32), 0, 5);
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> refund.put("minecraft:glass", 1));
    }
}
