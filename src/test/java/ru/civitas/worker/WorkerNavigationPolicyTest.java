package ru.civitas.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerNavigationPolicyTest {
    @Test
    void navigationIsNotStalledBeforeEightOneSecondChecks() {
        assertFalse(WorkerNavigationPolicy.hasStalled(0));
        assertFalse(WorkerNavigationPolicy.hasStalled(7));
    }

    @Test
    void navigationIsStalledAtEightChecks() {
        assertTrue(WorkerNavigationPolicy.hasStalled(8));
        assertTrue(WorkerNavigationPolicy.hasStalled(9));
    }

    @Test
    void retriesAreAllowedBeforeTheThirdFailure() {
        assertFalse(WorkerNavigationPolicy.hasExhaustedRetries(0));
        assertFalse(WorkerNavigationPolicy.hasExhaustedRetries(1));
        assertFalse(WorkerNavigationPolicy.hasExhaustedRetries(2));
    }

    @Test
    void thirdFailureExhaustsNavigationRetries() {
        assertTrue(WorkerNavigationPolicy.hasExhaustedRetries(3));
        assertTrue(WorkerNavigationPolicy.hasExhaustedRetries(4));
    }
}
