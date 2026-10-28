package ru.civitas.worker;

/**
 * Shared limits and pure decision rules for worker pathfinding.
 * Worker navigation is checked at the existing one-second update cadence.
 */
final class WorkerNavigationPolicy {
    static final int STAGNANT_CHECK_LIMIT = 8;
    static final int MAX_RETRIES = 3;

    private WorkerNavigationPolicy() {
    }

    static boolean hasStalled(int stagnantChecks, int limit) {
        return stagnantChecks >= limit;
    }

    static boolean hasExhaustedRetries(int retries, int limit) {
        return retries >= limit;
    }
}
