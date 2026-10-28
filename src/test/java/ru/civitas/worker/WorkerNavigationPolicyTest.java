package ru.civitas.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerNavigationPolicyTest {
    @Test
    void retryLimitIsThreeAttempts() {
        assertEquals(3, WorkerNavigationPolicy.MAX_RETRIES);
    }

    @Test
    void stagnantTimeoutIsEightWorkerUpdates() {
        assertEquals(8, WorkerNavigationPolicy.STAGNANT_CHECK_LIMIT);
    }
}
