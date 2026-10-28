package ru.civitas.economy;

/** Small pure rules so economy thresholds can be tested without loading a world. */
public final class SettlementEconomyRules {
    public static final int GROWTH_DAYS_REQUIRED = 2;
    private SettlementEconomyRules() { }

    public static int dailyNeed(int residents) {
        return Math.max(0, residents);
    }

    public static boolean isFed(int availableNutrition, int residents) {
        return availableNutrition >= dailyNeed(residents);
    }

    public static int reserveForGrowth(int dailyNeed, int reserveDays) {
        if (dailyNeed <= 0 || reserveDays <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (long) dailyNeed * reserveDays);
    }

    public static boolean hasGrowthReserve(int storedNutrition, int dailyNeed, int reserveDays) {
        return storedNutrition >= reserveForGrowth(dailyNeed, reserveDays);
    }

    public static boolean canGrow(boolean fed, int freeBeds, int progressDays) {
        return fed && freeBeds > 0 && progressDays >= GROWTH_DAYS_REQUIRED;
    }

    public static boolean canGrow(boolean fed, int freeBeds, int progressDays,
                                  int storedNutrition, int dailyNeed, int reserveDays) {
        return canGrow(fed, freeBeds, progressDays)
                && hasGrowthReserve(storedNutrition, dailyNeed, reserveDays);
    }
}
