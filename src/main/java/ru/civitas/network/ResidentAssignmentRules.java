package ru.civitas.network;

/**
 * Pure cargo guards for individual resident role assignment.
 *
 * Keeping these decisions free of Minecraft server objects makes the rules
 * deterministic and testable without starting a world or a network channel.
 */
final class ResidentAssignmentRules {
    private ResidentAssignmentRules() {
    }

    /**
     * A resident with an undelivered lumberjack load must not become a builder.
     */
    static String cargoErrorForBuilder(int carriedCount) {
        if (carriedCount <= 0) {
            return null;
        }
        return "Нельзя назначить строителем: житель несёт " + formatLogsCount(carriedCount)
                + ". Сначала дождись доставки груза на склад.";
    }

    /**
     * Reassigning a lumberjack who is already carrying logs is harmless and
     * should remain idempotent. A non-lumberjack with leftover cargo is treated
     * as inconsistent saved state and is not allowed to change role silently.
     */
    static String cargoErrorForLumberjack(String currentRole, int carriedCount) {
        if (carriedCount <= 0 || "lumberjack".equals(currentRole)) {
            return null;
        }
        return "Нельзя назначить лесорубом: у жителя сохранён незавершённый груз ("
                + formatLogsCount(carriedCount)
                + "), но его текущая роль не «Лесоруб». Сначала проверь роль жителя и доставку груза.";
    }

    private static String formatLogsCount(int count) {
        int lastTwoDigits = count % 100;
        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return count + " брёвен";
        }
        return switch (count % 10) {
            case 1 -> count + " бревно";
            case 2, 3, 4 -> count + " бревна";
            default -> count + " брёвен";
        };
    }
}
