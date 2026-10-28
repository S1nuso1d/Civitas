package ru.civitas.worker;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Converts stored food items into abstract nutrition units used by settlement upkeep. */
public final class FoodRegistry {
    private static final Map<Item, Integer> VALUES;
    private static final Set<Item> FOODS;

    static {
        Map<Item, Integer> values = new LinkedHashMap<>();
        values.put(Items.WHEAT, 1);
        values.put(Items.BREAD, 3);
        values.put(Items.POTATO, 1);
        values.put(Items.BAKED_POTATO, 2);
        values.put(Items.CARROT, 1);
        values.put(Items.GOLDEN_CARROT, 4);
        values.put(Items.BEETROOT, 1);
        values.put(Items.BEETROOT_SOUP, 3);
        values.put(Items.APPLE, 1);
        values.put(Items.MELON_SLICE, 1);
        values.put(Items.PUMPKIN_PIE, 4);
        values.put(Items.SWEET_BERRIES, 1);
        VALUES = Collections.unmodifiableMap(values);
        FOODS = Collections.unmodifiableSet(VALUES.keySet());
    }

    private FoodRegistry() { }

    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && FOODS.contains(stack.getItem());
    }

    public static boolean isFood(Item item) {
        return item != null && FOODS.contains(item);
    }

    public static int foodValue(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : foodValue(stack.getItem());
    }

    public static int foodValue(Item item) {
        return VALUES.getOrDefault(item, 0);
    }

    public static int totalValue(ItemStack stack) {
        if (!isFood(stack)) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (long) foodValue(stack.getItem()) * stack.getCount());
    }

    public static Set<Item> allFoods() {
        return FOODS;
    }

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
