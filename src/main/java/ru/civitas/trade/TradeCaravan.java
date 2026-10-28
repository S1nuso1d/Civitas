package ru.civitas.trade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TradeCaravan {
    public enum State { APPROACH, WAIT, LEAVE }
    private final UUID partnerId;
    private final UUID traderId;
    private final BlockPos tradePoint;
    private final BlockPos spawnPoint;
    private State state = State.APPROACH;
    private int tradesUsed;
    private final int tradeLimit;
    private int waitTicksLeft = 3600;
    private int missingTicks;
    private final List<TradeOffer> offers = new ArrayList<>();

    public TradeCaravan(UUID partnerId, UUID traderId, BlockPos tradePoint, BlockPos spawnPoint, int tradeLimit) {
        this.partnerId = partnerId;
        this.traderId = traderId;
        this.tradePoint = tradePoint.immutable();
        this.spawnPoint = spawnPoint.immutable();
        this.tradeLimit = Math.max(1, Math.min(20, tradeLimit));
    }

    public UUID partnerId() { return partnerId; }
    public UUID traderId() { return traderId; }
    public BlockPos tradePoint() { return tradePoint; }
    public BlockPos spawnPoint() { return spawnPoint; }
    public State state() { return state; }
    public void setState(State state) { if (state != null) this.state = state; }
    public int tradesUsed() { return tradesUsed; }
    public int tradeLimit() { return tradeLimit; }
    public int waitTicksLeft() { return waitTicksLeft; }
    public void setWaitTicksLeft(int ticks) { waitTicksLeft = Math.max(0, Math.min(72000, ticks)); }
    public void setWaitTicksLeft(int ticks) { waitTicksLeft = Math.max(0, Math.min(72000, ticks)); }
    public void tickWait() { if (waitTicksLeft > 0) waitTicksLeft--; }
    public int missingTicks() { return missingTicks; }
    public void tickMissing() { missingTicks = Math.min(1200, missingTicks + 20); }
    public void resetMissing() { missingTicks = 0; }
    public List<TradeOffer> offers() { return offers; }
    public boolean canTrade() { return state == State.WAIT && tradesUsed < tradeLimit && waitTicksLeft > 0; }
    public void markTraded() { if (canTrade()) tradesUsed++; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("partner", partnerId);
        tag.putUUID("trader", traderId);
        tag.putLong("trade_point", tradePoint.asLong());
        tag.putLong("spawn_point", spawnPoint.asLong());
        tag.putString("state", state.name());
        tag.putInt("trades_used", tradesUsed);
        tag.putInt("trade_limit", tradeLimit);
        tag.putInt("wait_ticks_left", waitTicksLeft);
        tag.putInt("missing_ticks", missingTicks);
        ListTag list = new ListTag();
        for (TradeOffer offer : offers) list.add(offer.save());
        tag.put("offers", list);
        return tag;
    }

    public static TradeCaravan load(CompoundTag tag) {
        UUID partner = tag.hasUUID("partner") ? tag.getUUID("partner") : UUID.randomUUID();
        UUID trader = tag.hasUUID("trader") ? tag.getUUID("trader") : UUID.randomUUID();
        TradeCaravan result = new TradeCaravan(partner, trader,
                BlockPos.of(tag.getLong("trade_point")), BlockPos.of(tag.getLong("spawn_point")),
                tag.getInt("trade_limit"));
        try { result.state = State.valueOf(tag.getString("state")); }
        catch (IllegalArgumentException ignored) { result.state = State.APPROACH; }
        result.tradesUsed = Math.max(0, Math.min(result.tradeLimit, tag.getInt("trades_used")));
        result.waitTicksLeft = Math.max(0, Math.min(72000, tag.getInt("wait_ticks_left")));
        result.missingTicks = Math.max(0, Math.min(1200, tag.getInt("missing_ticks")));
        ListTag list = tag.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(16, list.size()); i++) {
            TradeOffer offer = TradeOffer.load(list.getCompound(i));
            if (!offer.want().isEmpty() && !offer.offer().isEmpty()) result.offers.add(offer);
        }
        return result;
    }
}