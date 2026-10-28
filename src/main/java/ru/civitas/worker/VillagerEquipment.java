package ru.civitas.worker;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Visual role markers; worker logic and cargo remain independent of equipment. */
public final class VillagerEquipment {
    private VillagerEquipment() {
    }

    public static void ensure(Villager villager, String role) {
        ItemStack expected = toolFor(role);
        if (!ItemStack.isSameItem(villager.getMainHandItem(), expected)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, expected);
        }
    }

    public static void apply(Villager villager, String role) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, toolFor(role));
    }

    private static ItemStack toolFor(String role) {
        return switch (role) {
            case "lumberjack" -> new ItemStack(Items.IRON_AXE);
            case "builder" -> new ItemStack(Items.IRON_SHOVEL);
            case "farmer" -> new ItemStack(Items.IRON_HOE);
            default -> ItemStack.EMPTY;
        };
    }

    public static void clear(Villager villager) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
}
