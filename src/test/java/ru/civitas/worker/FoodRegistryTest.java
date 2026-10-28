package ru.civitas.worker;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodRegistryTest {
    @Test void breadHasThreeNutritionUnits() {
        assertTrue(FoodRegistry.isFood(new ItemStack(Items.BREAD)));
        assertEquals(3, FoodRegistry.foodValue(new ItemStack(Items.BREAD)));
    }
    @Test void foodValuesAreItemSpecific() {
        assertEquals(16, FoodRegistry.totalValue(new ItemStack(Items.WHEAT, 16)));
        assertEquals(8, FoodRegistry.totalValue(new ItemStack(Items.BAKED_POTATO, 4)));
        assertEquals(0, FoodRegistry.totalValue(new ItemStack(Items.STONE)));
    }
    @Test void seedsAreKeptForReplanting() {
        assertFalse(FoodRegistry.isFood(new ItemStack(Items.WHEAT_SEEDS)));
        assertEquals(0, FoodRegistry.foodValue(ItemStack.EMPTY));
    }
}
