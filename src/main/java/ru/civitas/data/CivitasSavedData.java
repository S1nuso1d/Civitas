package ru.civitas.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.Collections;
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
