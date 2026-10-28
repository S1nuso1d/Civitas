package ru.civitas.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiLodControllerTest {
    @Test
    void keepsNormalCadenceForNearbyWorkers() {
        assertEquals(20, AiLodController.intervalForDistanceSquared(0.0D));
        assertEquals(20, AiLodController.intervalForDistanceSquared(31.0D * 31.0D));
    }

    @Test
    void throttlesWorkersByDistance() {
        assertEquals(40, AiLodController.intervalForDistanceSquared(32.0D * 32.0D));
        assertEquals(100, AiLodController.intervalForDistanceSquared(64.0D * 64.0D));
        assertEquals(200, AiLodController.intervalForDistanceSquared(128.0D * 128.0D));
        assertEquals(200, AiLodController.intervalForDistanceSquared(Double.POSITIVE_INFINITY));
    }
}
