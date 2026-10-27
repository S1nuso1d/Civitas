package ru.civitas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.menu.SettlementWarehouseMenu;

import java.util.Map;

public final class SettlementWarehouseBlock extends Block implements EntityBlock {
    public SettlementWarehouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SettlementWarehouseBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                             Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        CivitasSavedData settlement = CivitasSavedData.get(serverPlayer.getServer());
        if (!settlement.isFounded()) {
            serverPlayer.sendSystemMessage(Component.literal("Сначала основай поселение: /civitas found <название>"));
            return InteractionResult.CONSUME;
        }
        if (!settlement.isOwner(serverPlayer) && !serverPlayer.hasPermissions(2)) {
            serverPlayer.sendSystemMessage(Component.literal("Управлять складом может только управляющий поселением или администратор."));
            return InteractionResult.CONSUME;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SettlementWarehouseBlockEntity warehouse)) {
            return InteractionResult.FAIL;
        }

        migrateLegacyResources(settlement, warehouse);

        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) ->
                        new SettlementWarehouseMenu(containerId, inventory, warehouse, pos),
                Component.translatable("block.civitas.warehouse")
        );
        serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    /**
     * Moves old Alpha 0.2 virtual resources into free physical slots. Only the
     * exact number inserted is removed from SavedData, so full warehouses keep
     * any remainder in the legacy store instead of losing it.
     */
    public static void migrateLegacyResources(CivitasSavedData settlement,
                                               SettlementWarehouseBlockEntity warehouse) {
        for (Map.Entry<String, Integer> entry : settlement.getResources().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }

            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) {
                continue;
            }

            int remaining = entry.getValue();
            int moved = 0;
            for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
                if (!warehouse.getItem(slot).isEmpty()) {
                    continue;
                }
                int batch = Math.min(remaining, Math.min(64, item.getDefaultMaxStackSize()));
                warehouse.setItem(slot, new ItemStack(item, batch));
                remaining -= batch;
                moved += batch;
            }

            if (moved > 0) {
                settlement.removeResource(entry.getKey(), moved);
            }
        }
    }
}
