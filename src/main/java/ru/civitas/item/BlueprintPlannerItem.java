package ru.civitas.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ru.civitas.client.ClientHooks;

/** Opens the in-game building catalog; all actual placement is validated by the server. */
public final class BlueprintPlannerItem extends Item {
    public BlueprintPlannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> ClientHooks::openBlueprintCatalog);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
