package ru.civitas;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common gameplay tuning for agriculture and settlement population. */
public final class CivitasConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue FOOD_PER_DAY;
    public static final ForgeConfigSpec.IntValue GROWTH_INTERVAL_DAYS;
    public static final ForgeConfigSpec.IntValue GROWTH_FOOD_RESERVE;
    public static final ForgeConfigSpec.IntValue FARMER_SEARCH_RADIUS;
    public static final ForgeConfigSpec.BooleanValue LUMBERJACK_REPLANT;
    public static final ForgeConfigSpec.IntValue ECONOMY_HISTORY_DAYS;
    public static final ForgeConfigSpec.IntValue CARAVAN_BASE_INTERVAL_DAYS;
    public static final ForgeConfigSpec.IntValue CARAVAN_WAIT_SECONDS;
    public static final ForgeConfigSpec.IntValue TRADE_REPUTATION_GAIN;
    public static final ForgeConfigSpec.IntValue GIFT_REPUTATION_GAIN;

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
        LUMBERJACK_REPLANT = builder.define("lumberjackReplant", true);
        builder.pop();
        builder.push("economy");
        ECONOMY_HISTORY_DAYS = builder.defineInRange("historyDays", 30, 7, 90);
        builder.pop();
        builder.push("trade");
        CARAVAN_BASE_INTERVAL_DAYS = builder.defineInRange("caravanBaseIntervalDays", 3, 1, 30);
        CARAVAN_WAIT_SECONDS = builder.defineInRange("caravanWaitSeconds", 180, 30, 900);
        TRADE_REPUTATION_GAIN = builder.defineInRange("tradeReputationGain", 2, 0, 20);
        GIFT_REPUTATION_GAIN = builder.defineInRange("giftReputationGain", 5, 0, 50);
        builder.pop();
        SPEC = builder.build();
    }

    private CivitasConfig() { }
}
