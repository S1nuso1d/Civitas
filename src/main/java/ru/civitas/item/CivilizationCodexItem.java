package ru.civitas.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ru.civitas.client.ClientHooks;

/** In-game guide and entry point to the Civitas management interface. */
public final class CivilizationCodexItem extends Item {
    public CivilizationCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openSettlementScreen);
        } else {
            player.sendSystemMessage(Component.literal("Civitas: панель управления поселением доступна через Кодекс цивилизации."));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
