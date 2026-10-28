package ru.civitas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;
import ru.civitas.CivitasMod;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.client.ClientNetworkHandler;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.world.BuildingDefinition;
import ru.civitas.worker.LumberjackWorkerAI;
import ru.civitas.worker.ConstructionWorkerAI;
import ru.civitas.worker.FarmerWorkerAI;
import ru.civitas.economy.SettlementEconomyAI;
import ru.civitas.CivitasConfig;
import ru.civitas.data.StorageFoodExtension;
import ru.civitas.trade.TradeCaravan;
import ru.civitas.trade.TradeOffer;
import ru.civitas.trade.TradePartner;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Server-authoritative networking for the Civitas dashboard.
 * Client packets contain typed actions, never arbitrary command strings.
 */
public final class CivitasNetwork {
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(CivitasMod.MOD_ID, "main"))
            .networkProtocolVersion(3)
            .simpleChannel();

    private static final Pattern ITEM_ID_PATTERN =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final int SNAPSHOT_REQUEST_COOLDOWN_TICKS = 10;
    private static final Map<UUID, Long> LAST_SNAPSHOT_REQUEST_TICK = new HashMap<>();

    private CivitasNetwork() {
    }

    public enum Action {
        FOUND,
        RENAME,
        BUILD,
        CANCEL_ORDER,
        ASSIGN_LUMBERJACK,
        ASSIGN_BUILDER,
        ASSIGN_LUMBERJACK_TO,
        ASSIGN_BUILDER_TO,
        ASSIGN_FARMER_TO,
        PAUSE_SELECTED,
        RESUME_SELECTED,
        RELEASE_SELECTED,
        PAUSE_LUMBERJACK,
        RESUME_LUMBERJACK,
        CLEAR_LUMBERJACK,
        DEPOSIT,
        WITHDRAW,
        BUILD_AT,
        PREVIEW_BUILD
    }

    public static void register() {
        int id = 1;

        CHANNEL.messageBuilder(RequestSnapshotPacket.class, id++)
                .direction(PacketFlow.SERVERBOUND)
                .encoder((packet, buffer) -> { })
                .decoder(buffer -> new RequestSnapshotPacket())
                .consumerMainThread(CivitasNetwork::handleSnapshotRequest)
                .add();

        CHANNEL.messageBuilder(ActionPacket.class, id++)
                .direction(PacketFlow.SERVERBOUND)
                .encoder(ActionPacket::encode)
                .decoder(ActionPacket::decode)
                .consumerMainThread(CivitasNetwork::handleAction)
                .add();

        CHANNEL.messageBuilder(SnapshotPacket.class, id++)
                .direction(PacketFlow.CLIENTBOUND)
                .encoder((packet, buffer) -> packet.snapshot().encode(buffer))
                .decoder(buffer -> new SnapshotPacket(SettlementSnapshot.decode(buffer)))
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> ClientNetworkHandler.handleSnapshot(packet.snapshot()));
                    context.setPacketHandled(true);
                })
                .add();


        CHANNEL.messageBuilder(TradeActionPacket.class, id++)
                .direction(PacketFlow.SERVERBOUND)
                .encoder(TradeActionPacket::encode)
                .decoder(TradeActionPacket::decode)
                .consumerMainThread(CivitasNetwork::handleTradeAction)
                .add();
        CHANNEL.messageBuilder(TradeScreenPacket.class, id++)
                .direction(PacketFlow.CLIENTBOUND)
                .encoder(TradeScreenPacket::encode)
                .decoder(TradeScreenPacket::decode)
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.civitas.client.TradeScreen.open(packet));
                    context.setPacketHandled(true);
                })
                .add();
        CHANNEL.build();
    }

    public static void requestSnapshot() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientNetworkHandler.sendPacket(new RequestSnapshotPacket()));
    }

    public static void sendAction(Action action, String value, int amount) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientNetworkHandler.sendPacket(
                        new ActionPacket(action, value == null ? "" : value, amount)));
    }

    private static void handleSnapshotRequest(RequestSnapshotPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || player.getServer() == null) {
            context.setPacketHandled(true);
            return;
        }

        // The GUI polls every 40 ticks. Throttle forged or accidental packet spam.
        long now = player.getServer().overworld().getGameTime();
        UUID playerId = player.getUUID();
        Long last = LAST_SNAPSHOT_REQUEST_TICK.get(playerId);
        if (last != null && now >= last && now - last < SNAPSHOT_REQUEST_COOLDOWN_TICKS) {
            context.setPacketHandled(true);
            return;
        }

        LAST_SNAPSHOT_REQUEST_TICK.put(playerId, now);
        if (LAST_SNAPSHOT_REQUEST_TICK.size() > 256) {
            LAST_SNAPSHOT_REQUEST_TICK.entrySet().removeIf(entry ->
                    now < entry.getValue() || now - entry.getValue() > 1200);
        }
        sendSnapshot(player);
        context.setPacketHandled(true);
    }

    private static void handleAction(ActionPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || player.getServer() == null) {
            context.setPacketHandled(true);
            return;
        }

        if (isResidentAction(packet.action())) {
            handleResidentAction(packet, player);
            context.setPacketHandled(true);
            return;
        }

        String command = commandFor(packet);
        if (command == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Civitas: действие не распознано или его параметры недопустимы."));
            context.setPacketHandled(true);
            return;
        }

        // Commands remain the authoritative validation layer for ownership,
        // warehouse stock, placement, worker availability and refunds.
        MinecraftServer server = player.getServer();
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(), command);
        sendSnapshot(player,
                "Данные поселения синхронизированы. Ответ команды — в чате.");
        context.setPacketHandled(true);
    }


    private static boolean isResidentAction(Action action) {
        return action == Action.ASSIGN_LUMBERJACK_TO
                || action == Action.ASSIGN_BUILDER_TO
                || action == Action.ASSIGN_FARMER_TO
                || action == Action.PAUSE_SELECTED
                || action == Action.RESUME_SELECTED
                || action == Action.RELEASE_SELECTED;
    }

    /**
     * Applies resident operations directly to the UUID selected by the client.
     * The client-provided UUID is only an identifier; every permission, entity,
     * dimension, and settlement-range check is repeated on the authoritative server.
     */
    private static void handleResidentAction(ActionPacket packet, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CivitasSavedData data = CivitasSavedData.get(server);
        if (!data.isFounded()) {
            residentActionFailure(player, "Сначала основай поселение.");
            return;
        }
        if (!data.isOwner(player) && !player.createCommandSourceStack().hasPermission(2)) {
            residentActionFailure(player, "Управлять жителями может только владелец поселения.");
            return;
        }
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            residentActionFailure(player, "Управление профессиями доступно только в обычном мире.");
            return;
        }

        UUID residentId;
        try {
            residentId = UUID.fromString(packet.value().trim());
        } catch (IllegalArgumentException exception) {
            residentActionFailure(player, "Выбери жителя в списке перед выполнением действия.");
            return;
        }

        ServerLevel level = server.overworld();
        var entity = level.getEntity(residentId);
        if (!(entity instanceof Villager villager) || villager.level() != level) {
            residentActionFailure(player, "Выбранный житель больше не найден. Обнови список жителей.");
            return;
        }

        var center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        if (!new AABB(center).inflate(24, 12, 24)
                .contains(villager.getX(), villager.getY(), villager.getZ())) {
            residentActionFailure(player, "Житель находится вне границ поселения. Выбери жителя поблизости.");
            return;
        }

        String role = LumberjackWorkerAI.getWorkerRole(villager);
        String name = villager.getCustomName() == null
                ? villager.getName().getString() : villager.getCustomName().getString();
        boolean succeeded;
        String message;
        switch (packet.action()) {
            case ASSIGN_LUMBERJACK_TO -> {
                int carried = villager.getPersistentData().getInt("civitas.worker_carried_count");
                String cargoError = ResidentAssignmentRules.cargoErrorForLumberjack(role, carried);
                if (cargoError != null) {
                    succeeded = false;
                    message = cargoError;
                } else if ("lumberjack".equals(role) && carried > 0) {
                    succeeded = true;
                    message = name + " уже лесоруб и доставит накопленные брёвна.";
                } else if ("farmer".equals(role) && FarmerWorkerAI.hasCargo(villager)) {
                    succeeded = false;
                    message = "Сначала дождись доставки урожая на склад.";
                } else {
                    succeeded = LumberjackWorkerAI.assignLumberjackTo(level, center, villager);
                    message = succeeded
                            ? "Житель «" + name + "» назначен лесорубом."
                            : "Не удалось назначить лесоруба: другой лесоруб может ещё доставлять груз.";
                }
            }
            case ASSIGN_FARMER_TO -> {
                if ("lumberjack".equals(role) && villager.getPersistentData().getInt("civitas.worker_carried_count") > 0) {
                    succeeded = false;
                    message = "Лесоруб несёт брёвна — дождись доставки на склад.";
                } else if (FarmerWorkerAI.hasCargo(villager) && !"farmer".equals(role)) {
                    succeeded = false;
                    message = "Фермер несёт урожай — дождись доставки на склад.";
                } else {
                    succeeded = FarmerWorkerAI.assignFarmerTo(level, center, villager);
                    message = succeeded
                            ? "Житель «" + name + "» назначен фермером."
                            : "Не удалось назначить фермера: житель занят другой задачей или вне поселения.";
                }
            }
            case ASSIGN_BUILDER_TO -> {
                int carried = villager.getPersistentData().getInt("civitas.worker_carried_count");
                String cargoError = ResidentAssignmentRules.cargoErrorForBuilder(carried);
                if (cargoError != null) {
                    succeeded = false;
                    message = cargoError;
                } else if (FarmerWorkerAI.hasCargo(villager)) {
                    succeeded = false;
                    message = "Фермер несёт урожай — дождись доставки на склад.";
                } else {
                    succeeded = ConstructionWorkerAI.assignBuilderTo(level, center, villager);
                    message = succeeded
                            ? "Житель «" + name + "» назначен строителем. Очередь строительства сохранена."
                            : "Не удалось назначить строителя: возможно, житель находится вне поселения.";
                }
            }
            case PAUSE_SELECTED -> {
                succeeded = LumberjackWorkerAI.setLumberjackPaused(villager, true);
                message = succeeded
                        ? "Работа лесоруба «" + name + "» приостановлена. Его груз сохранён."
                        : "Пауза доступна только выбранному жителю с профессией лесоруба.";
            }
            case RESUME_SELECTED -> {
                succeeded = LumberjackWorkerAI.setLumberjackPaused(villager, false);
                message = succeeded
                        ? "Лесоруб «" + name + "» продолжил работу."
                        : "Продолжить работу можно только у выбранного лесоруба.";
            }
            case RELEASE_SELECTED -> {
                if ("lumberjack".equals(role)) {
                    succeeded = LumberjackWorkerAI.releaseLumberjackRole(villager);
                    message = succeeded
                            ? "Профессия снята с жителя «" + name + "»."
                            : "Сначала дождись доставки всех брёвен на склад.";
                } else if ("farmer".equals(role)) {
                    succeeded = FarmerWorkerAI.releaseFarmerRole(villager);
                    message = succeeded
                            ? "Профессия фермера снята с жителя «" + name + "»."
                            : "Сначала дождись доставки урожая на склад.";
                } else if ("builder".equals(role)) {
                    succeeded = ConstructionWorkerAI.releaseBuilderRole(villager);
                    message = succeeded
                            ? "Профессия снята с жителя «" + name + "». Очередь строительства сохранена."
                            : "Не удалось снять профессию у выбранного жителя.";
                } else {
                    succeeded = false;
                    message = "У выбранного жителя нет профессии Civitas.";
                }
            }
            default -> {
                residentActionFailure(player, "Недопустимое действие для выбранного жителя.");
                return;
            }
        }

        if (!succeeded) {
            player.sendSystemMessage(Component.literal("Civitas: " + message));
        }
        sendSnapshot(player, message);
    }

    private static void residentActionFailure(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("Civitas: " + message));
        sendSnapshot(player, message);
    }

    private static String commandFor(ActionPacket packet) {
        Action action = packet.action();
        String value = packet.value() == null ? "" : packet.value().trim();

        return switch (action) {
            case FOUND -> {
                String name = sanitizeName(value);
                yield name.isEmpty() ? null : "civitas found " + name;
            }
            case RENAME -> {
                String name = sanitizeName(value);
                yield name.isEmpty() ? null : "civitas rename " + name;
            }
            case BUILD -> BuildingDefinition.Type.byId(value.toLowerCase(Locale.ROOT))
                    .map(type -> "civitas build " + type.id())
                    .orElse(null);
            case BUILD_AT -> manualBuildCommand(value, packet.amount(), false);
            case PREVIEW_BUILD -> manualBuildCommand(value, packet.amount(), true);
            case CANCEL_ORDER -> packet.amount() >= 1 && packet.amount() <= 999
                    ? "civitas construction cancel " + packet.amount()
                    : null;
            case ASSIGN_LUMBERJACK -> "civitas worker lumberjack";
            case ASSIGN_BUILDER -> "civitas worker builder";
            case ASSIGN_LUMBERJACK_TO, ASSIGN_BUILDER_TO, ASSIGN_FARMER_TO, PAUSE_SELECTED,
                    RESUME_SELECTED, RELEASE_SELECTED -> null;
            case PAUSE_LUMBERJACK -> "civitas worker pause";
            case RESUME_LUMBERJACK -> "civitas worker resume";
            case CLEAR_LUMBERJACK -> "civitas worker clear";
            case DEPOSIT -> packet.amount() >= 1 && packet.amount() <= 2304
                    ? "civitas deposit " + packet.amount()
                    : null;
            case WITHDRAW -> ITEM_ID_PATTERN.matcher(value).matches()
                    && packet.amount() >= 1 && packet.amount() <= 2304
                    ? "civitas withdraw " + value + " " + packet.amount()
                    : null;
        };
    }

    private static String manualBuildCommand(String encoded, int rotationDegrees, boolean preview) {
        if (encoded == null || encoded.length() > 128
                || rotationDegrees < 0 || rotationDegrees > 359) {
            return null;
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 4) return null;
        BuildingDefinition.Type type = BuildingDefinition.Type.byId(parts[0]).orElse(null);
        if (type == null) return null;
        for (int i = 1; i <= 3; i++) {
            // Only integer coordinates or vanilla relative coordinates are accepted.
            // This keeps the network action typed rather than allowing arbitrary commands.
            if (!parts[i].matches("(?:-?\\d+|~(?:-?\\d+)?)")) return null;
        }
        String command = preview ? "civitas preview " : "civitas build ";
        return command + type.id() + " " + parts[1] + " " + parts[2] + " " + parts[3]
                + " " + rotationDegrees;
    }

    private static String sanitizeName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40).trim();
        }
        return cleaned;
    }

    private static void sendSnapshot(ServerPlayer player) {
        sendSnapshot(player, "Снимок получен с сервера. Обновление выполняется автоматически.");
    }

    private static void sendSnapshot(ServerPlayer player, String statusMessage) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        CivitasSavedData data = CivitasSavedData.get(server);
        if (!data.isFounded()) {
            send(player, SettlementSnapshot.empty("Поселение ещё не создано."));
            return;
        }

        boolean canManage = data.isOwner(player) || player.createCommandSourceStack().hasPermission(2);
        if (!canManage) {
            send(player, SettlementSnapshot.empty("Доступ к данным поселения есть только у управляющего."));
            return;
        }

        ServerLevel level = server.overworld();
        var center = data.hasStarterVillage() ? data.getStarterVillageCenter() : data.getCenter();
        List<Villager> villageResidents = new ArrayList<>(level.getEntitiesOfClass(
                Villager.class, new AABB(center).inflate(24, 12, 24)));
        villageResidents.sort(java.util.Comparator
                .comparingDouble((Villager villager) -> villager.distanceToSqr(
                        center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D))
                .thenComparing(villager -> villager.getUUID().toString()));

        List<String> residentNames = new ArrayList<>();
        List<String> residentUuids = new ArrayList<>();
        List<String> residentRoles = new ArrayList<>();
        List<String> residentStatuses = new ArrayList<>();
        SettlementWarehouseBlockEntity foodWarehouse = level.hasChunkAt(center)
                && level.getBlockEntity(center) instanceof SettlementWarehouseBlockEntity foundWarehouse
                ? foundWarehouse : null;
        int foodStock = (int) Math.min(Integer.MAX_VALUE,
                (long) StorageFoodExtension.totalFoodValue(foodWarehouse) + data.getFoodCredit());
        int beds = SettlementEconomyAI.countBeds(level, center);
        int lumberjacks = 0;
        int builders = 0;
        int idle = 0;
        int farmers = 0;
        int residentCount = 0;

        for (Villager villager : villageResidents) {
            residentCount++;
            String role = LumberjackWorkerAI.getWorkerRole(villager);
            String roleLabel;
            if ("lumberjack".equals(role)) {
                lumberjacks++;
                roleLabel = "Лесоруб";
            } else if ("builder".equals(role)) {
                builders++;
                roleLabel = "Строитель";
            } else if ("farmer".equals(role)) {
                farmers++;
                roleLabel = "Фермер";
            } else {
                idle++;
                roleLabel = "Без назначения";
            }

            if (residentNames.size() < 64) {
                String name = villager.getCustomName() == null
                        ? villager.getName().getString()
                        : villager.getCustomName().getString();
                String residentStatus = "farmer".equals(role)
                        ? FarmerWorkerAI.getStatus(villager) : LumberjackWorkerAI.getWorkerStatus(villager);
                residentNames.add(name);
                residentUuids.add(villager.getUUID().toString());
                residentRoles.add(role);
                residentStatuses.add(residentStatus);
            }
        }

        List<String> buildingLines = new ArrayList<>();
        int cabins = 0;
        int farms = 0;
        int workshops = 0;
        for (CivitasSavedData.BuildingRecord building : data.getBuildings()) {
            BuildingDefinition definition = BuildingDefinition.find(building.type()).orElse(null);
            if (definition != null) {
                switch (definition.type()) {
                    case CABIN -> cabins++;
                    case FARM -> farms++;
                    case WORKSHOP -> workshops++;
                }
                if (buildingLines.size() < 128) {
                    buildingLines.add(definition.displayName() + " — "
                            + building.x() + ", " + building.y() + ", " + building.z());
                }
            } else if (buildingLines.size() < 128) {
                buildingLines.add(building.type() + " — "
                        + building.x() + ", " + building.y() + ", " + building.z());
            }
        }

        List<String> constructionLines = new ArrayList<>();
        var orders = data.getConstructionOrders();
        for (int i = 0; i < orders.size() && constructionLines.size() < 32; i++) {
            var order = orders.get(i);
            String name = BuildingDefinition.find(order.type())
                    .map(BuildingDefinition::displayName)
                    .orElse(order.type());
            int totalLayers = Math.max(1, order.totalLayers());
            int percent = Math.min(100, order.progress() * 100 / totalLayers);
            String blockage = "";
            if (order.materialsReservedInWarehouse()) {
                if (!order.hasValidLayerCosts()) {
                    blockage = " — ошибка сметы";
                } else if (!level.hasChunkAt(center)
                        || !(level.getBlockEntity(center) instanceof SettlementWarehouseBlockEntity warehouse)
                        || !data.warehouseContainsAllReservations(
                                ru.civitas.world.SettlementBuildingManager.inventoryCounts(warehouse))) {
                    blockage = " — ждёт снабжение";
                }
            }
            constructionLines.add((i + 1) + ". " + name + " — " + percent + "% (слой "
                    + Math.min(order.progress() + 1, totalLayers) + "/" + totalLayers + ")" + blockage);
        }

        List<String> storageLines = new ArrayList<>();
        int totalItems = 0;
        int occupiedSlots = 0;
        int warehouseSlots = 0;
        if (level.hasChunkAt(center)
                && level.getBlockEntity(center) instanceof SettlementWarehouseBlockEntity warehouse) {
            warehouseSlots = warehouse.getContainerSize();
            for (int slot = 0; slot < warehouseSlots; slot++) {
                ItemStack stack = warehouse.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                totalItems += stack.getCount();
                occupiedSlots++;
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                storageLines.add(stack.getHoverName().getString() + " (" + itemId + ") × " + stack.getCount());
            }
            if (storageLines.isEmpty()) {
                storageLines.add("Склад пока пуст.");
            }
            Map<String, Integer> reservedResources = data.getWarehouseReservedResources();
            if (!reservedResources.isEmpty()) {
                StringBuilder reservedLine = new StringBuilder("Резерв стройки: ");
                int shown = 0;
                for (Map.Entry<String, Integer> entry : reservedResources.entrySet()) {
                    if (shown == 4) break;
                    if (shown > 0) reservedLine.append(", ");
                    String shortId = entry.getKey().startsWith("minecraft:")
                            ? entry.getKey().substring("minecraft:".length())
                            : entry.getKey();
                    reservedLine.append(shortId).append(" × ").append(entry.getValue());
                    shown++;
                }
                if (reservedResources.size() > shown) reservedLine.append(", …");
                if (storageLines.size() >= 27) storageLines.remove(storageLines.size() - 1);
                storageLines.add(reservedLine.toString());
            }
        } else {
            storageLines.add("Физический склад сейчас недоступен.");
        }

        int foodNeed = (int) Math.min(Integer.MAX_VALUE,
                (long) residentCount * CivitasConfig.FOOD_PER_DAY.get());
        int foodDaysLeft = foodNeed <= 0 ? 999 : foodStock / foodNeed;
        List<String> eventLines = data.getEconomyHistory().recentEvents();
        List<String> economyLines = data.getEconomyHistory().formattedRecentDays(20);
        SettlementSnapshot snapshot = new SettlementSnapshot(
                true, data.getSettlementName(), data.getOwnerName(), data.getLevel(), residentCount,
                foodStock, foodNeed, foodDaysLeft, data.getHungerDays(), data.getGrowthProgressDays(),
                CivitasConfig.GROWTH_INTERVAL_DAYS.get(), beds, residentCount, lumberjacks, builders, farmers, idle,
                cabins, farms, workshops, orders.size(), totalItems, occupiedSlots, warehouseSlots,
                data.getEconomyHistory().deltaWood(7), data.getEconomyHistory().deltaStone(7),
                data.getEconomyHistory().deltaFood(7), statusMessage, eventLines, economyLines,
                residentNames, residentUuids, residentRoles, residentStatuses, buildingLines, constructionLines, storageLines);
        send(player, snapshot);
    }

    private static void send(ServerPlayer player, SettlementSnapshot snapshot) {
        CHANNEL.send(new SnapshotPacket(snapshot), PacketDistributor.PLAYER.with(player));
    }


    public static void openTradeScreen(ServerPlayer player, TradeCaravan caravan, CivitasSavedData data) {
        if (player == null || caravan == null || data == null || !caravan.canTrade()) return;
        TradePartner partner = data.getTradePartner(caravan.partnerId());
        if (partner == null) return;
        List<TradeScreenPacket.OfferLine> lines = new ArrayList<>();
        for (TradeOffer offer : caravan.offers()) {
            lines.add(new TradeScreenPacket.OfferLine(offer.want().getCount() + "× " + offer.want().getHoverName().getString(),
                    offer.offer().getCount() + "× " + offer.offer().getHoverName().getString(), offer.isAvailable()));
        }
        CHANNEL.send(new TradeScreenPacket(caravan.partnerId(), partner.name(), data.getTradeReputation(caravan.partnerId()),
                caravan.traderId(), lines, data.getTradeHistory(), Math.max(0, caravan.tradeLimit() - caravan.tradesUsed())),
                PacketDistributor.PLAYER.with(player));
    }

    private static void handleTradeAction(TradeActionPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null && player.getServer() != null) handleTradeAction(player, packet.traderId(), packet.offerIndex(), packet.gift());
        context.setPacketHandled(true);
    }

    private static void handleTradeAction(ServerPlayer player, UUID traderId, int offerIndex, boolean gift) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CivitasSavedData data = CivitasSavedData.get(server);
        if (!data.isFounded() || (!data.isOwner(player) && !player.createCommandSourceStack().hasPermission(2))) return;
        ServerLevel level = server.overworld();
        Entity entity = level.getEntity(traderId);
        if (!(entity instanceof Villager trader) || !trader.isAlive()
                || !trader.getPersistentData().getBoolean("civitas.trader") || player.distanceToSqr(trader) > 64.0D) return;
        TradeCaravan caravan = data.getActiveTradeCaravans().stream().filter(c -> c.traderId().equals(traderId)).findFirst().orElse(null);
        if (caravan == null || !caravan.canTrade()) return;
        BlockPos warehousePos = data.getStarterVillageCenter();
        if (!level.hasChunkAt(warehousePos) || !(level.getBlockEntity(warehousePos) instanceof SettlementWarehouseBlockEntity warehouse)) {
            player.sendSystemMessage(Component.translatable("civitas.trade.err.no_storage")); return;
        }
        ItemStack cost;
        ItemStack reward = ItemStack.EMPTY;
        TradeOffer offer = null;
        if (gift) {
            cost = new ItemStack(Items.IRON_INGOT, 1);
        } else {
            if (offerIndex < 0 || offerIndex >= caravan.offers().size()) return;
            offer = caravan.offers().get(offerIndex);
            if (!offer.isAvailable()) return;
            cost = offer.want();
            reward = offer.offer();
            if (!canInsertAll(warehouse, reward)) {
                player.sendSystemMessage(Component.literal("Civitas: на складе нет места для товара.")); return;
            }
        }
        Map<String, Integer> stock = ru.civitas.world.SettlementBuildingManager.inventoryCounts(warehouse);
        String itemId = BuiltInRegistries.ITEM.getKey(cost.getItem()).toString();
        int reserved = data.getWarehouseReservedResources().getOrDefault(itemId, 0);
        if (stock.getOrDefault(itemId, 0) - reserved < cost.getCount() || !hasExactItems(warehouse, cost)) {
            player.sendSystemMessage(Component.translatable("civitas.trade.err.not_enough", cost.getHoverName())); return;
        }
        int removed = extractExact(warehouse, cost);
        if (removed != cost.getCount()) {
            if (removed > 0) insertExact(warehouse, new ItemStack(cost.getItem(), removed));
            return;
        }
        if (!gift && !insertExact(warehouse, reward.copy())) {
            insertExact(warehouse, cost.copy());
            player.sendSystemMessage(Component.literal("Civitas: сделка отменена — склад изменился."));
            return;
        }
        TradePartner partner = data.getTradePartner(caravan.partnerId());
        if (gift) {
            int gain = CivitasConfig.GIFT_REPUTATION_GAIN.get();
            data.addTradeReputation(caravan.partnerId(), gain);
            data.recordTrade("Подарок партнёру «" + (partner == null ? "?" : partner.name()) + "»: +" + gain);
        } else {
            offer.markTraded();
            caravan.markTraded();
            int gain = CivitasConfig.TRADE_REPUTATION_GAIN.get();
            data.addTradeReputation(caravan.partnerId(), gain);
            data.recordTrade("Обмен: " + cost.getCount() + "× " + cost.getHoverName().getString()
                    + " → " + reward.getCount() + "× " + reward.getHoverName().getString());
        }
        warehouse.setChanged();
        data.markTradeDataDirty();
        openTradeScreen(player, caravan, data);
    }

    private static boolean hasExactItems(Container c, ItemStack target) {
        int total = 0;
        for (int i=0;i<c.getContainerSize();i++) if (ItemStack.isSameItemSameComponents(c.getItem(i), target)) total += c.getItem(i).getCount();
        return total >= target.getCount();
    }

    private static int extractExact(Container c, ItemStack target) {
        int left = target.getCount(), removed = 0;
        for (int i=0;i<c.getContainerSize() && left>0;i++) {
            ItemStack stack = c.getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, target)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) c.setItem(i, ItemStack.EMPTY);
            left -= take; removed += take;
        }
        return removed;
    }

    private static boolean canInsertAll(Container c, ItemStack target) {
        int left = target.getCount();
        for (int i=0;i<c.getContainerSize() && left>0;i++) {
            ItemStack stack = c.getItem(i);
            if (stack.isEmpty()) left -= Math.min(left, Math.min(c.getMaxStackSize(), target.getMaxStackSize()));
            else if (ItemStack.isSameItemSameComponents(stack, target)) {
                left -= Math.max(0, Math.min(stack.getMaxStackSize(), target.getMaxStackSize()) - stack.getCount());
            }
        }
        return left <= 0;
    }

    private static boolean insertExact(Container c, ItemStack input) {
        ItemStack stack = input.copy();
        for (int i=0;i<c.getContainerSize() && !stack.isEmpty();i++) {
            ItemStack existing = c.getItem(i);
            if (existing.isEmpty()) {
                int moved = Math.min(stack.getCount(), Math.min(c.getMaxStackSize(), stack.getMaxStackSize()));
                ItemStack placed = stack.copy(); placed.setCount(moved); c.setItem(i, placed); stack.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(stack.getCount(), Math.max(0, Math.min(existing.getMaxStackSize(), stack.getMaxStackSize()) - existing.getCount()));
                if (moved > 0) { existing.grow(moved); stack.shrink(moved); }
            }
        }
        return stack.isEmpty();
    }

    public record TradeActionPacket(UUID traderId, int offerIndex, boolean gift) {
        public void encode(FriendlyByteBuf b) { b.writeUUID(traderId); b.writeVarInt(offerIndex); b.writeBoolean(gift); }
        public static TradeActionPacket decode(FriendlyByteBuf b) {
            UUID id = b.readUUID(); int index = b.readVarInt(); boolean gift = b.readBoolean();
            if (index < -1 || index > 15) throw new IllegalArgumentException("Invalid trade offer index");
            return new TradeActionPacket(id, index, gift);
        }
    }

    public record TradeScreenPacket(UUID partnerId, String partnerName, int reputation, UUID traderId,
                                    List<OfferLine> offers, List<String> history, int tradesLeft) {
        public record OfferLine(String want, String offer, boolean available) { }
        public TradeScreenPacket {
            partnerName = partnerName == null ? "Unknown" : partnerName.substring(0, Math.min(64, partnerName.length()));
            reputation = Math.max(0, Math.min(100, reputation));
            offers = List.copyOf(offers == null ? List.of() : offers.subList(0, Math.min(8, offers.size())));
            List<String> safeHistory = history == null ? List.of() : history;
            history = List.copyOf(safeHistory.subList(Math.max(0, safeHistory.size() - 20), safeHistory.size()));
            tradesLeft = Math.max(0, Math.min(20, tradesLeft));
        }
        public void encode(FriendlyByteBuf b) {
            b.writeUUID(partnerId); b.writeUtf(partnerName,64); b.writeVarInt(reputation); b.writeUUID(traderId);
            b.writeVarInt(offers.size());
            for (OfferLine o:offers) { b.writeUtf(o.want(),128); b.writeUtf(o.offer(),128); b.writeBoolean(o.available()); }
            b.writeVarInt(history.size()); for (String line:history) b.writeUtf(line,256);
            b.writeVarInt(tradesLeft);
        }
        public static TradeScreenPacket decode(FriendlyByteBuf b) {
            UUID p=b.readUUID(); String name=b.readUtf(64); int rep=b.readVarInt(); UUID t=b.readUUID();
            int count=b.readVarInt(); if(count<0||count>8) throw new IllegalArgumentException("Invalid trade offer count");
            List<OfferLine> offers=new ArrayList<>();
            for(int i=0;i<count;i++) offers.add(new OfferLine(b.readUtf(128),b.readUtf(128),b.readBoolean()));
            int h=b.readVarInt(); if(h<0||h>20) throw new IllegalArgumentException("Invalid trade history count");
            List<String> history=new ArrayList<>(); for(int i=0;i<h;i++) history.add(b.readUtf(256));
            return new TradeScreenPacket(p,name,rep,t,offers,history,b.readVarInt());
        }
    }

    public record RequestSnapshotPacket() {
    }

    public record ActionPacket(Action action, String value, int amount) {
        public ActionPacket {
            if (action == null) {
                throw new IllegalArgumentException("Action is required");
            }
            value = value == null ? "" : value;
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(action.ordinal());
            buffer.writeUtf(value, 128);
            buffer.writeVarInt(amount);
        }

        public static ActionPacket decode(FriendlyByteBuf buffer) {
            int ordinal = buffer.readVarInt();
            if (ordinal < 0 || ordinal >= Action.values().length) {
                throw new IllegalArgumentException("Unknown Civitas action: " + ordinal);
            }
            return new ActionPacket(Action.values()[ordinal], buffer.readUtf(128), buffer.readVarInt());
        }
    }

    public record SnapshotPacket(SettlementSnapshot snapshot) {
    }
}
