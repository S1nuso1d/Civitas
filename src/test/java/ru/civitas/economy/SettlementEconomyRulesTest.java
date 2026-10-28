package ru.civitas.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettlementEconomyRulesTest {
    @Test
    void foodNeedMatchesPopulation() {
        assertEquals(5, SettlementEconomyRules.dailyNeed(5));
        assertEquals(0, SettlementEconomyRules.dailyNeed(0));
    }

    @Test
    void foodShortageStopsFeedingAndGrowth() {
        assertTrue(SettlementEconomyRules.isFed(5, 5));
        assertFalse(SettlementEconomyRules.isFed(4, 5));
        assertFalse(SettlementEconomyRules.canGrow(false, 1, 3));
    }

    @Test
    void growthNeedsThreeFedDaysAndFreeBed() {
        assertFalse(SettlementEconomyRules.canGrow(true, 1, 2));
        assertFalse(SettlementEconomyRules.canGrow(true, 0, 3));
        assertTrue(SettlementEconomyRules.canGrow(true, 1, 3));
    }
}
