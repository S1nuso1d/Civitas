package ru.civitas.data;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.civitas.worker.FoodCargo;
import ru.civitas.worker.FoodRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Atomic food-inventory operations shared by farming, daily upkeep and the dashboard. */
public final class StorageFoodExtension {
    private StorageFoodExtension() { }

    public static int totalFoodValue(Container container) {
        if (container == null) return 0;
        long total = 0L;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += FoodRegistry.totalValue(container.getItem(slot));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Removes whole items until their nutrition value meets amount or the store is exhausted. */
    public static int consumeFood(Container container, int amount) {
        if (container == null || amount <= 0) return 0;
        long consumed = 0L;
        for (int slot = 0; slot < container.getContainerSize() && consumed < amount; slot++) {
            ItemStack stack = container.getItem(slot);
            int perItem = FoodRegistry.foodValue(stack);
            if (stack.isEmpty() || perItem <= 0) continue;
            long need = (long) amount - consumed;
            int take = (int) Math.min(stack.getCount(), Math.max(1L, (need + perItem - 1L) / perItem));
            stack.shrink(take);
            consumed += (long) take * perItem;
            container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        container.setChanged();
        return (int) Math.min(Integer.MAX_VALUE, consumed);
    }

    /** Deposits all cargo or leaves inventory and cargo unchanged on failure. */
    public static boolean depositCargo(Container container, FoodCargo cargo) {
        if (cargo == null || cargo.isEmpty()) return true;
        if (container == null) return false;

        List<ItemStack> backup = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            backup.add(container.getItem(slot).copy());
        }

        for (Map.Entry<Item, Integer> entry : cargo.contents().entrySet()) {
            int remaining = entry.getValue();
            Item item = entry.getKey();
            while (remaining > 0) {
                int batch = Math.min(remaining, item.getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(item, batch);
                insert(container, stack);
                if (!stack.isEmpty()) {
                    restore(container, backup);
                    return false;
                }
                remaining -= batch;
            }
        }

        container.setChanged();
        cargo.clear();
        return true;
    }

    public static boolean removeOne(Container container, Item item) {
        if (container == null || item == null || item == Items.AIR) return false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            stack.shrink(1);
            container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            container.setChanged();
            return true;
        }
        return false;
    }

    private static void insert(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)
                    || !container.canPlaceItem(slot, stack)) continue;
            int cap = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int moved = Math.min(stack.getCount(), Math.max(0, cap - existing.getCount()));
            if (moved > 0) {
                existing.grow(moved);
                stack.shrink(moved);
                container.setItem(slot, existing);
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, stack)) continue;
            int moved = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), container.getMaxStackSize()));
            container.setItem(slot, new ItemStack(stack.getItem(), moved));
            stack.shrink(moved);
        }
    }

    private static void restore(Container container, List<ItemStack> backup) {
        for (int slot = 0; slot < backup.size(); slot++) container.setItem(slot, backup.get(slot).copy());
        container.setChanged();
    }
}
