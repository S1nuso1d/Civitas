package ru.civitas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import ru.civitas.data.CivitasSavedData;

import java.util.Map;

public final class CivitasCommands {
    private CivitasCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("civitas")
                        .then(Commands.literal("help").executes(context -> help(context.getSource())))
                        .then(Commands.literal("status").executes(CivitasCommands::status))
                        .then(Commands.literal("found")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(CivitasCommands::found)))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(CivitasCommands::rename)))
                        .then(Commands.literal("storage").executes(CivitasCommands::storage))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
                                        .executes(CivitasCommands::deposit)))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
                                                .executes(CivitasCommands::withdraw))))
        );
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§lCivitas — команды прототипа"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas found <название> §7— основать поселение в текущей точке"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas status §7— состояние поселения"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas rename <название> §7— переименовать поселение"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas storage §7— содержимое виртуального склада"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas deposit <количество> §7— положить предметы из основной руки"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas withdraw <namespace:item> <количество> §7— забрать предметы"), false);
        source.sendSuccess(() -> Component.literal("§7В этой версии пока один объект поселения на мир. Физическое строительство и жители будут следующими этапами."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!data.isFounded()) {
            context.getSource().sendFailure(Component.literal("Поселение ещё не создано. Используй: /civitas found Новая деревня"));
            return 0;
        }

        BlockPos pos = data.getCenter();
        context.getSource().sendSuccess(() -> Component.literal("§6§l" + data.getSettlementName()), false);
        context.getSource().sendSuccess(() -> Component.literal("Уровень: " + data.getLevel() + " | Население: " + data.getPopulation()), false);
        context.getSource().sendSuccess(() -> Component.literal("Управляющий: " + data.getOwnerName()), false);
        context.getSource().sendSuccess(() -> Component.literal("Центр: X=" + pos.getX() + " Y=" + pos.getY() + " Z=" + pos.getZ()), false);
        context.getSource().sendSuccess(() -> Component.literal("Единиц ресурсов на складе: " + data.getResources().values().stream().mapToInt(Integer::intValue).sum()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int found(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        String name = StringArgumentType.getString(context, "name");
        if (!data.found(name, player, player.blockPosition())) {
            context.getSource().sendFailure(Component.literal("В этом мире поселение Civitas уже создано."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("§aПоселение «" + data.getSettlementName() + "» основано!"), false);
        context.getSource().sendSuccess(() -> Component.literal("Начальное население: 5. Уровень развития: 1. Склад пуст."), false);
        context.getSource().sendSuccess(() -> Component.literal("Следующий шаг: §e/civitas help"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int rename(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!data.isFounded()) {
            context.getSource().sendFailure(Component.literal("Сначала создай поселение: /civitas found <название>"));
            return 0;
        }
        if (!data.isOwner(player) && !context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Переименовать поселение может только управляющий или администратор."));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        if (!data.rename(name)) {
            context.getSource().sendFailure(Component.literal("Название не может быть пустым."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Поселение переименовано в «" + data.getSettlementName() + "»."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int storage(CommandContext<CommandSourceStack> context) {
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!data.isFounded()) {
            context.getSource().sendFailure(Component.literal("Сначала создай поселение: /civitas found <название>"));
            return 0;
        }
        Map<String, Integer> resources = data.getResources();
        context.getSource().sendSuccess(() -> Component.literal("§6§lСклад поселения «" + data.getSettlementName() + "»"), false);
        if (resources.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Склад пуст. Держи предметы в основной руке и используй /civitas deposit <количество>"), false);
        } else {
            resources.forEach((itemId, amount) -> context.getSource().sendSuccess(
                    () -> Component.literal("• " + itemId + " × " + amount), false));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) {
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(context, "amount");
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Возьми предмет в основную руку."));
            return 0;
        }
        if (amount > held.getCount()) {
            context.getSource().sendFailure(Component.literal("В руке только " + held.getCount() + " предмет(ов)."));
            return 0;
        }
        if (!held.getComponentsPatch().isEmpty()) {
            context.getSource().sendFailure(Component.literal("Прототип склада принимает только обычные предметы без имени, повреждений, зачарований и других индивидуальных свойств."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!data.addResource(itemId, amount)) {
            context.getSource().sendFailure(Component.literal("Не удалось положить предметы на склад."));
            return 0;
        }
        held.shrink(amount);
        context.getSource().sendSuccess(() -> Component.literal("На склад добавлено: " + itemId + " × " + amount), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int withdraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) {
            return 0;
        }

        ResourceLocation id = ResourceLocationArgument.getId(context, "item");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            context.getSource().sendFailure(Component.literal("Предмет не найден в реестре Minecraft: " + id));
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            context.getSource().sendFailure(Component.literal("Нельзя забрать пустой предмет."));
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        if (!data.removeResource(itemId, amount)) {
            context.getSource().sendFailure(Component.literal("На складе недостаточно " + itemId + "."));
            return 0;
        }

        int remaining = amount;
        while (remaining > 0) {
            int batchSize = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, batchSize);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= batchSize;
        }
        context.getSource().sendSuccess(() -> Component.literal("Со склада выдано: " + itemId + " × " + amount), false);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean ensureOwner(CommandSourceStack source, ServerPlayer player, CivitasSavedData data) {
        if (!data.isFounded()) {
            source.sendFailure(Component.literal("Сначала создай поселение: /civitas found <название>"));
            return false;
        }
        if (!data.isOwner(player) && !source.hasPermission(2)) {
            source.sendFailure(Component.literal("Управлять складом может только управляющий или администратор."));
            return false;
        }
        return true;
    }
}
