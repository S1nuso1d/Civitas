package ru.civitas.trade;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NeighborCivilizationTest {
    private NeighborCivilization create() {
        return new NeighborCivilization(new TradePartner(UUID.fromString("00000000-0000-0000-0000-000000000123"),
                "Лесной Союз", TradePersonality.NEUTRAL, 250), 42L);
    }
    @Test void saveLoadPreservesDiplomacyAndEconomy() {
        NeighborCivilization original = create();
        original.setStance(NeighborCivilization.Stance.ALLIED);
        original.advanceToDay(5);
        NeighborCivilization loaded = NeighborCivilization.load(original.save());
        assertEquals(original.id(), loaded.id());
        assertEquals(original.stance(), loaded.stance());
        assertEquals(original.population(), loaded.population());
        assertEquals(original.food(), loaded.food());
        assertEquals(original.lastSimulatedDay(), loaded.lastSimulatedDay());
    }
    @Test void warDisablesTradeAndPeaceRestoresIt() {
        NeighborCivilization c = create();
        assertTrue(c.permitsTrade());
        c.setStance(NeighborCivilization.Stance.WAR);
        assertFalse(c.permitsTrade());
        c.setStance(NeighborCivilization.Stance.ALLIED);
        assertTrue(c.permitsTrade());
    }
    @Test void distantSimulationIsBoundedAndIdempotent() {
        NeighborCivilization c = create();
        assertEquals(30, c.advanceToDay(1000));
        int population = c.population();
        assertEquals(0, c.advanceToDay(1000));
        assertEquals(population, c.population());
        assertEquals(1000, c.lastSimulatedDay());
    }
    @Test void corruptLegacyValuesAreClamped() {
        CompoundTag tag = create().save();
        tag.putInt("population", -100);
        tag.putInt("food", Integer.MAX_VALUE);
        NeighborCivilization loaded = NeighborCivilization.load(tag);
        assertEquals(1, loaded.population());
        assertEquals(1_000_000, loaded.food());
    }
}