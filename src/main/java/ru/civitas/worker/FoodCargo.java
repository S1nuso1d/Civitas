package ru.civitas.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent food cargo stored by stable item IDs, so serialization needs no active game registry. */
public final class FoodCargo {
    private final Map<String, Integer> contents = new LinkedHashMap<>();

    public void add(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        add(FoodRegistry.itemId(stack.getItem()), stack.getCount());
    }

    public void add(Item item, int count) {
        if (item == null) return;
        add(FoodRegistry.itemId(item), count);
    }

    public void add(String itemId, int count) {
        if (!FoodRegistry.isFood(itemId) || count <= 0) return;
        long sum = (long) contents.getOrDefault(itemId, 0) + count;
        contents.put(itemId, (int) Math.min(Integer.MAX_VALUE, sum));
    }

    public boolean isEmpty() { return contents.isEmpty(); }

    public int totalFoodValue() {
        long total = 0L;
        for (var entry : contents.entrySet()) {
            total += (long) FoodRegistry.foodValue(entry.getKey()) * entry.getValue();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int totalItems() {
        long total = 0L;
        for (int count : contents.values()) total += count;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public Map<String, Integer> contents() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(contents));
    }

    public void remove(String itemId, int count) {
        if (itemId == null || count <= 0) return;
        int remaining = contents.getOrDefault(itemId, 0) - count;
        if (remaining <= 0) contents.remove(itemId);
        else contents.put(itemId, remaining);
    }

    public void remove(Item item, int count) {
        if (item != null) remove(FoodRegistry.itemId(item), count);
    }

    public void clear() { contents.clear(); }

    public void save(CompoundTag tag, String key) {
        ListTag list = new ListTag();
        for (var entry : contents.entrySet()) {
            if (entry.getValue() <= 0) continue;
            CompoundTag encoded = new CompoundTag();
            encoded.putString("id", entry.getKey());
            encoded.putInt("count", entry.getValue());
            list.add(encoded);
        }
        tag.put(key, list);
    }

    public static FoodCargo load(CompoundTag tag, String key) {
        FoodCargo cargo = new FoodCargo();
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) return cargo;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag encoded = list.getCompound(i);
            cargo.add(encoded.getString("id"), encoded.getInt("count"));
        }
        return cargo;
    }
}
