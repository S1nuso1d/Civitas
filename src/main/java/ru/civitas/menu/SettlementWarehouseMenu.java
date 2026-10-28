package ru.civitas.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import ru.civitas.block.ModBlocks;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;

public final class SettlementWarehouseMenu extends AbstractContainerMenu {
    private final Container warehouse;
    private final BlockPos warehousePos;

    public SettlementWarehouseMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory,
                new net.minecraft.world.SimpleContainer(SettlementWarehouseBlockEntity.SLOT_COUNT),
                data.readBlockPos());
    }

    public SettlementWarehouseMenu(int containerId, Inventory playerInventory, Container warehouse, BlockPos pos) {
        super(ModMenus.WAREHOUSE.get(), containerId);
        checkContainerSize(warehouse, SettlementWarehouseBlockEntity.SLOT_COUNT);
        this.warehouse = warehouse;
        this.warehousePos = pos;
        warehouse.startOpen(playerInventory.player);

        // Warehouse slots enforce reservation rules for normal clicks as well as
        // shift-clicks. Players can still deposit items, but cannot withdraw
        // materials promised to active construction orders.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new ProtectedWarehouseSlot(warehouse, col + row * 9,
                        8 + col * 18, 18 + row * 18));
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
        if (index < 0 || index >= slots.size()) {
            return empty;
        }

        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return empty;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        int warehouseSlots = SettlementWarehouseBlockEntity.SLOT_COUNT;

        if (index < warehouseSlots) {
            int allowed = Math.min(source.getCount(), availableToWithdraw(source));
            if (allowed <= 0) {
                return empty;
            }

            // Transfer a copy capped to unreserved stock, then remove only what
            // actually fit in the player's inventory from the warehouse stack.
            ItemStack transfer = source.copyWithCount(allowed);
            int offered = transfer.getCount();
            if (!moveItemStackTo(transfer, warehouseSlots, slots.size(), true)) {
                return empty;
            }
            int moved = offered - transfer.getCount();
            if (moved <= 0) {
                return empty;
            }
            source.shrink(moved);
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

    private int availableToWithdraw(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int physical = 0;
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack stored = warehouse.getItem(slot);
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, stack)) {
                physical += stored.getCount();
            }
        }

        int reserved = 0;
        if (warehouse instanceof SettlementWarehouseBlockEntity blockEntity) {
            Level level = blockEntity.getLevel();
            if (level != null && !level.isClientSide && level.getServer() != null) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                reserved = CivitasSavedData.get(level.getServer())
                        .getWarehouseReservedResources().getOrDefault(itemId, 0);
            }
        }
        return Math.max(0, physical - reserved);
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

    private final class ProtectedWarehouseSlot extends Slot {
        private ProtectedWarehouseSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return super.mayPickup(player) && availableToWithdraw(getItem()) > 0;
        }

        @Override
        public ItemStack remove(int amount) {
            int allowed = Math.min(amount, availableToWithdraw(getItem()));
            return allowed <= 0 ? ItemStack.EMPTY : super.remove(allowed);
        }
    }
}
