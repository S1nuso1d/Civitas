package ru.civitas.building;

/** Functional thresholds shared by building UI and settlement simulation. */
public enum DamageLevel {
    PRISTINE(0, 0, 1.00D),
    WORN(10, 0, 1.00D),
    CRACKED(30, -5, 0.95D),
    DAMAGED(60, -15, 0.75D),
    DANGEROUS(80, -30, 0.40D),
    RUINED(100, -50, 0.00D);

    public final int threshold;
    public final int happinessPenalty;
    public final double productionMultiplier;

    DamageLevel(int threshold, int happinessPenalty, double productionMultiplier) {
        this.threshold = threshold;
        this.happinessPenalty = happinessPenalty;
        this.productionMultiplier = productionMultiplier;
    }

    public static DamageLevel of(int damage) {
        int safe = Math.max(0, Math.min(100, damage));
        DamageLevel result = PRISTINE;
        for (DamageLevel level : values()) {
            if (safe >= level.threshold) result = level;
        }
        return result;
    }
}
