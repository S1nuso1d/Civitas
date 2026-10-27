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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import ru.civitas.block.SettlementWarehouseBlock;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;

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
        source.sendSuccess(() -> Component.literal("§e/civitas storage §7— содержимое физического склада"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas deposit <количество> §7— положить предметы из основной руки"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas withdraw <namespace:item> <количество> §7— забрать предметы"), false);
        source.sendSuccess(() -> Component.literal("§7Команды склада и окно склада используют один инвентарь."), false);
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
        SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
        if (warehouse == null) {
            context.getSource().sendSuccess(() -> Component.literal("Физический склад не найден в центре деревни."), false);
        } else {
            int total = countAllItems(warehouse);
            context.getSource().sendSuccess(() -> Component.literal("Предметов в физическом складе: " + total), false);
        }
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
        context.getSource().sendSuccess(() -> Component.literal("Начальное население: 5. Уровень развития: 1."), false);
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
        if (!data.rename(StringArgumentType.getString(context, "name"))) {
            context.getSource().sendFailure(Component.literal("Название не может быть пустым."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Поселение переименовано в «" + data.getSettlementName() + "»."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int storage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
        if (warehouse == null) {
            context.getSource().sendFailure(Component.literal("Физический склад не найден. Проверь центральный склад деревни."));
            return 0;
        }
        SettlementWarehouseBlock.migrateLegacyResources(data, warehouse);
        context.getSource().sendSuccess(() -> Component.literal("§6§lСклад «" + data.getSettlementName() + "»"), false);
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (!stack.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int amount = stack.getCount();
                context.getSource().sendSuccess(() -> Component.literal("• " + itemId + " × " + amount), false);
            }
        }
        int total = countAllItems(warehouse);
        context.getSource().sendSuccess(() -> Component.literal("Всего предметов: " + total + " / " + (warehouse.getContainerSize() * 64)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
        if (warehouse == null) {
            context.getSource().sendFailure(Component.literal("Физический склад не найден. Положить предметы некуда."));
            return 0;
        }
        SettlementWarehouseBlock.migrateLegacyResources(data, warehouse);

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
            context.getSource().sendFailure(Component.literal("Склад принимает только обычные предметы без индивидуальных свойств."));
            return 0;
        }

        int capacity = 0;
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack existing = warehouse.getItem(slot);
            if (existing.isEmpty()) {
                capacity += Math.min(64, held.getMaxStackSize());
            } else if (ItemStack.isSameItemSameComponents(existing, held)) {
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
            if (capacity >= amount) break;
        }
        if (capacity < amount) {
            context.getSource().sendFailure(Component.literal("В физическом складе недостаточно свободного места. Для этого предмета свободно: " + capacity + "."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int remaining = amount;
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = warehouse.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, held)) {
                int moved = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    warehouse.setChanged();
                    remaining -= moved;
                }
            }
        }
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            if (warehouse.getItem(slot).isEmpty()) {
                int moved = Math.min(remaining, held.getMaxStackSize());
                warehouse.setItem(slot, held.copyWithCount(moved));
                remaining -= moved;
            }
        }
        held.shrink(amount);
        context.getSource().sendSuccess(() -> Component.literal("На физический склад добавлено: " + itemId + " × " + amount), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int withdraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
        if (warehouse == null) {
            context.getSource().sendFailure(Component.literal("Физический склад не найден."));
            return 0;
        }
        SettlementWarehouseBlock.migrateLegacyResources(data, warehouse);

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
        int available = 0;
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) available += stack.getCount();
        }
        if (available < amount) {
            context.getSource().sendFailure(Component.literal("На физическом складе только " + available + " предмет(ов) " + id + "."));
            return 0;
        }

        int remaining = amount;
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            int moved = Math.min(remaining, stack.getCount());
            stack.shrink(moved);
            if (stack.isEmpty()) warehouse.setItem(slot, ItemStack.EMPTY);
            else warehouse.setChanged();
            remaining -= moved;
        }

        int toGive = amount;
        while (toGive > 0) {
            int batchSize = Math.min(toGive, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, batchSize);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            toGive -= batchSize;
        }
        context.getSource().sendSuccess(() -> Component.literal("С физического склада выдано: " + id + " × " + amount), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int countAllItems(SettlementWarehouseBlockEntity warehouse) {
        int total = 0;
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            total += warehouse.getItem(slot).getCount();
        }
        return total;
    }

    private static SettlementWarehouseBlockEntity findWarehouse(CommandSourceStack source, CivitasSavedData data) {
        ServerLevel overworld = source.getServer().overworld();
        BlockPos pos = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (overworld.getBlockEntity(pos) instanceof SettlementWarehouseBlockEntity warehouse) return warehouse;
        return null;
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
