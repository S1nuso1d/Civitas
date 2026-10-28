package ru.civitas.trade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class TradeOffer {
    private final ItemStack want;
    private final ItemStack offer;
    private final int limit;
    private int traded;

    public TradeOffer(ItemStack want, ItemStack offer, int limit) {
        this.want = want.copy();
        this.offer = offer.copy();
        this.limit = Math.max(1, Math.min(64, limit));
    }

    public ItemStack want() { return want.copy(); }
    public ItemStack offer() { return offer.copy(); }
    public int limit() { return limit; }
    public int traded() { return traded; }
    public boolean isAvailable() { return traded < limit; }
    public void markTraded() { if (isAvailable()) traded++; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("want_id", BuiltInRegistries.ITEM.getKey(want.getItem()).toString());
        tag.putInt("want_count", want.getCount());
        tag.putString("offer_id", BuiltInRegistries.ITEM.getKey(offer.getItem()).toString());
        tag.putInt("offer_count", offer.getCount());
        tag.putInt("limit", limit);
        tag.putInt("traded", traded);
        return tag;
    }

    public static TradeOffer load(CompoundTag tag) {
        Item wantItem = resolve(tag.getString("want_id"));
        Item offerItem = resolve(tag.getString("offer_id"));
        int wantCount = Math.max(1, Math.min(64, tag.getInt("want_count")));
        int offerCount = Math.max(1, Math.min(64, tag.getInt("offer_count")));
        TradeOffer result = new TradeOffer(new ItemStack(wantItem, wantCount),
                new ItemStack(offerItem, offerCount), tag.getInt("limit"));
        result.traded = Math.max(0, Math.min(result.limit, tag.getInt("traded")));
        return result;
    }

    private static Item resolve(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return net.minecraft.world.item.Items.AIR;
        }
        return BuiltInRegistries.ITEM.get(location);
    }

    public String describe() {
        return want.getCount() + "× " + want.getHoverName().getString() + " → "
                + offer.getCount() + "× " + offer.getHoverName().getString();
    }
}