package ru.civitas.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryInfluenceTest {
    @Test
    void influenceGrowsWithBuildingsPopulationAndLevel() {
        var small = TerritoryInfluence.calculate(5, 1, List.of());
        var developed = TerritoryInfluence.calculate(80, 5, List.of(
                new CivitasSavedData.BuildingRecord("cabin", 0, 64, 0),
                new CivitasSavedData.BuildingRecord("farm", 8, 64, 0),
                new CivitasSavedData.BuildingRecord("workshop", -8, 64, 0),
                new CivitasSavedData.BuildingRecord("market", 0, 64, 8)));

        assertTrue(developed.score() > small.score());
        assertTrue(developed.radius() > small.radius());
    }

    @Test
    void influenceAndRadiusAreBounded() {
        var result = TerritoryInfluence.calculate(Integer.MAX_VALUE, Integer.MAX_VALUE,
                java.util.stream.IntStream.range(0, 500)
                        .mapToObj(i -> new CivitasSavedData.BuildingRecord("town_hall", i, 64, i))
                        .toList());

        assertEquals(1000, result.score());
        assertEquals(62, result.radius());
        assertEquals("Региональный центр", result.tier());
        assertTrue(result.buildingPoints() <= 600);
    }

    @Test
    void nullAndUnknownBuildingsAreHandledDeterministically() {
        var result = TerritoryInfluence.calculate(0, 1,
                java.util.Arrays.asList(null, new CivitasSavedData.BuildingRecord("decorative_pillar", 0, 64, 0)));

        assertEquals(13, result.score());
        assertEquals(12, result.radius());
    }
}
