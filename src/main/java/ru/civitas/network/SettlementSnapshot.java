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
        int foodStock,
        int foodConsumptionPerDay,
        int hungerDays,
        int growthProgressDays,
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
        List<String> residentUuids,
        List<String> residentRoles,
        List<String> residentStatuses,
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
        residents = boundedLines(residents, MAX_RESIDENT_LINES);
        residentUuids = boundedLines(residentUuids, MAX_RESIDENT_LINES);
        residentRoles = boundedLines(residentRoles, MAX_RESIDENT_LINES);
        residentStatuses = boundedLines(residentStatuses, MAX_RESIDENT_LINES);
        int alignedResidents = Math.min(Math.min(residents.size(), residentUuids.size()),
                Math.min(residentRoles.size(), residentStatuses.size()));
        residents = List.copyOf(residents.subList(0, alignedResidents));
        residentUuids = List.copyOf(residentUuids.subList(0, alignedResidents));
        residentRoles = List.copyOf(residentRoles.subList(0, alignedResidents));
        residentStatuses = List.copyOf(residentStatuses.subList(0, alignedResidents));
        buildings = List.copyOf(buildings);
        construction = List.copyOf(construction);
        storage = List.copyOf(storage);
    }

    public static SettlementSnapshot empty(String message) {
        return new SettlementSnapshot(
                false, "Нет поселения", "", 1, 0,
                0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, message,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(founded);
        buffer.writeUtf(settlementName, 40);
        buffer.writeUtf(ownerName, 40);
        buffer.writeVarInt(level);
        buffer.writeVarInt(population);
        buffer.writeVarInt(foodStock);
        buffer.writeVarInt(foodConsumptionPerDay);
        buffer.writeVarInt(hungerDays);
        buffer.writeVarInt(growthProgressDays);
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
        writeLines(buffer, residentUuids, MAX_RESIDENT_LINES);
        writeLines(buffer, residentRoles, MAX_RESIDENT_LINES);
        writeLines(buffer, residentStatuses, MAX_RESIDENT_LINES);
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
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 1_000_000),
                readBoundedInt(buffer, 27),
                readBoundedInt(buffer, 27),
                buffer.readUtf(MAX_LINE_LENGTH),
                readLines(buffer, MAX_RESIDENT_LINES),
                readLines(buffer, MAX_RESIDENT_LINES),
                readLines(buffer, MAX_RESIDENT_LINES),
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

    private static List<String> boundedLines(List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) return List.of();
        int size = Math.min(lines.size(), max);
        List<String> copy = new ArrayList<>(size);
        for (int i = 0; i < size; i++) copy.add(safe(lines.get(i)));
        return List.copyOf(copy);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
