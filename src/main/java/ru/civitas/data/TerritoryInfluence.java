package ru.civitas.data;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic, bounded estimate of how strongly a settlement develops and
 * supports its surrounding territory. This is an influence score, not a claim
 * that every block in a radius is owned by the settlement.
 */
public final class TerritoryInfluence {
    private TerritoryInfluence() { }

    public record Result(int score, int radius, String tier, int buildingPoints,
                         int populationPoints, int levelPoints) { }

    public static Result calculate(int population, int level,
                                   List<CivitasSavedData.BuildingRecord> buildings) {
        int safePopulation = Math.max(0, Math.min(1_000_000, population));
        int safeLevel = Math.max(1, Math.min(100_000, level));
        int buildingPoints = 0;
        if (buildings != null) {
            for (CivitasSavedData.BuildingRecord building : buildings) {
                if (building == null || building.type() == null) continue;
                String type = building.type().toLowerCase(Locale.ROOT);
                // Distinct structures contribute differently; construction
                // orders do not count until they are registered as completed.
                if (containsAny(type, "town_hall", "hall", "center", "bell_tower", "civic")) buildingPoints += 12;
                else if (containsAny(type, "workshop", "smith", "sawmill", "market")) buildingPoints += 8;
                else if (containsAny(type, "farm", "field", "granary")) buildingPoints += 6;
                else if (containsAny(type, "cabin", "house", "home", "hut")) buildingPoints += 5;
                else if (containsAny(type, "road", "path", "bridge")) buildingPoints += 2;
                else buildingPoints += 3;
                if (buildingPoints >= 600) { buildingPoints = 600; break; }
            }
        }
        int populationPoints = Math.min(250, safePopulation * 2 / 3);
        int levelPoints = Math.min(150, safeLevel * 10);
        int score = Math.max(0, Math.min(1000, buildingPoints + populationPoints + levelPoints));
        int radius = Math.max(12, Math.min(64, 12 + score / 20));
        String tier = score >= 700 ? "Региональный центр"
                : score >= 450 ? "Развитое поселение"
                : score >= 250 ? "Растущее поселение"
                : score >= 100 ? "Местная община" : "Небольшое поселение";
        return new Result(score, radius, tier, buildingPoints, populationPoints, levelPoints);
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }
}
