package ru.civitas.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only server snapshot displayed by the settlement dashboard.
 * The client never invents or mutates authoritative settlement state.
 */
public record SettlementSnapshot(
        boolean founded,
        String settlementName,
        String ownerName,
        int level,
        int population,
        int residentCount,
        int lumberjacks,
        int builders,
        int idleResidents,
        int cabinCount,
        int farmCount,
        int workshopCount,
        int orderCount,
        int warehouseItemCount,
        int warehouseOccupiedSlots,
        int warehouseSlots,
        String statusMessage,
        List<String> residents,
        List<String> buildings,
        List<String> construction,
        List<String> storage
) {
    private static final int MAX_RESIDENT_LINES = 64;
    private static final int MAX_BUILDING_LINES = 128;
    private static final int MAX_CONSTRUCTION_LINES = 32;
    private static final int MAX_STORAGE_LINES = 27;
    private static final int MAX_LINE_LENGTH = 512;

    public SettlementSnapshot {
        settlementName = safe(settlementName);
        ownerName = safe(ownerName);
        statusMessage = safe(statusMessage);
        residents = List.copyOf(residents);
        buildings = List.copyOf(buildings);
        construction = List.copyOf(construction);
        storage = List.copyOf(storage);
    }

    public static SettlementSnapshot empty(String message) {
        return new SettlementSnapshot(
                false, "Нет поселения", "", 1, 0,
                0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, message,
                List.of(), List.of(), List.of(), List.of()
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(founded);
        buffer.writeUtf(settlementName, 40);
        buffer.writeUtf(ownerName, 40);
        buffer.writeVarInt(level);
        buffer.writeVarInt(population);
        buffer.writeVarInt(residentCount);
        buffer.writeVarInt(lumberjacks);
        buffer.writeVarInt(builders);
        buffer.writeVarInt(idleResidents);
        buffer.writeVarInt(cabinCount);
        buffer.writeVarInt(farmCount);
        buffer.writeVarInt(workshopCount);
        buffer.writeVarInt(orderCount);
        buffer.writeVarInt(warehouseItemCount);
        buffer.writeVarInt(warehouseOccupiedSlots);
        buffer.writeVarInt(warehouseSlots);
        buffer.writeUtf(statusMessage, MAX_LINE_LENGTH);
        writeLines(buffer, residents, MAX_RESIDENT_LINES);
        writeLines(buffer, buildings, MAX_BUILDING_LINES);
        writeLines(buffer, construction, MAX_CONSTRUCTION_LINES);
        writeLines(buffer, storage, MAX_STORAGE_LINES);
    }

    public static SettlementSnapshot decode(FriendlyByteBuf buffer) {
        return new SettlementSnapshot(
                buffer.readBoolean(),
                buffer.readUtf(40),
                buffer.readUtf(40),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 27),
                readBoundedInt(buffer, 27),
                buffer.readUtf(MAX_LINE_LENGTH),
                readLines(buffer, MAX_RESIDENT_LINES),
                readLines(buffer, MAX_BUILDING_LINES),
                readLines(buffer, MAX_CONSTRUCTION_LINES),
                readLines(buffer, MAX_STORAGE_LINES)
        );
    }

    private static int readBoundedInt(FriendlyByteBuf buffer, int max) {
        int value = buffer.readVarInt();
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Invalid Civitas snapshot integer: " + value);
        }
        return value;
    }

    private static void writeLines(FriendlyByteBuf buffer, List<String> lines, int max) {
        int size = Math.min(lines.size(), max);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buffer.writeUtf(safe(lines.get(i)), MAX_LINE_LENGTH);
        }
    }

    private static List<String> readLines(FriendlyByteBuf buffer, int max) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid Civitas snapshot list size: " + size);
        }
        List<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(buffer.readUtf(MAX_LINE_LENGTH));
        }
        return List.copyOf(lines);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
