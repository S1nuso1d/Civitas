package ru.civitas.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import ru.civitas.block.ModBlocks;
import ru.civitas.block.SettlementWarehouseBlockEntity;

public final class SettlementWarehouseMenu extends AbstractContainerMenu {
    private final Container warehouse;
    private final BlockPos warehousePos;

    public SettlementWarehouseMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, new net.minecraft.world.SimpleContainer(SettlementWarehouseBlockEntity.SLOT_COUNT),
                data.readBlockPos());
    }

    public SettlementWarehouseMenu(int containerId, Inventory playerInventory, Container warehouse, BlockPos pos) {
        super(ModMenus.WAREHOUSE.get(), containerId);
        checkContainerSize(warehouse, SettlementWarehouseBlockEntity.SLOT_COUNT);
        this.warehouse = warehouse;
        this.warehousePos = pos;
        warehouse.startOpen(playerInventory.player);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(warehouse, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 86 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 144));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return empty;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        int warehouseSlots = SettlementWarehouseBlockEntity.SLOT_COUNT;

        if (index < warehouseSlots) {
            if (!moveItemStackTo(source, warehouseSlots, slots.size(), true)) {
                return empty;
            }
        } else if (!moveItemStackTo(source, 0, warehouseSlots, false)) {
            return empty;
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (source.getCount() == original.getCount()) {
            return empty;
        }
        slot.onTake(player, source);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), warehousePos), player, ModBlocks.WAREHOUSE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        warehouse.stopOpen(player);
    }
}
