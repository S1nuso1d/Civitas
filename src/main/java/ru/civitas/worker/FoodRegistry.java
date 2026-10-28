package ru.civitas.worker;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Nutrition units by stable item ID. This registry has no dependency on Minecraft startup order. */
public final class FoodRegistry {
    private static final Map<String, Integer> VALUES;
    private static final Set<String> FOODS;

    static {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("minecraft:wheat", 1);
        values.put("minecraft:bread", 3);
        values.put("minecraft:potato", 1);
        values.put("minecraft:baked_potato", 2);
        values.put("minecraft:carrot", 1);
        values.put("minecraft:golden_carrot", 4);
        values.put("minecraft:beetroot", 1);
        values.put("minecraft:beetroot_soup", 3);
        values.put("minecraft:apple", 1);
        values.put("minecraft:melon_slice", 1);
        values.put("minecraft:pumpkin_pie", 4);
        values.put("minecraft:sweet_berries", 1);
        VALUES = Collections.unmodifiableMap(values);
        FOODS = Collections.unmodifiableSet(VALUES.keySet());
    }

    private FoodRegistry() { }

    public static boolean isFood(String itemId) {
        return itemId != null && VALUES.containsKey(itemId);
    }

    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && isFood(itemId(stack.getItem()));
    }

    public static boolean isFood(Item item) {
        return item != null && isFood(itemId(item));
    }

    public static int foodValue(String itemId) {
        return itemId == null ? 0 : VALUES.getOrDefault(itemId, 0);
    }

    public static int foodValue(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : foodValue(stack.getItem());
    }

    public static int foodValue(Item item) {
        return item == null ? 0 : foodValue(itemId(item));
    }

    public static int totalValue(String itemId, int count) {
        if (!isFood(itemId) || count <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (long) foodValue(itemId) * count);
    }

    public static int totalValue(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : totalValue(itemId(stack.getItem()), stack.getCount());
    }

    public static Set<String> allFoods() {
        return FOODS;
    }

    public static String itemId(Item item) {
        return item == null ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
