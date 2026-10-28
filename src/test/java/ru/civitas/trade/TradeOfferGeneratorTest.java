package ru.civitas.trade;

import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradeOfferGeneratorTest {
    @Test
    void personalitiesHaveValidTradeLimitsAndOfferCounts() {
        for (TradePersonality personality : TradePersonality.values()) {
            assertTrue(personality.offerCount >= 1 && personality.offerCount <= 8);
            assertTrue(personality.tradeLimit >= 1 && personality.tradeLimit <= 20);
            assertTrue(personality.wantMultiplier > 0);
            assertTrue(personality.offerMultiplier > 0);
        }
    }

    @Test
    void partnerNameGenerationIsDeterministicForSameSeed() {
        assertEquals(TradePartner.generateName(new Random(1234)),
                TradePartner.generateName(new Random(1234)));
    }

    @Test
    void partnerArrivalIntervalIsBounded() {
        TradePartner partner = new TradePartner(UUID.randomUUID(), "Test Partner", TradePersonality.WARLIKE, 50_000);
        assertTrue(partner.arrivalIntervalDays() >= 2 && partner.arrivalIntervalDays() <= 30);
    }
}
