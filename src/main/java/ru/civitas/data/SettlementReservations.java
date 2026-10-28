package ru.civitas.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent settlement-wide reservation ledger for farm crop blocks. */
public final class SettlementReservations {
    private final Map<BlockPos, UUID> farmland = new HashMap<>();

    public boolean isReserved(BlockPos pos) { return pos != null && farmland.containsKey(pos); }
    public boolean isReservedBy(BlockPos pos, UUID workerId) {
        return pos != null && workerId != null && workerId.equals(farmland.get(pos));
    }
    public boolean isReservedByOther(BlockPos pos, UUID workerId) {
        UUID owner = pos == null ? null : farmland.get(pos);
        return owner != null && !owner.equals(workerId);
    }
    public boolean tryReserve(BlockPos pos, UUID workerId) {
        if (pos == null || workerId == null || isReservedByOther(pos, workerId)) return false;
        farmland.put(pos.immutable(), workerId);
        return true;
    }
    public boolean release(BlockPos pos, UUID workerId) {
        if (pos == null || workerId == null || !workerId.equals(farmland.get(pos))) return false;
        farmland.remove(pos);
        return true;
    }
    public boolean releaseAll(UUID workerId) {
        if (workerId == null) return false;
        return farmland.entrySet().removeIf(entry -> workerId.equals(entry.getValue()));
    }
    public Map<BlockPos, UUID> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(farmland));
    }
    public void save(CompoundTag parent, String key) {
        ListTag list = new ListTag();
        for (var entry : farmland.entrySet()) {
            CompoundTag encoded = new CompoundTag();
            encoded.putLong("pos", entry.getKey().asLong());
            encoded.putUUID("worker", entry.getValue());
            list.add(encoded);
        }
        parent.put(key, list);
    }
    public static SettlementReservations load(CompoundTag parent, String key) {
        SettlementReservations reservations = new SettlementReservations();
        if (parent == null || !parent.contains(key, Tag.TAG_LIST)) return reservations;
        ListTag list = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag encoded = list.getCompound(i);
            if (encoded.hasUUID("worker")) {
                reservations.farmland.put(BlockPos.of(encoded.getLong("pos")), encoded.getUUID("worker"));
            }
        }
        return reservations;
    }
    public void restoreFrom(SettlementReservations other) {
        farmland.clear();
        if (other != null) farmland.putAll(other.farmland);
    }
}
