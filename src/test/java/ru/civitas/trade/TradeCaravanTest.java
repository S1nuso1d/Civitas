package ru.civitas.trade;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradeCaravanTest {
    @Test
    void nbtRoundTripPreservesLifecycleAndOffers() {
        UUID partner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID trader = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TradeCaravan original = new TradeCaravan(partner, trader, new BlockPos(10, 64, 20), new BlockPos(50, 64, 20), 3);
        original.setState(TradeCaravan.State.WAIT);
        original.setWaitTicksLeft(800);
        original.offers().add(new TradeOffer(new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.BREAD, 8), 3));
        original.offers().get(0).markTraded();

        TradeCaravan restored = TradeCaravan.load(original.save());
        assertEquals(original.partnerId(), restored.partnerId());
        assertEquals(original.traderId(), restored.traderId());
        assertEquals(original.state(), restored.state());
        assertEquals(original.waitTicksLeft(), restored.waitTicksLeft());
        assertEquals(1, restored.offers().size());
        assertEquals(1, restored.offers().get(0).traded());
        assertEquals(Items.OAK_LOG, restored.offers().get(0).want().getItem());
    }

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
}
