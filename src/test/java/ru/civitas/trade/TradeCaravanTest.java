package ru.civitas.trade;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradeCaravanTest {
    @Test
    void tradeRequiresWaitingStateAndRemainingLimit() {
        TradeCaravan caravan = new TradeCaravan(UUID.randomUUID(), UUID.randomUUID(),
                BlockPos.ZERO, BlockPos.ZERO, 2);
        assertFalse(caravan.canTrade());
        caravan.setState(TradeCaravan.State.WAIT);
        assertTrue(caravan.canTrade());
        caravan.markTraded();
        caravan.markTraded();
        assertFalse(caravan.canTrade());
    }

    @Test
    void waitTimerCannotBecomeNegative() {
        TradeCaravan caravan = new TradeCaravan(UUID.randomUUID(), UUID.randomUUID(),
                BlockPos.ZERO, BlockPos.ZERO, 2);
        caravan.setWaitTicksLeft(1);
        caravan.tickWait();
        assertEquals(0, caravan.waitTicksLeft());
    }
}
