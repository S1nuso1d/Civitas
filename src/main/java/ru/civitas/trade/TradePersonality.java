package ru.civitas.trade;

public enum TradePersonality {
    FRIENDLY(0.85, 1.15, 3, 3),
    NEUTRAL(1.0, 1.0, 2, 3),
    GREEDY(1.35, 0.9, 4, 4),
    WARLIKE(1.6, 0.7, 2, 2);

    public final double wantMultiplier;
    public final double offerMultiplier;
    public final int offerCount;
    public final int tradeLimit;

    TradePersonality(double wantMultiplier, double offerMultiplier, int offerCount, int tradeLimit) {
        this.wantMultiplier = wantMultiplier;
        this.offerMultiplier = offerMultiplier;
        this.offerCount = offerCount;
        this.tradeLimit = tradeLimit;
    }

    public static TradePersonality byName(String name) {
        if (name != null) {
            for (TradePersonality value : values()) if (value.name().equals(name)) return value;
        }
        return NEUTRAL;
    }
}