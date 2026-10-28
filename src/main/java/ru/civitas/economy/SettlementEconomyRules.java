package ru.civitas.economy;

/** Pure rules used by the daily settlement economy. */
public final class SettlementEconomyRules {
    public static final int GROWTH_DAYS_REQUIRED = 3;
    private SettlementEconomyRules() { }

    public static int dailyNeed(int residents) { return Math.max(0, residents); }
    public static boolean isFed(int availableFood, int residents) {
        return availableFood >= dailyNeed(residents);
    }
    public static boolean canGrow(boolean fed, int freeBeds, int progressDays) {
        return fed && freeBeds > 0 && progressDays >= GROWTH_DAYS_REQUIRED;
    }
}
