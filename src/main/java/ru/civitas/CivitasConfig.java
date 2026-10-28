package ru.civitas;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common gameplay tuning for agriculture and settlement population. */
public final class CivitasConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue FOOD_PER_DAY;
    public static final ForgeConfigSpec.IntValue GROWTH_INTERVAL_DAYS;
    public static final ForgeConfigSpec.IntValue GROWTH_FOOD_RESERVE;
    public static final ForgeConfigSpec.IntValue FARMER_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue ECONOMY_HISTORY_DAYS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("food");
        FOOD_PER_DAY = builder.defineInRange("foodPerDayPerVillager", 1, 1, 10);
        GROWTH_FOOD_RESERVE = builder.defineInRange("growthFoodReserveDays", 3, 1, 30);
        builder.pop();
        builder.push("population");
        GROWTH_INTERVAL_DAYS = builder.defineInRange("growthIntervalDays", 2, 1, 30);
        builder.pop();
        builder.push("workers");
        FARMER_SEARCH_RADIUS = builder.defineInRange("farmerSearchRadius", 24, 8, 64);
        builder.pop();
        builder.push("economy");
        ECONOMY_HISTORY_DAYS = builder.defineInRange("historyDays", 30, 7, 90);
        builder.pop();
        SPEC = builder.build();
    }

    private CivitasConfig() { }
}
