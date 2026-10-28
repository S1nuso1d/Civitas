package ru.civitas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
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
            .networkProtocolVersion(1)
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
        PAUSE_LUMBERJACK,
        RESUME_LUMBERJACK,
        CLEAR_LUMBERJACK,
        DEPOSIT,
        WITHDRAW
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
            case CANCEL_ORDER -> packet.amount() >= 1 && packet.amount() <= 999
                    ? "civitas construction cancel " + packet.amount()
                    : null;
            case ASSIGN_LUMBERJACK -> "civitas worker lumberjack";
            case ASSIGN_BUILDER -> "civitas worker builder";
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
        List<String> residentLines = new ArrayList<>();
        int lumberjacks = 0;
        int builders = 0;
        int idle = 0;
        int residentCount = 0;

        for (Villager villager : level.getEntitiesOfClass(
                Villager.class, new AABB(center).inflate(24, 12, 24))) {
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

            if (residentLines.size() < 64) {
                String name = villager.getCustomName() == null
                        ? villager.getName().getString()
                        : villager.getCustomName().getString();
                residentLines.add(name + " — " + roleLabel + " | "
                        + LumberjackWorkerAI.getWorkerStatus(villager));
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
            constructionLines.add((i + 1) + ". " + name + " — " + percent + "% (слой "
                    + Math.min(order.progress() + 1, totalLayers) + "/" + totalLayers + ")");
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
                residentLines,
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
