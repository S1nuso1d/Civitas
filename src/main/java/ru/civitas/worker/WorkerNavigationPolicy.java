package ru.civitas.worker;

/**
 * Shared limits for worker pathfinding. Worker updates run once per second.
 */
final class WorkerNavigationPolicy {
    static final int STAGNANT_CHECK_LIMIT = 8;
    static final int MAX_RETRIES = 3;

    private WorkerNavigationPolicy() {
    }
}
