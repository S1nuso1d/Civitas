package ru.civitas.worker;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodCargoTest {
    @Test void cargoSumsNutritionAndItemCount() {
        FoodCargo cargo = new FoodCargo();
        cargo.add("minecraft:bread", 1);
        cargo.add("minecraft:carrot", 2);
        assertEquals(5, cargo.totalFoodValue());
        assertEquals(3, cargo.totalItems());
        assertTrue(cargo.contents().containsKey("minecraft:bread"));
    }

    @Test void cargoRoundTripsThroughNbt() {
        FoodCargo cargo = new FoodCargo();
        cargo.add("minecraft:bread", 2);
        cargo.add("minecraft:potato", 3);
        CompoundTag tag = new CompoundTag();
        cargo.save(tag, "cargo");
        FoodCargo loaded = FoodCargo.load(tag, "cargo");
        assertEquals(cargo.contents(), loaded.contents());
        assertEquals(cargo.totalFoodValue(), loaded.totalFoodValue());
        assertEquals(cargo.totalItems(), loaded.totalItems());
    }

    @Test void cargoRejectsNonFoodAndUnknownItems() {
        FoodCargo cargo = new FoodCargo();
        cargo.add("minecraft:stone", 10);
        cargo.add("minecraft:unknown_food", 2);
        assertTrue(cargo.isEmpty());
    }
}
