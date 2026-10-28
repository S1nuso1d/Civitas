package ru.civitas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            .networkProtocolVersion(2)
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
                if ("lumberjack".equals(role)
                        && villager.getPersistentData().getInt("civitas.worker_carried_count") > 0) {
                    succeeded = true;
                    message = name + " уже лесоруб и доставит накопленные брёвна.";
                } else {
                    succeeded = LumberjackWorkerAI.assignLumberjackTo(level, center, villager);
                    message = succeeded
                            ? "Житель «" + name + "» назначен лесорубом."
                            : "Не удалось назначить лесоруба: проверь груз текущего лесоруба и выбранного жителя.";
                }
            }
            case ASSIGN_BUILDER_TO -> {
                succeeded = ConstructionWorkerAI.assignBuilderTo(level, center, villager);
                message = succeeded
                        ? "Житель «" + name + "» назначен строителем. Очередь строительства сохранена."
                        : "Нельзя назначить строителя: возможно, житель ещё несёт брёвна или находится вне поселения.";
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
            case ASSIGN_LUMBERJACK_TO, ASSIGN_BUILDER_TO, PAUSE_SELECTED,
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
        int lumberjacks = 0;
        int builders = 0;
        int idle = 0;
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
            } else {
                idle++;
                roleLabel = "Без назначения";
            }

            if (residentNames.size() < 64) {
                String name = villager.getCustomName() == null
                        ? villager.getName().getString()
                        : villager.getCustomName().getString();
                String residentStatus = LumberjackWorkerAI.getWorkerStatus(villager);
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

        SettlementSnapshot snapshot = new SettlementSnapshot(
                true,
                data.getSettlementName(),
                data.getOwnerName(),
                data.getLevel(),
                data.getPopulation(),
                residentCount,
                lumberjacks,
                builders,
                idle,
                cabins,
                farms,
                workshops,
                orders.size(),
                totalItems,
                occupiedSlots,
                warehouseSlots,
                statusMessage,
                residentNames,
                residentUuids,
                residentRoles,
                residentStatuses,
                buildingLines,
                constructionLines,
                storageLines
        );
        send(player, snapshot);
    }

    private static void send(ServerPlayer player, SettlementSnapshot snapshot) {
        CHANNEL.send(new SnapshotPacket(snapshot), PacketDistributor.PLAYER.with(player));
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
