package ru.civitas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ru.civitas.client.ClientHooks;

/**
 * First physical warehouse prototype. Interacting with it opens the settlement
 * management panel; inventory persistence remains in CivitasSavedData for now.
 */
public final class SettlementWarehouseBlock extends Block {
    public SettlementWarehouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openSettlementScreen);
        }
        return InteractionResult.SUCCESS;
    }
}
