package ru.civitas.worker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodRegistryTest {
    @Test void breadHasThreeNutritionUnits() {
        assertTrue(FoodRegistry.isFood("minecraft:bread"));
        assertEquals(3, FoodRegistry.foodValue("minecraft:bread"));
    }

    @Test void foodValuesAreItemSpecific() {
        assertEquals(16, FoodRegistry.totalValue("minecraft:wheat", 16));
        assertEquals(8, FoodRegistry.totalValue("minecraft:baked_potato", 4));
        assertEquals(0, FoodRegistry.foodValue("minecraft:stone"));
    }

    @Test void seedsAreKeptForReplanting() {
        assertFalse(FoodRegistry.isFood("minecraft:wheat_seeds"));
        assertEquals(0, FoodRegistry.foodValue("minecraft:wheat_seeds"));
    }
}
