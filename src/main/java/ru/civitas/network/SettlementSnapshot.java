package ru.civitas.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;

/** Immutable server snapshot. Clients only render these authoritative values. */
public record SettlementSnapshot(
        boolean founded, String settlementName, String ownerName, int level, int population,
        int foodStock, int foodConsumptionPerDay, int foodDaysLeft, int hungerDays,
        int growthProgressDays, int growthIntervalDays, int bedCount,
        int residentCount, int lumberjacks, int builders, int farmers, int idleResidents,
        int cabinCount, int farmCount, int workshopCount, int orderCount,
        int warehouseItemCount, int warehouseOccupiedSlots, int warehouseSlots,
        int woodDelta7d, int stoneDelta7d, int foodDelta7d,
        String statusMessage, List<String> recentEvents, List<String> economy,
        List<String> residents, List<String> residentUuids, List<String> residentRoles,
        List<String> residentStatuses, List<String> buildings, List<String> construction, List<String> storage
) {
    private static final int MAX_RESIDENT_LINES=64, MAX_BUILDING_LINES=128, MAX_CONSTRUCTION_LINES=32,
            MAX_STORAGE_LINES=27, MAX_EVENT_LINES=32, MAX_ECONOMY_LINES=30, MAX_LINE_LENGTH=512;

    public SettlementSnapshot {
        settlementName=safe(settlementName); ownerName=safe(ownerName); statusMessage=safe(statusMessage);
        recentEvents=boundedLines(recentEvents,MAX_EVENT_LINES); economy=boundedLines(economy,MAX_ECONOMY_LINES);
        residents=boundedLines(residents,MAX_RESIDENT_LINES); residentUuids=boundedLines(residentUuids,MAX_RESIDENT_LINES);
        residentRoles=boundedLines(residentRoles,MAX_RESIDENT_LINES); residentStatuses=boundedLines(residentStatuses,MAX_RESIDENT_LINES);
        int aligned=Math.min(Math.min(residents.size(),residentUuids.size()),Math.min(residentRoles.size(),residentStatuses.size()));
        residents=List.copyOf(residents.subList(0,aligned)); residentUuids=List.copyOf(residentUuids.subList(0,aligned));
        residentRoles=List.copyOf(residentRoles.subList(0,aligned)); residentStatuses=List.copyOf(residentStatuses.subList(0,aligned));
        buildings=boundedLines(buildings,MAX_BUILDING_LINES); construction=boundedLines(construction,MAX_CONSTRUCTION_LINES);
        storage=boundedLines(storage,MAX_STORAGE_LINES);
    }

    public static SettlementSnapshot empty(String message) {
        return new SettlementSnapshot(false,"Нет поселения","",1,0,
                0,0,0,0,0,2,0,
                0,0,0,0,0,
                0,0,0,0,
                0,0,0,
                0,0,0,message,
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(founded); b.writeUtf(settlementName,40); b.writeUtf(ownerName,40);
        b.writeVarInt(level); b.writeVarInt(population); b.writeVarInt(foodStock); b.writeVarInt(foodConsumptionPerDay);
        b.writeVarInt(foodDaysLeft); b.writeVarInt(hungerDays); b.writeVarInt(growthProgressDays);
        b.writeVarInt(growthIntervalDays); b.writeVarInt(bedCount); b.writeVarInt(residentCount);
        b.writeVarInt(lumberjacks); b.writeVarInt(builders); b.writeVarInt(farmers); b.writeVarInt(idleResidents);
        b.writeVarInt(cabinCount); b.writeVarInt(farmCount); b.writeVarInt(workshopCount); b.writeVarInt(orderCount);
        b.writeVarInt(warehouseItemCount); b.writeVarInt(warehouseOccupiedSlots); b.writeVarInt(warehouseSlots);
        b.writeInt(woodDelta7d); b.writeInt(stoneDelta7d); b.writeInt(foodDelta7d); b.writeUtf(statusMessage,MAX_LINE_LENGTH);
        writeLines(b,recentEvents,MAX_EVENT_LINES); writeLines(b,economy,MAX_ECONOMY_LINES);
        writeLines(b,residents,MAX_RESIDENT_LINES); writeLines(b,residentUuids,MAX_RESIDENT_LINES);
        writeLines(b,residentRoles,MAX_RESIDENT_LINES); writeLines(b,residentStatuses,MAX_RESIDENT_LINES);
        writeLines(b,buildings,MAX_BUILDING_LINES); writeLines(b,construction,MAX_CONSTRUCTION_LINES); writeLines(b,storage,MAX_STORAGE_LINES);
    }

    public static SettlementSnapshot decode(FriendlyByteBuf b) {
        return new SettlementSnapshot(b.readBoolean(),b.readUtf(40),b.readUtf(40),
                readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),
                readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),
                readBoundedInt(b,30),readBoundedInt(b,30),readBoundedInt(b,1_000_000),
                readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),
                readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),
                readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),
                readBoundedInt(b,1_000_000),readBoundedInt(b,1_000_000),readBoundedInt(b,27),readBoundedInt(b,27),
                b.readInt(),b.readInt(),b.readInt(),b.readUtf(MAX_LINE_LENGTH),
                readLines(b,MAX_EVENT_LINES),readLines(b,MAX_ECONOMY_LINES),
                readLines(b,MAX_RESIDENT_LINES),readLines(b,MAX_RESIDENT_LINES),
                readLines(b,MAX_RESIDENT_LINES),readLines(b,MAX_RESIDENT_LINES),
                readLines(b,MAX_BUILDING_LINES),readLines(b,MAX_CONSTRUCTION_LINES),readLines(b,MAX_STORAGE_LINES));
    }

    private static int readBoundedInt(FriendlyByteBuf b,int max) {
        int value=b.readVarInt();
        if(value<0||value>max) throw new IllegalArgumentException("Invalid Civitas snapshot integer: "+value);
        return value;
    }
    private static void writeLines(FriendlyByteBuf b,List<String> lines,int max) {
        int size=Math.min(lines.size(),max); b.writeVarInt(size);
        for(int i=0;i<size;i++) b.writeUtf(safe(lines.get(i)),MAX_LINE_LENGTH);
    }
    private static List<String> readLines(FriendlyByteBuf b,int max) {
        int size=b.readVarInt();
        if(size<0||size>max) throw new IllegalArgumentException("Invalid Civitas snapshot list size: "+size);
        List<String> out=new ArrayList<>(size);
        for(int i=0;i<size;i++) out.add(b.readUtf(MAX_LINE_LENGTH));
        return List.copyOf(out);
    }
    private static List<String> boundedLines(List<String> lines,int max) {
        if(lines==null||lines.isEmpty()) return List.of();
        int size=Math.min(lines.size(),max); List<String> out=new ArrayList<>(size);
        for(int i=0;i<size;i++) out.add(safe(lines.get(i)));
        return List.copyOf(out);
    }
    private static String safe(String value) { return value==null?"":value; }
}
