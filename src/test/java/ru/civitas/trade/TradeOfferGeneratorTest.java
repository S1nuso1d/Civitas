package ru.civitas.trade;

import org.junit.jupiter.api.Test;
import ru.civitas.data.EconomyHistory;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradeOfferGeneratorTest {
    @Test
    void samePartnerAndDayProduceSameOffers() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        EconomyHistory history = new EconomyHistory(30);
        history.recordDaily(1, 100, 50, 30, 5);
        var first = TradeOfferGenerator.generate(id, 5, TradePersonality.NEUTRAL, history);
        var second = TradeOfferGenerator.generate(id, 5, TradePersonality.NEUTRAL, history);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).want().getItem(), second.get(i).want().getItem());
            assertEquals(first.get(i).want().getCount(), second.get(i).want().getCount());
            assertEquals(first.get(i).offer().getItem(), second.get(i).offer().getItem());
            assertEquals(first.get(i).offer().getCount(), second.get(i).offer().getCount());
        }
    }

    @Test
    void everyGeneratedOfferHasPositiveCountsAndFiniteLimit() {
        var offers = TradeOfferGenerator.generate(UUID.randomUUID(), 42, TradePersonality.FRIENDLY, new EconomyHistory(30));
        assertFalse(offers.isEmpty());
        for (TradeOffer offer : offers) {
            assertTrue(offer.want().getCount() > 0);
            assertTrue(offer.offer().getCount() > 0);
            assertTrue(offer.limit() >= 1 && offer.limit() <= 64);
        }
    }
}
