package ru.civitas.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerNavigationPolicyTest {
    @Test
    void navigationIsNotStalledBeforeEightOneSecondChecks() {
        assertFalse(WorkerNavigationPolicy.hasStalled(0, WorkerNavigationPolicy.STAGNANT_CHECK_LIMIT));
        assertFalse(WorkerNavigationPolicy.hasStalled(7, WorkerNavigationPolicy.STAGNANT_CHECK_LIMIT));
    }

    @Test
    void navigationIsStalledAtEightChecks() {
        assertTrue(WorkerNavigationPolicy.hasStalled(8, WorkerNavigationPolicy.STAGNANT_CHECK_LIMIT));
        assertTrue(WorkerNavigationPolicy.hasStalled(9, WorkerNavigationPolicy.STAGNANT_CHECK_LIMIT));
    }

    @Test
    void retriesAreAllowedBeforeTheThirdFailure() {
        assertFalse(WorkerNavigationPolicy.hasExhaustedRetries(0, WorkerNavigationPolicy.MAX_RETRIES));
        assertFalse(WorkerNavigationPolicy.hasExhaustedRetries(1, WorkerNavigationPolicy.MAX_RETRIES));
        assertFalse(WorkerNavigationPolicy.hasExhaustedRetries(2, WorkerNavigationPolicy.MAX_RETRIES));
    }

    @Test
    void thirdFailureExhaustsNavigationRetries() {
        assertTrue(WorkerNavigationPolicy.hasExhaustedRetries(3, WorkerNavigationPolicy.MAX_RETRIES));
        assertTrue(WorkerNavigationPolicy.hasExhaustedRetries(4, WorkerNavigationPolicy.MAX_RETRIES));
    }
}
