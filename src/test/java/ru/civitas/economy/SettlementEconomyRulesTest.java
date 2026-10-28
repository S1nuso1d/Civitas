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
    void shortagePreventsGrowth() {
        assertTrue(SettlementEconomyRules.isFed(5, 5));
        assertFalse(SettlementEconomyRules.isFed(4, 5));
        assertFalse(SettlementEconomyRules.canGrow(false, 1, 2));
    }

    @Test
    void excessNutritionIsCarriedAcrossDaysWithoutLosingFoodValue() {
        var firstDay = SettlementEconomyRules.resolveFoodConsumption(0, 3, 1);
        assertTrue(firstDay.fed());
        assertEquals(1, firstDay.suppliedNutrition());
        assertEquals(2, firstDay.nextCredit());

        var secondDay = SettlementEconomyRules.resolveFoodConsumption(firstDay.nextCredit(), 0, 1);
        assertTrue(secondDay.fed());
        assertEquals(1, secondDay.suppliedNutrition());
        assertEquals(1, secondDay.nextCredit());
    }

    @Test
    void shortageClearsUnusedCreditAndDoesNotPretendTheDayWasFed() {
        var result = SettlementEconomyRules.resolveFoodConsumption(1, 2, 5);
        assertFalse(result.fed());
        assertEquals(3, result.suppliedNutrition());
        assertEquals(0, result.nextCredit());
    }

    @Test
    void growthRequiresTwoFedDaysFreeBedAndFoodReserve() {
        assertFalse(SettlementEconomyRules.canGrow(true, 1, 1));
        assertFalse(SettlementEconomyRules.canGrow(true, 0, 2));
        assertFalse(SettlementEconomyRules.canGrow(true, 1, 2, 14, 5, 3));
        assertTrue(SettlementEconomyRules.canGrow(true, 1, 2, 15, 5, 3));
        assertEquals(15, SettlementEconomyRules.reserveForGrowth(5, 3));
    }
}
