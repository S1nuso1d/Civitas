package ru.civitas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.menu.SettlementWarehouseMenu;

public final class SettlementWarehouseBlock extends BaseEntityBlock {
    public SettlementWarehouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SettlementWarehouseBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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

        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) ->
                        new SettlementWarehouseMenu(containerId, inventory, warehouse, pos),
                Component.translatable("block.civitas.warehouse")
        );
        NetworkHooks.openScreen(serverPlayer, provider, pos);
        return InteractionResult.CONSUME;
    }
}
