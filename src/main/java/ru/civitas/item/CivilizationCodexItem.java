package ru.civitas.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** In-game guide for the current Civitas prototype. */
public final class CivilizationCodexItem extends Item {
    public CivilizationCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.sendSystemMessage(Component.literal("§6§lCIVITAS — КОДЕКС ЦИВИЛИЗАЦИИ"));
            player.sendSystemMessage(Component.literal("§eВаша роль: §fуправлять поселением, а не добывать все ресурсы самостоятельно."));
            player.sendSystemMessage(Component.literal("§eСоздать поселение: §f/civitas found <название>"));
            player.sendSystemMessage(Component.literal("§eСостояние: §f/civitas status §7| §eСклад: §f/civitas storage"));
            player.sendSystemMessage(Component.literal("§eСдать предметы: §fдержите их в основной руке и выполните /civitas deposit <количество>"));
            player.sendSystemMessage(Component.literal("§eЗабрать предметы: §f/civitas withdraw <item_id> <количество>"));
            player.sendSystemMessage(Component.literal("§7Склад пока виртуальный и сохраняется вместе с миром. Физическое здание склада появится позже."));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
