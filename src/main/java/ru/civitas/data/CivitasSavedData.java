package ru.civitas.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Persistent world-level data for the first settlement prototype. */
public final class CivitasSavedData extends SavedData {
    private static final String DATA_NAME = "civitas_settlement";
    private static final SavedData.Factory<CivitasSavedData> FACTORY = new SavedData.Factory<>(
            CivitasSavedData::new,
            CivitasSavedData::load,
            DataFixTypes.LEVEL
    );

    private boolean founded;
    private String settlementName = "";
    private String ownerUuid = "";
    private String ownerName = "";
    private int centerX;
    private int centerY;
    private int centerZ;
    private int population = 5;
    private int level = 1;
    private boolean starterVillageGenerated;
    private int villageX;
    private int villageY;
    private int villageZ;
    private final Map<String, Integer> resources = new TreeMap<>();
    private final List<BuildingRecord> buildings = new ArrayList<>();
    private final List<ConstructionOrder> constructionOrders = new ArrayList<>();

    public record BuildingRecord(String type, int x, int y, int z) { }

    /**
     * Immutable reservation ledger captured when a construction order is queued.
     * Progress allocates the reserved materials to completed layers; the rest
     * remains refundable if the player cancels the order.
     */
    public record ConstructionOrder(
            String type,
            int x,
            int y,
            int z,
            int progress,
            int totalLayers,
            Map<String, Integer> reservedResources
    ) {
        public ConstructionOrder(String type, int x, int y, int z, int progress, int totalLayers) {
            this(type, x, y, z, progress, totalLayers, Map.of());
        }

        public ConstructionOrder {
            reservedResources = reservedResources == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(reservedResources));
        }

