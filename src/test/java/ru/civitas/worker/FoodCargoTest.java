package ru.civitas.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodCargoTest {
    @Test void cargoSumsNutritionAndItemCount() {
        FoodCargo cargo = new FoodCargo();
        cargo.add(Items.BREAD, 1);
        cargo.add(Items.CARROT, 2);
        assertEquals(5, cargo.totalFoodValue());
        assertEquals(3, cargo.totalItems());
    }
    @Test void cargoRoundTripsThroughNbt() {
        FoodCargo cargo = new FoodCargo();
        cargo.add(Items.BREAD, 2);
        cargo.add(Items.POTATO, 3);
        CompoundTag tag = new CompoundTag();
        cargo.save(tag, "cargo");
        FoodCargo loaded = FoodCargo.load(tag, "cargo");
        assertEquals(cargo.totalFoodValue(), loaded.totalFoodValue());
        assertEquals(cargo.totalItems(), loaded.totalItems());
    }
}
