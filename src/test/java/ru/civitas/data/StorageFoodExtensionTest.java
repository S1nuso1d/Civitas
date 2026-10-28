package ru.civitas.data;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import ru.civitas.worker.FoodCargo;
import static org.junit.jupiter.api.Assertions.*;

class StorageFoodExtensionTest {
    @Test void consumptionReportsFullNutritionOfWholeItems() {
        SimpleContainer inventory = new SimpleContainer(2);
        inventory.setItem(0, new ItemStack(Items.BREAD, 2));
        assertEquals(6, StorageFoodExtension.consumeFood(inventory, 5));
        assertTrue(inventory.isEmpty());
    }
    @Test void cargoDepositIsAllOrNothing() {
        SimpleContainer full = new SimpleContainer(1);
        full.setItem(0, new ItemStack(Items.BREAD, 64));
        FoodCargo cargo = new FoodCargo();
        cargo.add(Items.CARROT, 1);
        assertFalse(StorageFoodExtension.depositCargo(full, cargo));
        assertFalse(cargo.isEmpty());
        assertEquals(64, full.getItem(0).getCount());
    }
    @Test void cargoDepositsWhenSpaceExists() {
        SimpleContainer inventory = new SimpleContainer(3);
        FoodCargo cargo = new FoodCargo();
        cargo.add(Items.CARROT, 2);
        assertTrue(StorageFoodExtension.depositCargo(inventory, cargo));
        assertTrue(cargo.isEmpty());
        assertEquals(2, inventory.getItem(0).getCount());
    }
}