        /**
         * Returns only the reserved amount allocated to layers that have not
         * been completed. Integer division means indivisible resources (such
         * as one water bucket) are allocated to the final layer.
         */
        public Map<String, Integer> remainingReservedResources() {
            if (reservedResources.isEmpty()) {
                return Map.of();
            }

            int safeTotalLayers = Math.max(1, totalLayers);
            int completedLayers = Math.max(0, Math.min(progress, safeTotalLayers));
            Map<String, Integer> remaining = new TreeMap<>();
            for (Map.Entry<String, Integer> entry : reservedResources.entrySet()) {
                int originalAmount = Math.max(0, entry.getValue());
                long consumed = ((long) originalAmount * completedLayers) / safeTotalLayers;
                int refundable = originalAmount - (int) consumed;
                if (refundable > 0) {
                    remaining.put(entry.getKey(), refundable);
                }
            }
            return Collections.unmodifiableMap(remaining);
        }
    }

    public CivitasSavedData() {
    }

    public static CivitasSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static CivitasSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CivitasSavedData data = new CivitasSavedData();
        data.founded = tag.getBoolean("founded");
        data.settlementName = tag.getString("settlement_name");
        data.ownerUuid = tag.getString("owner_uuid");
        data.ownerName = tag.getString("owner_name");
        data.centerX = tag.getInt("center_x");
        data.centerY = tag.getInt("center_y");
        data.centerZ = tag.getInt("center_z");
        data.population = Math.max(0, tag.getInt("population"));
        data.level = Math.max(1, tag.getInt("level"));
        data.starterVillageGenerated = tag.getBoolean("starter_village_generated");
        data.villageX = tag.getInt("village_x");
        data.villageY = tag.getInt("village_y");
        data.villageZ = tag.getInt("village_z");

        ListTag storedOrders = tag.getList("construction_orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < storedOrders.size(); i++) {
            CompoundTag order = storedOrders.getCompound(i);
            String type = order.getString("type");
            int total = Math.max(1, order.getInt("total_layers"));
            int progress = Math.max(0, Math.min(total, order.getInt("progress")));
            Map<String, Integer> reservedResources = new TreeMap<>();
            CompoundTag storedReservation = order.getCompound("reserved_resources");
            for (String itemId : storedReservation.getAllKeys()) {
                int count = storedReservation.getInt(itemId);
                if (count > 0) {
                    reservedResources.put(itemId, count);
                }
            }
            if (!type.isBlank() && progress < total) {
                data.constructionOrders.add(new ConstructionOrder(
                        type, order.getInt("x"), order.getInt("y"), order.getInt("z"),
                        progress, total, reservedResources));
            }
        }
        ListTag storedBuildings = tag.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < storedBuildings.size(); i++) {
            CompoundTag building = storedBuildings.getCompound(i);
            String type = building.getString("type");
            if (!type.isBlank()) data.buildings.add(new BuildingRecord(type, building.getInt("x"), building.getInt("y"), building.getInt("z")));
        }
        CompoundTag storedResources = tag.getCompound("resources");
        for (String itemId : storedResources.getAllKeys()) {
            int count = storedResources.getInt(itemId);
            if (count > 0) {
                data.resources.put(itemId, count);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("founded", founded);
        tag.putString("settlement_name", settlementName);
        tag.putString("owner_uuid", ownerUuid);
        tag.putString("owner_name", ownerName);
        tag.putInt("center_x", centerX);
        tag.putInt("center_y", centerY);
        tag.putInt("center_z", centerZ);
        tag.putInt("population", population);
        tag.putInt("level", level);
        tag.putBoolean("starter_village_generated", starterVillageGenerated);
        tag.putInt("village_x", villageX);
        tag.putInt("village_y", villageY);
        tag.putInt("village_z", villageZ);

        ListTag storedOrders = new ListTag();
        for (ConstructionOrder order : constructionOrders) {
            CompoundTag stored = new CompoundTag();
            stored.putString("type", order.type());
            stored.putInt("x", order.x()); stored.putInt("y", order.y()); stored.putInt("z", order.z());
            stored.putInt("progress", order.progress()); stored.putInt("total_layers", order.totalLayers());
            CompoundTag reservation = new CompoundTag();
            order.reservedResources().forEach(reservation::putInt);
            stored.put("reserved_resources", reservation);
            storedOrders.add(stored);
        }
        tag.put("construction_orders", storedOrders);
        ListTag storedBuildings = new ListTag();
        for (BuildingRecord record : buildings) {
            CompoundTag building = new CompoundTag();
            building.putString("type", record.type());
            building.putInt("x", record.x());
            building.putInt("y", record.y());
            building.putInt("z", record.z());
            storedBuildings.add(building);
        }
        tag.put("buildings", storedBuildings);
        CompoundTag storedResources = new CompoundTag();
        resources.forEach(storedResources::putInt);
        tag.put("resources", storedResources);
        return tag;
    }

    public boolean found(String name, ServerPlayer owner, BlockPos center) {
        if (founded) {
            return false;
        }
        String cleanedName = name == null ? "" : name.trim();
        if (cleanedName.isEmpty()) {
            cleanedName = "Новая деревня";
        }
        if (cleanedName.length() > 40) {
            cleanedName = cleanedName.substring(0, 40);
        }
        founded = true;
        settlementName = cleanedName;
        ownerUuid = owner.getUUID().toString();
        ownerName = owner.getName().getString();
        centerX = center.getX();
        centerY = center.getY();
        centerZ = center.getZ();
        population = 5;
        level = 1;
        setDirty();
        return true;
    }

    public boolean rename(String name) {
        String cleanedName = name == null ? "" : name.trim();
        if (!founded || cleanedName.isEmpty()) {
            return false;
        }
        if (cleanedName.length() > 40) {
            cleanedName = cleanedName.substring(0, 40);
        }
        settlementName = cleanedName;
        setDirty();
        return true;
    }

    public boolean isOwner(ServerPlayer player) {
        return player.getUUID().toString().equals(ownerUuid);
    }

    public boolean addResource(String itemId, int amount) {
        if (!founded || itemId == null || itemId.isBlank() || amount <= 0) {
            return false;
        }
        int current = resources.getOrDefault(itemId, 0);
        if (current > Integer.MAX_VALUE - amount) {
            return false;
        }
        resources.put(itemId, current + amount);
        setDirty();
        return true;
    }

    public boolean removeResource(String itemId, int amount) {
        if (!founded || itemId == null || amount <= 0 || resources.getOrDefault(itemId, 0) < amount) {
            return false;
        }
        int remaining = resources.get(itemId) - amount;
        if (remaining == 0) {
            resources.remove(itemId);
        } else {
            resources.put(itemId, remaining);
        }
        setDirty();
        return true;
    }

    public int getResourceCount(String itemId) {
        return resources.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> getResources() {
        return Collections.unmodifiableMap(new TreeMap<>(resources));
    }

    public void addConstructionOrder(String type, BlockPos position, int totalLayers) {
        addConstructionOrder(type, position, totalLayers, Map.of());
    }

    public void addConstructionOrder(
            String type, BlockPos position, int totalLayers, Map<String, Integer> reservedResources) {
        if (type == null || type.isBlank() || position == null || totalLayers < 1) return;
        Map<String, Integer> reservation = new TreeMap<>();
        if (reservedResources != null) {
            reservedResources.forEach((itemId, amount) -> {
                if (itemId != null && !itemId.isBlank() && amount != null && amount > 0) {
                    reservation.put(itemId, amount);
                }
            });
        }
        constructionOrders.add(new ConstructionOrder(
                type, position.getX(), position.getY(), position.getZ(), 0, totalLayers, reservation));
        setDirty();
    }
    public List<ConstructionOrder> getConstructionOrders() { return Collections.unmodifiableList(new ArrayList<>(constructionOrders)); }
    public boolean removeConstructionOrder(ConstructionOrder order) {
        if (order == null || !constructionOrders.remove(order)) return false;
        setDirty();
        return true;
    }

    public void advanceConstructionOrder(ConstructionOrder order) {
        int index = constructionOrders.indexOf(order);
        if (index < 0) return;
        if (order.progress() + 1 >= order.totalLayers()) {
            constructionOrders.remove(index);
        } else {
            constructionOrders.set(index, new ConstructionOrder(
                    order.type(), order.x(), order.y(), order.z(),
                    order.progress() + 1, order.totalLayers(), order.reservedResources()));
        }
        setDirty();
    }
    public void registerBuilding(String type, BlockPos position) {
        if (type == null || type.isBlank() || position == null) return;
        buildings.add(new BuildingRecord(type, position.getX(), position.getY(), position.getZ()));
        setDirty();
    }
    public List<BuildingRecord> getBuildings() {
        return Collections.unmodifiableList(new ArrayList<>(buildings));
    }
    public boolean hasBuildingNear(BlockPos position, int radius) {
        for (BuildingRecord building : buildings) {
            if (Math.abs(building.x() - position.getX()) <= radius && Math.abs(building.z() - position.getZ()) <= radius) return true;
        }
        return false;
    }
    public boolean isFounded() {
        return founded;
    }

    public String getSettlementName() {
        return settlementName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BlockPos getCenter() {
        return new BlockPos(centerX, centerY, centerZ);
    }

    public int getPopulation() {
        return population;
    }

    public int getLevel() {
        return level;
    }

    public boolean hasStarterVillage() {
        return starterVillageGenerated;
    }

    public BlockPos getStarterVillageCenter() {
        return new BlockPos(villageX, villageY, villageZ);
    }

    public void setStarterVillageCenter(BlockPos center) {
        villageX = center.getX();
        villageY = center.getY();
        villageZ = center.getZ();
        starterVillageGenerated = true;
        setDirty();
    }
}
