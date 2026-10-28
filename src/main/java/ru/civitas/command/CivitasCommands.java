package ru.civitas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import ru.civitas.block.SettlementWarehouseBlock;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.worker.ConstructionWorkerAI;
import ru.civitas.worker.LumberjackWorkerAI;
import ru.civitas.world.BuildingDefinition;
import ru.civitas.world.SettlementBuildingManager;
import ru.civitas.trade.NeighborCivilization;
import java.util.ArrayList;
import java.util.Comparator;

import java.util.Map;

public final class CivitasCommands {
    private CivitasCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("civitas")
                        .then(Commands.literal("help").executes(context -> help(context.getSource())))
                        .then(Commands.literal("diplomacy")
                                .executes(CivitasCommands::diplomacy)
                                .then(Commands.literal("stance")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1, 100))
                                                .then(Commands.argument("stance", StringArgumentType.word())
                                                        .suggests((context, builder) -> {
                                                            for (String value : new String[]{"neutral", "allied", "rival", "war"}) builder.suggest(value);
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(CivitasCommands::setDiplomaticStance)))))
                        .then(Commands.literal("visit")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, 100))
                                        .executes(CivitasCommands::visitNeighbor)))
                        .then(Commands.literal("return").executes(CivitasCommands::returnFromNeighbor))
                        .then(Commands.literal("status").executes(CivitasCommands::status))
                        .then(Commands.literal("territory").executes(CivitasCommands::territory))
                        .then(Commands.literal("overview").executes(CivitasCommands::overview))
                        .then(Commands.literal("found")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(CivitasCommands::found)))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(CivitasCommands::rename)))
                        .then(Commands.literal("workers").executes(CivitasCommands::workers))
                        .then(Commands.literal("buildings").executes(CivitasCommands::buildings))
                        .then(Commands.literal("construction")
                                .executes(CivitasCommands::construction)
                                .then(Commands.literal("cancel")
                                        .executes(CivitasCommands::cancelConstruction)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(CivitasCommands::cancelConstructionByIndex))))
                        .then(Commands.literal("preview")
                                .then(Commands.literal("cabin")
                                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                                .executes(ctx -> preview(ctx, BuildingDefinition.Type.CABIN, 0))
                                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 359))
                                                        .executes(ctx -> preview(ctx, BuildingDefinition.Type.CABIN,
                                                                IntegerArgumentType.getInteger(ctx, "rotation"))))))
                                .then(Commands.literal("farm")
                                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                                .executes(ctx -> preview(ctx, BuildingDefinition.Type.FARM, 0))
                                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 359))
                                                        .executes(ctx -> preview(ctx, BuildingDefinition.Type.FARM,
                                                                IntegerArgumentType.getInteger(ctx, "rotation"))))))
                                .then(Commands.literal("workshop")
                                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                                .executes(ctx -> preview(ctx, BuildingDefinition.Type.WORKSHOP, 0))
                                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 359))
                                                        .executes(ctx -> preview(ctx, BuildingDefinition.Type.WORKSHOP,
                                                                IntegerArgumentType.getInteger(ctx, "rotation")))))))
                        .then(Commands.literal("build")
                                .then(Commands.literal("cabin")
                                        .executes(ctx -> build(ctx, BuildingDefinition.Type.CABIN))
                                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                                .executes(ctx -> buildAt(ctx, BuildingDefinition.Type.CABIN, 0))
                                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 359))
                                                        .executes(ctx -> buildAt(ctx, BuildingDefinition.Type.CABIN,
                                                                IntegerArgumentType.getInteger(ctx, "rotation"))))))
                                .then(Commands.literal("farm")
                                        .executes(ctx -> build(ctx, BuildingDefinition.Type.FARM))
                                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                                .executes(ctx -> buildAt(ctx, BuildingDefinition.Type.FARM, 0))
                                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 359))
                                                        .executes(ctx -> buildAt(ctx, BuildingDefinition.Type.FARM,
                                                                IntegerArgumentType.getInteger(ctx, "rotation"))))))
                                .then(Commands.literal("workshop")
                                        .executes(ctx -> build(ctx, BuildingDefinition.Type.WORKSHOP))
                                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                                .executes(ctx -> buildAt(ctx, BuildingDefinition.Type.WORKSHOP, 0))
                                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 359))
                                                        .executes(ctx -> buildAt(ctx, BuildingDefinition.Type.WORKSHOP,
                                                                IntegerArgumentType.getInteger(ctx, "rotation")))))))
                        .then(Commands.literal("worker")
                                .then(Commands.literal("lumberjack")
                                        .executes(CivitasCommands::assignLumberjack)
                                        .then(Commands.argument("resident", EntityArgument.entity())
                                                .executes(CivitasCommands::assignSelectedLumberjack)))
                                .then(Commands.literal("builder")
                                        .executes(CivitasCommands::assignBuilder)
                                        .then(Commands.argument("resident", EntityArgument.entity())
                                                .executes(CivitasCommands::assignSelectedBuilder)))
                                .then(Commands.literal("pause").executes(context -> setLumberjackPaused(context, true)))
                                .then(Commands.literal("resume").executes(context -> setLumberjackPaused(context, false)))
                                .then(Commands.literal("clear").executes(CivitasCommands::clearLumberjack)))
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

    private static int visitNeighbor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        if (!context.getSource().getLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            context.getSource().sendFailure(Component.literal("Посещать соседние цивилизации можно только из обычного мира."));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index");
        java.util.List<NeighborCivilization> neighbors = new java.util.ArrayList<>(data.getNeighborCivilizations().values());
        neighbors.sort(Comparator.comparing(NeighborCivilization::name, String.CASE_INSENSITIVE_ORDER));
        if (index < 1 || index > neighbors.size()) {
            context.getSource().sendFailure(Component.literal("Цивилизация №" + index + " не найдена. Используй /civitas diplomacy."));
            return 0;
        }
        NeighborCivilization civilization = neighbors.get(index - 1);
        BlockPos origin = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        BlockPos site = ru.civitas.world.NeighborSettlementGenerator.ensureGenerated(
                context.getSource().getServer().overworld(), data, civilization, origin);
        if (site == null || !civilization.siteGenerated()) {
            context.getSource().sendFailure(Component.literal("Не удалось безопасно найти место для поселения. Мир не изменён; попробуй ещё раз позже."));
            return 0;
        }
        if (!ru.civitas.world.NeighborSettlementGenerator.visit(player, data, civilization)) {
            context.getSource().sendFailure(Component.literal("Не удалось посетить поселение."));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int returnFromNeighbor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!ru.civitas.world.NeighborSettlementGenerator.returnToSavedPosition(player)) {
            context.getSource().sendFailure(Component.literal("Нет сохранённой точки возврата. Сначала посети соседнюю цивилизацию через /civitas visit <номер>."));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§lCivitas — команды прототипа"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas found <название> §7— основать поселение в текущей точке"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas diplomacy §7— список соседних цивилизаций"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas visit <номер> §7— физически посетить соседнее поселение"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas return §7— вернуться из соседнего поселения"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas status §7— состояние поселения"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas overview §7— сводка поселения: жители, здания, стройка и склад"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas rename <название> §7— переименовать поселение"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas workers §7— список жителей и их роли"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas buildings §7— реестр построенных зданий"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas construction §7— очередь и прогресс строительства"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas preview cabin|farm|workshop <позиция> [угол] §7— показать чертёж частицами"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas build cabin|farm|workshop §7— автоматическая площадка; добавь координаты и угол для выбора места"), false);
        source.sendSuccess(() -> Component.literal("§7Угол: 0, 90, 180 или 270. Координаты — твёрдый блок земли под зданием; стоя на поверхности используй ~ ~-1 ~."), false);
        source.sendSuccess(() -> Component.literal("§e/civitas worker lumberjack §7— назначить лесоруба"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas worker builder|lumberjack <житель> §7— назначить выбранного жителя через селектор, например @e[type=minecraft:villager,sort=nearest,limit=1]"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas worker pause|resume §7— приостановить/продолжить"), false);
        source.sendSuccess(() -> Component.literal("§e/civitas worker clear §7— снять профессию после доставки груза"), false);
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

    /**
     * Combined strategic snapshot intended as a single entry point for the next dashboard iteration.
     * All values are derived from the authoritative server world and saved settlement data.
     */
    private static int territory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        var influence = data.getTerritoryInfluence();
        BlockPos center = data.getTerritoryCenter();
        BlockPos here = player.blockPosition();
        boolean inside = data.isWithinTerritory(here);
        context.getSource().sendSuccess(() -> Component.literal(
                "§6Территория поселения: §f" + influence.tier()
                        + "\n§7Влияние: §f" + influence.score() + "/1000"
                        + "\n§7Радиус: §f" + influence.radius() + " блоков"
                        + "\n§7Центр: §f" + center.getX() + ", " + center.getY() + ", " + center.getZ()
                        + "\n" + (inside ? "§aВы находитесь в зоне влияния." : "§cВы находитесь за пределами зоны влияния.")
                        + "\n§8Зона влияет на размещение новых построек; это не защита блоков и не граница чанков."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int overview(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;

        ServerLevel level = context.getSource().getServer().overworld();
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        AABB residentArea = new AABB(center).inflate(24, 12, 24);
        java.util.List<Villager> residents = level.getEntitiesOfClass(Villager.class, residentArea);
        int lumberjacks = 0;
        int builders = 0;
        int idle = 0;
        for (Villager villager : residents) {
            String role = villager.getPersistentData().getString("civitas.worker_role");
            if ("lumberjack".equals(role)) lumberjacks++;
            else if ("builder".equals(role)) builders++;
            else idle++;
        }

        int cabins = 0;
        int farms = 0;
        int workshops = 0;
        for (CivitasSavedData.BuildingRecord building : data.getBuildings()) {
            BuildingDefinition definition = BuildingDefinition.find(building.type()).orElse(null);
            if (definition == null) {
                continue;
            }
            switch (definition.type()) {
                case CABIN -> cabins++;
                case FARM -> farms++;
                case WORKSHOP -> workshops++;
            }
        }

        var orders = data.getConstructionOrders();
        int totalItems = 0;
        int occupiedSlots = 0;
        SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
        if (warehouse != null) {
            for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
                ItemStack stack = warehouse.getItem(slot);
                if (!stack.isEmpty()) {
                    totalItems += stack.getCount();
                    occupiedSlots++;
                }
            }
        }

        context.getSource().sendSuccess(() -> Component.literal("§6§lСводка Civitas — " + data.getSettlementName()), false);
        context.getSource().sendSuccess(() -> Component.literal("Развитие: уровень " + data.getLevel()
                + " | зарегистрировано зданий: " + data.getBuildings().size()
                + " | в очереди: " + orders.size()), false);
        final String residentSummary = "Жители в радиусе 24 блоков: " + residents.size()
                + " | лесорубы: " + lumberjacks + " | строители: " + builders + " | без профессии: " + idle;
        final String buildingSummary = "Здания: дома " + cabins + " | фермы " + farms
                + " | мастерские " + workshops;
        context.getSource().sendSuccess(() -> Component.literal(residentSummary), false);
        context.getSource().sendSuccess(() -> Component.literal(buildingSummary), false);
        if (warehouse == null) {
            context.getSource().sendSuccess(() -> Component.literal("§cСклад недоступен: проверь центральный блок склада."), false);
        } else {
            final int items = totalItems;
            final int slots = occupiedSlots;
            context.getSource().sendSuccess(() -> Component.literal("Склад: " + items + " предметов, занято слотов "
                    + slots + "/" + warehouse.getContainerSize()), false);
            if (slots >= warehouse.getContainerSize()) {
                context.getSource().sendSuccess(() -> Component.literal("§eПредупреждение: склад заполнен; работники могут не суметь сдать ресурсы."), false);
            }
        }

        if (orders.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Строительство: очередь пуста."), false);
        } else {
            for (int i = 0; i < Math.min(3, orders.size()); i++) {
                var order = orders.get(i);
                int progress = Math.min(100, order.progress() * 100 / Math.max(1, order.totalLayers()));
                String type = switch (order.type()) {
                    case "cabin" -> "Дом";
                    case "farm" -> "Ферма";
                    case "workshop" -> "Мастерская";
                    default -> order.type();
                };
                String line = "Стройка #" + (i + 1) + ": " + type + " — " + progress + "%";
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
            if (orders.size() > 3) {
                int remaining = orders.size() - 3;
                context.getSource().sendSuccess(() -> Component.literal("И ещё заказов: " + remaining
                        + ". Полный список: /civitas construction"), false);
            }
            if (builders == 0) {
                context.getSource().sendSuccess(() -> Component.literal("§eВнимание: назначь строителя командой /civitas worker builder."), false);
            }
        }
        context.getSource().sendSuccess(() -> Component.literal("Подробности: /civitas workers, /civitas buildings, /civitas construction"), false);
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

    private static int workers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        ServerLevel overworld = context.getSource().getServer().overworld();
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        AABB area = new AABB(center).inflate(24, 12, 24);
        java.util.List<Villager> villagers = overworld.getEntitiesOfClass(Villager.class, area);
        if (villagers.isEmpty()) {
            context.getSource().sendFailure(Component.literal("В окрестностях поселения не найдено жителей."));
            return 0;
        }
        final int villagerCount = villagers.size();
        context.getSource().sendSuccess(() -> Component.literal("§6§lЖители поселения: " + villagerCount), false);
        int index = 1;
        for (Villager villager : villagers) {
            final int number = index++;
            String role = LumberjackWorkerAI.getWorkerRole(villager);
            String displayRole = "lumberjack".equals(role) ? "Лесоруб" : "builder".equals(role) ? "Строитель" : "Без назначения";
            String name = villager.getCustomName() == null
                    ? villager.getName().getString()
                    : villager.getCustomName().getString();
            String activity = LumberjackWorkerAI.getWorkerStatus(villager);
            String line = number + ". " + name + " — " + displayRole + " | " + activity;
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Назначить жителя: /civitas worker lumberjack или /civitas worker builder"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assignLumberjack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        ServerLevel overworld = context.getSource().getServer().overworld();
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (LumberjackWorkerAI.assignNearestLumberjack(overworld, center)) {
            context.getSource().sendSuccess(() -> Component.literal("§aПрофессия «Лесоруб Civitas» назначена ближайшему жителю."), false);
            context.getSource().sendSuccess(() -> Component.literal("Проверь список командой /civitas workers."), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal("Не удалось найти жителя для назначения. Проверь, что жители есть рядом с деревней."));
        return 0;
    }

    private static int assignSelectedLumberjack(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        ServerLevel overworld = context.getSource().getServer().overworld();
        if (!context.getSource().getLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            context.getSource().sendFailure(Component.literal("Назначение профессий доступно только в обычном мире."));
            return 0;
        }
        var entity = EntityArgument.getEntity(context, "resident");
        if (!(entity instanceof Villager villager) || villager.level() != overworld) {
            context.getSource().sendFailure(Component.literal("Выбранная сущность не является жителем обычного мира."));
            return 0;
        }
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (!new AABB(center).inflate(24, 12, 24).contains(villager.getX(), villager.getY(), villager.getZ())) {
            context.getSource().sendFailure(Component.literal("Житель должен находиться рядом с поселением, в радиусе 24 блоков."));
            return 0;
        }
        if (!LumberjackWorkerAI.assignLumberjackTo(overworld, center, villager)) {
            context.getSource().sendFailure(Component.literal(
                    "Нельзя назначить лесоруба: другой лесоруб ещё несёт груз или выбранный житель занят."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("§aВыбранный житель назначен лесорубом Civitas."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assignSelectedBuilder(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        ServerLevel overworld = context.getSource().getServer().overworld();
        if (!context.getSource().getLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            context.getSource().sendFailure(Component.literal("Назначение профессий доступно только в обычном мире."));
            return 0;
        }
        var entity = EntityArgument.getEntity(context, "resident");
        if (!(entity instanceof Villager villager) || villager.level() != overworld) {
            context.getSource().sendFailure(Component.literal("Выбранная сущность не является жителем обычного мира."));
            return 0;
        }
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (!new AABB(center).inflate(24, 12, 24).contains(villager.getX(), villager.getY(), villager.getZ())) {
            context.getSource().sendFailure(Component.literal("Житель должен находиться рядом с поселением, в радиусе 24 блоков."));
            return 0;
        }

        String currentRole = LumberjackWorkerAI.getWorkerRole(villager);
        if ("builder".equals(currentRole)) {
            context.getSource().sendSuccess(() -> Component.literal("§eЭтот житель уже назначен строителем."), false);
            return Command.SINGLE_SUCCESS;
        }
        if (!ConstructionWorkerAI.assignBuilderTo(overworld, center, villager)) {
            context.getSource().sendFailure(Component.literal(
                    "Нельзя назначить строителя: дождись доставки груза лесорубом и проверь, что житель рядом с поселением."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal(
                "§aВыбранный житель назначен строителем. Активные заказы сохраняются и могут перейти другому строителю."),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assignBuilder(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        ServerLevel level = context.getSource().getServer().overworld();
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        AABB area = new AABB(center).inflate(24, 12, 24);
        java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        Villager chosen = null;
        double nearest = Double.MAX_VALUE;
        for (Villager villager : villagers) {
            String role = villager.getPersistentData().getString("civitas.worker_role");
            if ("lumberjack".equals(role) || "builder".equals(role)) continue;
            double distance = villager.distanceToSqr(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D);
            if (distance < nearest) { nearest = distance; chosen = villager; }
        }
        if (chosen == null) {
            context.getSource().sendFailure(Component.literal("Нет свободного жителя. Сними с одного жителя другую профессию или приведи нового."));
            return 0;
        }
        chosen.getPersistentData().putString("civitas.worker_role", "builder");
        chosen.setCustomName(Component.literal("Строитель Civitas"));
        chosen.setCustomNameVisible(true);
        chosen.setPersistenceRequired();
        int assignedBuilders = 0;
        for (Villager villager : villagers) {
            if ("builder".equals(villager.getPersistentData().getString("civitas.worker_role"))) {
                assignedBuilders++;
            }
        }
        final int totalBuilders = assignedBuilders;
        context.getSource().sendSuccess(() -> Component.literal(
                "§aНазначен дополнительный строитель. Всего строителей: " + totalBuilders
                        + ". Работники могут строить разные заказы параллельно."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setLumberjackPaused(CommandContext<CommandSourceStack> context, boolean paused) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (!LumberjackWorkerAI.setLumberjackPaused(context.getSource().getServer().overworld(), center, paused)) {
            context.getSource().sendFailure(Component.literal("Лесоруб не назначен. Используй /civitas worker lumberjack."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(paused ? "§eРабота лесоруба приостановлена. Груз сохранён." : "§aЛесоруб продолжил работу."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearLumberjack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (LumberjackWorkerAI.clearLumberjackRole(context.getSource().getServer().overworld(), center)) {
            context.getSource().sendSuccess(() -> Component.literal("§aПрофессия лесоруба снята."), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal("Лесоруб не найден или ещё несёт брёвна. Дождись доставки груза."));
        return 0;
    }

    private static int build(CommandContext<CommandSourceStack> context, BuildingDefinition.Type type) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
        if (warehouse == null) {
            context.getSource().sendFailure(Component.literal("Центральный физический склад не найден."));
            return 0;
        }
        SettlementBuildingManager.BuildResult result = SettlementBuildingManager.build(
                context.getSource().getServer().overworld(),
                data.getStarterVillageCenter(),
                warehouse,
                type,
                data
        );
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int buildAt(CommandContext<CommandSourceStack> context,
                                BuildingDefinition.Type type, int rotationDegrees) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        if (!context.getSource().getLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            context.getSource().sendFailure(Component.literal("Строительство Civitas доступно только в обычном мире."));
            return 0;
        }
        BlockPos site = BlockPosArgument.getLoadedBlockPos(context, "position");
        SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
        if (warehouse == null) {
            context.getSource().sendFailure(Component.literal("Центральный физический склад не найден."));
            return 0;
        }
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (!data.isWithinTerritory(site)) {
            context.getSource().sendFailure(Component.literal("Площадка находится за пределами территории поселения. Используй /civitas territory, чтобы узнать текущий радиус."));
            return 0;
        }
        SettlementBuildingManager.BuildResult result = SettlementBuildingManager.buildAt(
                context.getSource().getServer().overworld(), center, warehouse, type, data, site, rotationDegrees);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int preview(CommandContext<CommandSourceStack> context,
                               BuildingDefinition.Type type, int rotationDegrees) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        if (!context.getSource().getLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            context.getSource().sendFailure(Component.literal("Чертёж Civitas можно показывать только в обычном мире."));
            return 0;
        }
        BlockPos site = BlockPosArgument.getLoadedBlockPos(context, "position");
        BlockPos center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (!data.isWithinTerritory(site)) {
            context.getSource().sendFailure(Component.literal("Площадка находится за пределами территории поселения. Используй /civitas territory, чтобы узнать текущий радиус."));
            return 0;
        }
        SettlementBuildingManager.BuildResult result = SettlementBuildingManager.preview(
                context.getSource().getServer().overworld(), center, type, data, site, rotationDegrees);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal("§aЧертёж показан частицами. Зелёные точки — площадка свободна. "
                    + "Заказ не создан и ресурсы не списаны."), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal(result.message()
                + " Чертёж показан дымом там, где он не помещается. Заказ не создан."));
        return 0;
    }

    private static int construction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;

        var orders = data.getConstructionOrders();
        context.getSource().sendSuccess(() -> Component.literal("§6§lОчередь строительства: " + orders.size()), false);
        if (orders.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Активных строительных заказов нет."), false);
            return Command.SINGLE_SUCCESS;
        }

        ServerLevel level = context.getSource().getServer().overworld();
        for (int i = 0; i < orders.size(); i++) {
            var order = orders.get(i);
            String label = BuildingDefinition.find(order.type())
                    .map(BuildingDefinition::displayName)
                    .orElse(order.type());
            int percent = Math.min(100, order.progress() * 100 / order.totalLayers());
            String progressLine = (i + 1) + ". " + label + " — " + percent + "% (слой "
                    + Math.min(order.progress() + 1, order.totalLayers()) + "/" + order.totalLayers()
                    + ", поворот " + order.rotationDegrees() + "°)";
            context.getSource().sendSuccess(() -> Component.literal(progressLine), false);

            BlockPos site = new BlockPos(order.x(), order.y(), order.z());
            String workerState = "строитель не назначен";
            if (level.hasChunkAt(site)) {
                AABB area = new AABB(site).inflate(64, 24, 64);
                Villager builder = null;
                double nearest = Double.MAX_VALUE;
                for (Villager villager : level.getEntitiesOfClass(Villager.class, area)) {
                    if (!"builder".equals(villager.getPersistentData().getString("civitas.worker_role"))) continue;
                    double distance = villager.distanceToSqr(site.getX() + 0.5D, site.getY() + 1.0D, site.getZ() + 0.5D);
                    if (distance < nearest) {
                        nearest = distance;
                        builder = villager;
                    }
                }
                if (builder != null) {
                    workerState = nearest <= 25.0D ? "строитель на площадке" : "строитель направляется к площадке";
                } else {
                    workerState = "в радиусе стройки нет строителя";
                }
            } else {
                workerState = "площадка в незагруженном чанке";
            }
            if (order.materialsReservedInWarehouse()) {
                SettlementWarehouseBlockEntity warehouse = findWarehouse(context.getSource(), data);
                if (warehouse == null) {
                    workerState = "ожидание: центральный склад отсутствует";
                } else if (!data.warehouseContainsAllReservations(
                        SettlementBuildingManager.inventoryCounts(warehouse))) {
                    workerState = "ожидание: часть зарезервированных материалов отсутствует";
                } else {
                    workerState += " | материалы зарезервированы, списываются по слоям";
                }
            }
            String stateLine = "   Состояние: " + workerState + " | координаты: "
                    + order.x() + ", " + order.y() + ", " + order.z();
            context.getSource().sendSuccess(() -> Component.literal(stateLine), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelConstructionByIndex(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        int index = IntegerArgumentType.getInteger(context, "index");
        SettlementBuildingManager.BuildResult result = SettlementBuildingManager.cancelOrder(data, player, index);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int cancelConstruction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        SettlementBuildingManager.BuildResult result = SettlementBuildingManager.cancelFirstOrder(data, player);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int buildings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        var buildings = data.getBuildings();
        context.getSource().sendSuccess(() -> Component.literal("§6§lРеестр зданий: " + buildings.size()), false);
        if (buildings.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Пока нет зарегистрированных зданий. Открой панель Civitas и выбери постройку."), false);
            return Command.SINGLE_SUCCESS;
        }
        for (int i = 0; i < buildings.size(); i++) {
            var building = buildings.get(i);
            String label = BuildingDefinition.find(building.type())
                    .map(BuildingDefinition::displayName)
                    .orElse(building.type());
            String line = (i + 1) + ". " + label + " — " + building.x() + ", " + building.y() + ", " + building.z();
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
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
        Map<String, Integer> reserved = data.getWarehouseReservedResources();
        if (reserved.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Резерв строительства: нет."), false);
        } else {
            Map<String, Integer> stock = SettlementBuildingManager.inventoryCounts(warehouse);
            context.getSource().sendSuccess(() -> Component.literal("Резервы активных строительных заказов:"), false);
            for (Map.Entry<String, Integer> entry : reserved.entrySet()) {
                int stored = stock.getOrDefault(entry.getKey(), 0);
                int amountReserved = entry.getValue();
                int free = Math.max(0, stored - amountReserved);
                String line = "• " + entry.getKey() + ": всего " + stored
                        + ", зарезервировано " + amountReserved + ", свободно " + free;
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }


    private static int diplomacy(CommandContext<CommandSourceStack> context) {
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        var civilizations = new ArrayList<>(data.getNeighborCivilizations().values());
        civilizations.sort(Comparator.comparing(NeighborCivilization::name, String.CASE_INSENSITIVE_ORDER));
        if (civilizations.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Соседних цивилизаций пока нет. Дождись первого торгового каравана."), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendSuccess(() -> Component.literal("=== Соседние цивилизации ==="), false);
        for (int i = 0; i < civilizations.size(); i++) {
            NeighborCivilization c = civilizations.get(i);
            String line = (i + 1) + ". " + c.name() + " — " + c.stance().displayName()
                    + " | население " + c.population() + " | территория " + c.territory()
                    + " | еда " + c.food() + " | дерево " + c.timber() + " | камень " + c.stone()
                    + " | железо " + c.iron() + " | стабильность " + c.stability() + "%";
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Изменить отношения: /civitas diplomacy stance <номер> <neutral|allied|rival|war>"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setDiplomaticStance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CivitasSavedData data = CivitasSavedData.get(context.getSource().getServer());
        if (!ensureOwner(context.getSource(), player, data)) return 0;
        var civilizations = new ArrayList<>(data.getNeighborCivilizations().values());
        civilizations.sort(Comparator.comparing(NeighborCivilization::name, String.CASE_INSENSITIVE_ORDER));
        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        if (index < 0 || index >= civilizations.size()) {
            context.getSource().sendFailure(Component.literal("Цивилизация с таким номером не найдена. Выполни /civitas diplomacy."));
            return 0;
        }
        String raw = StringArgumentType.getString(context, "stance").toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("neutral", "allied", "ally", "rival", "war").contains(raw)) {
            context.getSource().sendFailure(Component.literal("Допустимые отношения: neutral, allied, rival, war."));
            return 0;
        }
        NeighborCivilization civilization = civilizations.get(index);
        NeighborCivilization.Stance stance = NeighborCivilization.Stance.parse(raw);
        data.setNeighborStance(civilization.id(), stance);
        data.recordTrade("Дипломатия: с «" + civilization.name() + "» установлено отношение «" + stance.displayName() + "»");
        context.getSource().sendSuccess(() -> Component.literal("Отношения с «" + civilization.name() + "»: " + stance.displayName() + "."), false);
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
        int stored = 0;
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) stored += stack.getCount();
        }
        int reserved = data.getWarehouseReservedResources().getOrDefault(id.toString(), 0);
        int available = Math.max(0, stored - reserved);
        if (available < amount) {
            context.getSource().sendFailure(Component.literal("Можно забрать только свободные ресурсы. На складе: "
                    + stored + ", зарезервировано под строительство: " + reserved
                    + ", доступно: " + available + " предмет(ов) " + id + "."));
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
