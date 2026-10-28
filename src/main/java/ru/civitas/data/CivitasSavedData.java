package ru.civitas.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.datafix.DataFixTypes;
import ru.civitas.CivitasConfig;
import ru.civitas.trade.TradePartner;
import ru.civitas.trade.TradeCaravan;
import ru.civitas.trade.NeighborCivilization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
    private long lastEconomyDay = -1L;
    private int hungerDays;
    private int growthProgressDays;
    private int foodCredit;
    private final EconomyHistory economyHistory = new EconomyHistory(30);
    private final SettlementReservations farmlandReservations = new SettlementReservations();
    private int level = 1;
    private boolean starterVillageGenerated;
    private int villageX;
    private int villageY;
    private int villageZ;
    private final Map<String, Integer> resources = new TreeMap<>();
    private final List<BuildingRecord> buildings = new ArrayList<>();
    private final List<ConstructionOrder> constructionOrders = new ArrayList<>();
    private final Map<UUID, TradePartner> tradePartners = new HashMap<>();
    private final Map<UUID, NeighborCivilization> neighborCivilizations = new HashMap<>();
    private final Map<UUID, Integer> tradeReputation = new HashMap<>();
    private final List<TradeCaravan> activeTradeCaravans = new ArrayList<>();
    private final List<String> tradeHistory = new ArrayList<>();
    private long nextCaravanTick;
    private BlockPos tradePoint;
    /** Persisted overworld chunk claims owned by this settlement. */
    private final Set<Long> claimedChunks = new HashSet<>();

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
            Map<String, Integer> reservedResources,
            List<Map<String, Integer>> layerCosts,
            boolean materialsReservedInWarehouse,
            int rotationDegrees
    ) {
        public ConstructionOrder(String type, int x, int y, int z, int progress, int totalLayers,
                                 Map<String, Integer> reservedResources, List<Map<String, Integer>> layerCosts,
                                 boolean materialsReservedInWarehouse) {
            this(type, x, y, z, progress, totalLayers, reservedResources, layerCosts,
                    materialsReservedInWarehouse, 0);
        }
        public ConstructionOrder(String type, int x, int y, int z, int progress, int totalLayers) {
            this(type, x, y, z, progress, totalLayers, Map.of(), List.of(), false, 0);
        }

        public ConstructionOrder(String type, int x, int y, int z, int progress,
                                 int totalLayers, Map<String, Integer> reservedResources) {
            this(type, x, y, z, progress, totalLayers, reservedResources, List.of(), false, 0);
        }

        public ConstructionOrder(String type, int x, int y, int z, int progress,
                                 int totalLayers, Map<String, Integer> reservedResources,
                                 List<Map<String, Integer>> layerCosts) {
            this(type, x, y, z, progress, totalLayers, reservedResources, layerCosts, false, 0);
        }

        public ConstructionOrder {
            reservedResources = reservedResources == null || reservedResources.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(reservedResources));

            if (layerCosts == null || layerCosts.isEmpty()) {
                layerCosts = List.of();
            } else {
                List<Map<String, Integer>> copiedLayers = new ArrayList<>();
                for (Map<String, Integer> layer : layerCosts) {
                    Map<String, Integer> copied = new TreeMap<>();
                    if (layer != null) {
                        layer.forEach((itemId, amount) -> {
                            if (itemId != null && !itemId.isBlank() && amount != null && amount > 0) {
                                copied.put(itemId, amount);
                            }
                        });
                    }
                    copiedLayers.add(Collections.unmodifiableMap(copied));
                }
                layerCosts = Collections.unmodifiableList(copiedLayers);
            }
        }

        /**
         * A layer plan is accepted only when it covers every layer and its
         * aggregate exactly matches the reservation snapshot. Invalid or
         * legacy plans fall back to the proportional 0.13 calculation.
         */
        public boolean hasValidLayerCosts() {
            if (layerCosts.isEmpty() || layerCosts.size() != Math.max(1, totalLayers)
                    || reservedResources.isEmpty()) {
                return false;
            }

            Map<String, Integer> totals = new TreeMap<>();
            for (Map<String, Integer> layer : layerCosts) {
                layer.forEach((itemId, amount) -> totals.merge(itemId, amount, Integer::sum));
            }
            return totals.equals(new TreeMap<>(reservedResources));
        }

        /**
         * Returns the reservation still assigned to unbuilt layers.
         * New orders use explicit stage costs; old saves retain proportional
         * refunds so updating the mod does not reinterpret their accounting.
         */
        public Map<String, Integer> remainingReservedResources() {
            if (reservedResources.isEmpty()) {
                return Map.of();
            }

            int safeTotalLayers = Math.max(1, totalLayers);
            int completedLayers = Math.max(0, Math.min(progress, safeTotalLayers));
            Map<String, Integer> remaining = new TreeMap<>();

            if (hasValidLayerCosts()) {
                for (int layerIndex = completedLayers; layerIndex < layerCosts.size(); layerIndex++) {
                    layerCosts.get(layerIndex).forEach((itemId, amount) ->
                            remaining.merge(itemId, amount, Integer::sum));
                }
            } else {
                for (Map.Entry<String, Integer> entry : reservedResources.entrySet()) {
                    int originalAmount = Math.max(0, entry.getValue());
                    long consumed = ((long) originalAmount * completedLayers) / safeTotalLayers;
                    int refundable = originalAmount - (int) consumed;
                    if (refundable > 0) {
                        remaining.put(entry.getKey(), refundable);
                    }
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
        data.lastEconomyDay = tag.contains("last_economy_day") ? tag.getLong("last_economy_day") : -1L;
        data.hungerDays = Math.max(0, Math.min(1_000_000, tag.getInt("hunger_days")));
        data.growthProgressDays = Math.max(0, Math.min(30, tag.getInt("growth_progress_days")));
        data.foodCredit = Math.max(0, Math.min(1_000_000, tag.getInt("food_credit")));
        int historyCapacity = CivitasConfig.ECONOMY_HISTORY_DAYS.get();
        data.economyHistory.setCapacity(historyCapacity);
        data.economyHistory.restoreFrom(EconomyHistory.load(tag, "economy_history", historyCapacity));
        data.farmlandReservations.restoreFrom(SettlementReservations.load(tag, "farmland_reservations"));
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
            List<Map<String, Integer>> layerCosts = new ArrayList<>();
            if (order.contains("layer_costs", Tag.TAG_LIST)) {
                ListTag storedLayers = order.getList("layer_costs", Tag.TAG_COMPOUND);
                for (int layerIndex = 0; layerIndex < storedLayers.size(); layerIndex++) {
                    CompoundTag storedLayer = storedLayers.getCompound(layerIndex);
                    Map<String, Integer> costs = new TreeMap<>();
                    for (String itemId : storedLayer.getAllKeys()) {
                        int count = storedLayer.getInt(itemId);
                        if (count > 0) costs.put(itemId, count);
                    }
                    layerCosts.add(costs);
                }
            }
            if (!type.isBlank() && progress < total) {
                // Missing flag means this is a pre-0.15 order whose materials
                // were already removed from storage when the order was placed.
                boolean materialsReservedInWarehouse = order.getBoolean("materials_in_warehouse");
                ConstructionOrder loaded = new ConstructionOrder(
                        type, order.getInt("x"), order.getInt("y"), order.getInt("z"),
                        progress, total, reservedResources, layerCosts, materialsReservedInWarehouse,
                        order.contains("rotation_degrees") ? order.getInt("rotation_degrees") : 0);
                data.constructionOrders.add(loaded);
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
        CompoundTag partnerTags = tag.getCompound("trade_partners");
        for (String key : partnerTags.getAllKeys()) {
            try {
                TradePartner partner = TradePartner.load(partnerTags.getCompound(key));
                data.tradePartners.put(partner.id(), partner);
            } catch (RuntimeException ignored) { }
        }
        CompoundTag reputationTags = tag.getCompound("trade_reputation");
        for (String key : reputationTags.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                data.tradeReputation.put(id, Math.max(0, Math.min(100, reputationTags.getInt(key))));
            } catch (IllegalArgumentException ignored) { }
        }
        CompoundTag civilizationTags = tag.getCompound("neighbor_civilizations");
        for (String key : civilizationTags.getAllKeys()) {
            try {
                NeighborCivilization civilization = NeighborCivilization.load(civilizationTags.getCompound(key));
                data.neighborCivilizations.put(civilization.id(), civilization);
            } catch (RuntimeException ignored) { }
        }
        // Migration for worlds saved before neighbouring civilizations existed.
        for (TradePartner partner : data.tradePartners.values()) {
            data.neighborCivilizations.computeIfAbsent(partner.id(),
                    id -> new NeighborCivilization(partner, id.getLeastSignificantBits()));
        }
        ListTag caravanTags = tag.getList("trade_caravans", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(8, caravanTags.size()); i++) {
            try { data.activeTradeCaravans.add(TradeCaravan.load(caravanTags.getCompound(i))); }
            catch (RuntimeException ignored) { }
        }
        ListTag historyTags = tag.getList("trade_history", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, historyTags.size() - 20); i < historyTags.size(); i++) {
            String entry = historyTags.getCompound(i).getString("text");
            if (!entry.isBlank()) data.tradeHistory.add(entry.substring(0, Math.min(256, entry.length())));
        }
        data.nextCaravanTick = Math.max(0L, tag.getLong("next_caravan_tick"));
        data.tradePoint = tag.contains("trade_point") ? BlockPos.of(tag.getLong("trade_point")) : null;
        ListTag storedClaims = tag.getList("territory_claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < storedClaims.size(); i++) {
            CompoundTag claim = storedClaims.getCompound(i);
            data.claimedChunks.add(new ChunkPos(claim.getInt("x"), claim.getInt("z")).toLong());
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
        tag.putLong("last_economy_day", lastEconomyDay);
        tag.putInt("hunger_days", hungerDays);
        tag.putInt("growth_progress_days", growthProgressDays);
        tag.putInt("food_credit", foodCredit);
        economyHistory.save(tag, "economy_history");
        farmlandReservations.save(tag, "farmland_reservations");
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
            if (!order.layerCosts().isEmpty()) {
                ListTag storedLayerCosts = new ListTag();
                for (Map<String, Integer> layer : order.layerCosts()) {
                    CompoundTag storedLayer = new CompoundTag();
                    layer.forEach(storedLayer::putInt);
                    storedLayerCosts.add(storedLayer);
                }
                stored.put("layer_costs", storedLayerCosts);
            }
            stored.putBoolean("materials_in_warehouse", order.materialsReservedInWarehouse());
            stored.putInt("rotation_degrees", Math.floorMod(order.rotationDegrees(), 360));
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
        CompoundTag partnerTags = new CompoundTag();
        tradePartners.forEach((id, partner) -> partnerTags.put(id.toString(), partner.save()));
        tag.put("trade_partners", partnerTags);
        CompoundTag civilizationTags = new CompoundTag();
        neighborCivilizations.forEach((id, civilization) -> civilizationTags.put(id.toString(), civilization.save()));
        tag.put("neighbor_civilizations", civilizationTags);
        CompoundTag reputationTags = new CompoundTag();
        tradeReputation.forEach((id, value) -> reputationTags.putInt(id.toString(), Math.max(0, Math.min(100, value))));
        tag.put("trade_reputation", reputationTags);
        ListTag caravanTags = new ListTag();
        for (TradeCaravan caravan : activeTradeCaravans) caravanTags.add(caravan.save());
        tag.put("trade_caravans", caravanTags);
        ListTag historyTags = new ListTag();
        for (String entry : tradeHistory) {
            CompoundTag history = new CompoundTag();
            history.putString("text", entry);
            historyTags.add(history);
        }
        tag.put("trade_history", historyTags);
        tag.putLong("next_caravan_tick", nextCaravanTick);
        if (tradePoint != null) tag.putLong("trade_point", tradePoint.asLong());
        ListTag storedClaims = new ListTag();
        for (long packed : claimedChunks) {
            ChunkPos chunk = new ChunkPos(packed);
            CompoundTag claim = new CompoundTag();
            claim.putInt("x", chunk.x);
            claim.putInt("z", chunk.z);
            storedClaims.add(claim);
        }
        tag.put("territory_claims", storedClaims);
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
        claimedChunks.clear();
        ChunkPos initialChunk = new ChunkPos(center);
        claimedChunks.add(initialChunk.toLong());
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
        addConstructionOrder(type, position, totalLayers, reservedResources, List.of());
    }

    public void addConstructionOrder(
            String type, BlockPos position, int totalLayers,
            Map<String, Integer> reservedResources, List<Map<String, Integer>> layerCosts) {
        addConstructionOrder(type, position, totalLayers, reservedResources, layerCosts, false);
    }

    public void addConstructionOrder(
            String type, BlockPos position, int totalLayers,
            Map<String, Integer> reservedResources, List<Map<String, Integer>> layerCosts,
            boolean materialsReservedInWarehouse) {
        addConstructionOrder(type, position, totalLayers, reservedResources, layerCosts,
                materialsReservedInWarehouse, 0);
    }

    public void addConstructionOrder(
            String type, BlockPos position, int totalLayers,
            Map<String, Integer> reservedResources, List<Map<String, Integer>> layerCosts,
            boolean materialsReservedInWarehouse, int rotationDegrees) {
        if (type == null || type.isBlank() || position == null || totalLayers < 1) return;
        Map<String, Integer> reservation = new TreeMap<>();
        if (reservedResources != null) {
            reservedResources.forEach((itemId, amount) -> {
                if (itemId != null && !itemId.isBlank() && amount != null && amount > 0) {
                    reservation.put(itemId, amount);
                }
            });
        }
        ConstructionOrder order = new ConstructionOrder(
                type, position.getX(), position.getY(), position.getZ(), 0, totalLayers,
                reservation, layerCosts, materialsReservedInWarehouse, Math.floorMod(rotationDegrees, 360));
        if (materialsReservedInWarehouse && !order.hasValidLayerCosts()) {
            // Do not create an unpaid order without a complete stage cost plan.
            return;
        }
        if (!materialsReservedInWarehouse && !order.hasValidLayerCosts()) {
            order = new ConstructionOrder(type, position.getX(), position.getY(), position.getZ(),
                    0, totalLayers, reservation);
        }
        constructionOrders.add(order);
        setDirty();
    }

    /** Materials still physically reserved in the warehouse by active 0.15+ orders. */
    public Map<String, Integer> getWarehouseReservedResources() {
        return aggregateWarehouseReservations(null);
    }

    public Map<String, Integer> getWarehouseReservedResourcesExcluding(ConstructionOrder excluded) {
        return aggregateWarehouseReservations(excluded);
    }

    private Map<String, Integer> aggregateWarehouseReservations(ConstructionOrder excluded) {
        Map<String, Integer> result = new TreeMap<>();
        for (ConstructionOrder order : constructionOrders) {
            if (order.equals(excluded) || !order.materialsReservedInWarehouse()) continue;
            order.remainingReservedResources().forEach((itemId, amount) -> {
                if (amount != null && amount > 0) {
                    long sum = (long) result.getOrDefault(itemId, 0) + amount;
                    result.put(itemId, (int) Math.min(Integer.MAX_VALUE, sum));
                }
            });
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * True only when physical inventory contains every outstanding reservation.
     * This prevents one order from consuming materials promised to another order.
     */
    public boolean warehouseContainsAllReservations(Map<String, Integer> physicalStock) {
        Map<String, Integer> stock = physicalStock == null ? Map.of() : physicalStock;
        for (Map.Entry<String, Integer> reservation : getWarehouseReservedResources().entrySet()) {
            if (stock.getOrDefault(reservation.getKey(), 0) < reservation.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Checks whether a new order can reserve stock without overbooking current orders. */
    public boolean canReserveWarehouseResources(
            Map<String, Integer> physicalStock, Map<String, Integer> requestedCosts) {
        Map<String, Integer> stock = physicalStock == null ? Map.of() : physicalStock;
        Map<String, Integer> requested = requestedCosts == null ? Map.of() : requestedCosts;
        Map<String, Integer> reserved = getWarehouseReservedResources();
        if (!warehouseContainsAllReservations(stock)) return false;
        for (Map.Entry<String, Integer> cost : requested.entrySet()) {
            if (cost.getKey() == null || cost.getValue() == null || cost.getValue() < 0) return false;
            long required = (long) reserved.getOrDefault(cost.getKey(), 0) + cost.getValue();
            if (stock.getOrDefault(cost.getKey(), 0) < required) return false;
        }
        return true;
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
                    order.progress() + 1, order.totalLayers(),
                    order.reservedResources(), order.layerCosts(), order.materialsReservedInWarehouse(), order.rotationDegrees()));
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

    /** Current territory influence is derived from saved settlement state. */
    public TerritoryInfluence.Result getTerritoryInfluence() {
        return TerritoryInfluence.calculate(population, level, buildings);
    }

    /** Use the physical starter-village center when it exists; otherwise use the founding center. */
    public BlockPos getTerritoryCenter() {
        return hasStarterVillage() ? getStarterVillageCenter() : getCenter();
    }

    /**
     * Whether a planned building site falls inside the settlement's current
     * influence radius. This is a horizontal radius check, not chunk ownership.
     */
    /** Maximum number of chunks the settlement may claim at its current development level. */
    public int getTerritoryClaimLimit() {
        return Math.max(1, Math.min(16, 1 + getTerritoryInfluence().score() / 100));
    }

    public int getClaimedChunkCount() {
        return claimedChunks.size();
    }

    public boolean isChunkClaimed(int chunkX, int chunkZ) {
        return claimedChunks.contains(new ChunkPos(chunkX, chunkZ).toLong());
    }

    public boolean isChunkClaimed(BlockPos position) {
        if (position == null) return false;
        return isChunkClaimed(position.getX() >> 4, position.getZ() >> 4);
    }

    /** Claims a chunk only if it is inside the settlement's influence and capacity. */
    public boolean claimChunk(int chunkX, int chunkZ) {
        if (!founded || isChunkClaimed(chunkX, chunkZ) || claimedChunks.size() >= getTerritoryClaimLimit()) {
            return false;
        }
        BlockPos chunkCenter = new BlockPos(chunkX * 16 + 8, getTerritoryCenter().getY(), chunkZ * 16 + 8);
        if (!isWithinTerritory(chunkCenter)) return false;
        claimedChunks.add(new ChunkPos(chunkX, chunkZ).toLong());
        setDirty();
        return true;
    }

    /** The founding chunk is permanent; other chunks can be released by the owner. */
    public boolean unclaimChunk(int chunkX, int chunkZ) {
        if (!founded || !isChunkClaimed(chunkX, chunkZ)) return false;
        ChunkPos foundingChunk = new ChunkPos(getCenter());
        long packed = new ChunkPos(chunkX, chunkZ).toLong();
        if (packed == foundingChunk.toLong()) return false;
        boolean removed = claimedChunks.remove(packed);
        if (removed) setDirty();
        return removed;
    }

    public List<ChunkPos> getClaimedChunks() {
        List<ChunkPos> result = new ArrayList<>();
        for (long packed : claimedChunks) result.add(new ChunkPos(packed));
        result.sort(java.util.Comparator.comparingInt((ChunkPos chunk) -> chunk.x)
                .thenComparingInt(chunk -> chunk.z));
        return Collections.unmodifiableList(result);
    }

    public boolean isWithinTerritory(BlockPos position) {
        if (!founded || position == null) return false;
        BlockPos center = getTerritoryCenter();
        int radius = getTerritoryInfluence().radius();
        long dx = (long) position.getX() - center.getX();
        long dz = (long) position.getZ() - center.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }
    public boolean hasBuildingNear(BlockPos position, int radius) {
        for (BuildingRecord building : buildings) {
            if (Math.abs(building.x() - position.getX()) <= radius && Math.abs(building.z() - position.getZ()) <= radius) return true;
        }
        return false;
    }

    // Trade & caravans (0.20.0).
    public Map<UUID, TradePartner> getTradePartners() { return Collections.unmodifiableMap(new HashMap<>(tradePartners)); }
    public TradePartner getTradePartner(UUID id) { return tradePartners.get(id); }
    public void putTradePartner(TradePartner partner) {
        if (partner == null) return;
        tradePartners.put(partner.id(), partner);
        tradeReputation.putIfAbsent(partner.id(), 50);
        neighborCivilizations.computeIfAbsent(partner.id(),
                id -> new NeighborCivilization(partner, id.getLeastSignificantBits()));
        setDirty();
    }
    public Map<UUID, NeighborCivilization> getNeighborCivilizations() {
        return Collections.unmodifiableMap(new HashMap<>(neighborCivilizations));
    }
    public NeighborCivilization getNeighborCivilization(UUID id) { return neighborCivilizations.get(id); }
    public boolean setNeighborStance(UUID id, NeighborCivilization.Stance stance) {
        NeighborCivilization civilization = neighborCivilizations.get(id);
        if (civilization == null || stance == null) return false;
        civilization.setStance(stance);
        setDirty();
        return true;
    }
    public int getTradeReputation(UUID id) { return Math.max(0, Math.min(100, tradeReputation.getOrDefault(id, 50))); }
    public int addTradeReputation(UUID id, int delta) {
        if (id == null) return 0;
        int value = Math.max(0, Math.min(100, getTradeReputation(id) + delta));
        tradeReputation.put(id, value);
        setDirty();
        return value;
    }
    public List<TradeCaravan> getActiveTradeCaravans() { return activeTradeCaravans; }
    public List<String> getTradeHistory() { return List.copyOf(tradeHistory); }
    public void recordTrade(String entry) {
        if (entry == null || entry.isBlank()) return;
        tradeHistory.add(entry.substring(0, Math.min(256, entry.length())));
        while (tradeHistory.size() > 20) tradeHistory.remove(0);
        setDirty();
    }
    public long getNextCaravanTick() { return nextCaravanTick; }
    public void setNextCaravanTick(long tick) {
        long safe = Math.max(0L, tick);
        if (nextCaravanTick != safe) { nextCaravanTick = safe; setDirty(); }
    }
    public BlockPos getTradePoint() { return tradePoint; }
    public void setTradePoint(BlockPos pos) {
        BlockPos safe = pos == null ? null : pos.immutable();
        if (tradePoint == null ? safe != null : !tradePoint.equals(safe)) { tradePoint = safe; setDirty(); }
    }
    public void markTradeDataDirty() { setDirty(); }

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

    public void setPopulation(int population) {
        int safe = Math.max(0, Math.min(1_000_000, population));
        if (this.population != safe) { this.population = safe; setDirty(); }
    }

    public long getLastEconomyDay() { return lastEconomyDay; }
    public void setLastEconomyDay(long day) {
        if (lastEconomyDay != day) { lastEconomyDay = day; setDirty(); }
    }
    public int getFoodCredit() { return foodCredit; }
    public void setFoodCredit(int value) {
        int safe = Math.max(0, Math.min(1_000_000, value));
        if (foodCredit != safe) { foodCredit = safe; setDirty(); }
    }
    public int getHungerDays() { return hungerDays; }
    public void setHungerDays(int days) {
        int safe = Math.max(0, Math.min(1_000_000, days));
        if (hungerDays != safe) { hungerDays = safe; setDirty(); }
    }
    public int getGrowthProgressDays() { return growthProgressDays; }
    public void setGrowthProgressDays(int days) {
        int safe = Math.max(0, Math.min(30, days));
        if (growthProgressDays != safe) { growthProgressDays = safe; setDirty(); }
    }

    public EconomyHistory getEconomyHistory() { return economyHistory; }

    public void setEconomyHistoryCapacity(int capacity) {
        if (economyHistory.setCapacity(capacity)) setDirty();
    }

    public void recordFoodProduction(long day, int foodValue) {
        if (foodValue <= 0) return;
        economyHistory.recordFoodProduction(day, foodValue);
        economyHistory.recordEvent(day, "Фермер доставил " + foodValue + " ед. еды");
        setDirty();
    }

    public boolean tryReserveFarmland(BlockPos pos, UUID workerId) {
        boolean reserved = farmlandReservations.tryReserve(pos, workerId);
        if (reserved) setDirty();
        return reserved;
    }

    public boolean isFarmlandReservedByOther(BlockPos pos, UUID workerId) {
        return farmlandReservations.isReservedByOther(pos, workerId);
    }

    public boolean isFarmlandReservedBy(BlockPos pos, UUID workerId) {
        return farmlandReservations.isReservedBy(pos, workerId);
    }

    public void releaseFarmland(BlockPos pos, UUID workerId) {
        if (farmlandReservations.release(pos, workerId)) setDirty();
    }

    public void releaseFarmlandForWorker(UUID workerId) {
        if (farmlandReservations.releaseAll(workerId)) setDirty();
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
