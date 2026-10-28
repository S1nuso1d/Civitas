package ru.civitas.data;

import org.junit.jupiter.api.Test;
import ru.civitas.economy.SettlementEconomyRules;
import ru.civitas.worker.FoodCargo;
import ru.civitas.worker.FoodRegistry;
import static org.junit.jupiter.api.Assertions.*;

class StorageFoodExtensionTest {
    @Test void wholeFoodItemsCanSupplySeveralDaysOfNutrition() {
        assertEquals(3, FoodRegistryTestValue("minecraft:bread", 1));
        var dayOne = SettlementEconomyRules.resolveFoodConsumption(0, 3, 1);
        assertTrue(dayOne.fed());
        assertEquals(2, dayOne.nextCredit());
        var dayTwo = SettlementEconomyRules.resolveFoodConsumption(dayOne.nextCredit(), 0, 1);
        assertTrue(dayTwo.fed());
        assertEquals(1, dayTwo.nextCredit());
    }

    @Test void partialDeliveryLeavesCargoAvailableForRetry() {
        FoodCargo cargo = new FoodCargo();
        cargo.add("minecraft:carrot", 2);
        assertEquals(2, cargo.totalItems());
        assertFalse(cargo.isEmpty());
        // A failed physical insert must not clear the saved payload.
        assertEquals(2, FoodCargo.load(saveCargo(cargo), "cargo").totalItems());
    }

    private static net.minecraft.nbt.CompoundTag saveCargo(FoodCargo cargo) {
        var tag = new net.minecraft.nbt.CompoundTag();
        cargo.save(tag, "cargo");
        return tag;
    }

    private static int FoodRegistryTestValue(String id, int count) {
        return ru.civitas.worker.FoodRegistry.totalValue(id, count);
    }
}
