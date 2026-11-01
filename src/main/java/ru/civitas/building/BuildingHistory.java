package ru.civitas.building;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Bounded, persistent event journal for one registered building or construction site. */
public final class BuildingHistory {
    public static final int MAX_EVENTS = 24;

    public enum EventType {
        CONSTRUCTION_STARTED("Начало строительства"),
        CONSTRUCTION_COMPLETED("Строительство завершено"),
        CONSTRUCTION_CANCELLED("Строительство отменено"),
        UPGRADED("Улучшение"),
        WEAR_STATE_CHANGED("Изменение состояния"),
        DAMAGED("Повреждение"),
        REPAIRED("Ремонт");

        private final String displayName;

        EventType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record Entry(long day, EventType type, String actor, String description) {
        public Entry {
            day = Math.max(0L, day);
            type = type == null ? EventType.WEAR_STATE_CHANGED : type;
            actor = sanitize(actor, 32);
            description = sanitize(description, 110);
        }

        public String toDisplayLine() {
            StringBuilder line = new StringBuilder("День ")
                    .append(day).append(" · ").append(type.displayName())
                    .append(": ").append(description);
            if (!actor.isBlank()) line.append(" (").append(actor).append(')');
            return line.toString();
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    public boolean record(long day, EventType type, String actor, String description) {
        if (type == null || description == null || description.isBlank()) return false;
        entries.addLast(new Entry(day, type, actor, description));
        while (entries.size() > MAX_EVENTS) entries.removeFirst();
        return true;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag storedEntries = new ListTag();
        for (Entry entry : entries) {
            CompoundTag stored = new CompoundTag();
            stored.putLong("day", entry.day());
            stored.putString("type", entry.type().name());
            stored.putString("actor", entry.actor());
            stored.putString("description", entry.description());
            storedEntries.add(stored);
        }
        root.put("events", storedEntries);
        return root;
    }

    public static BuildingHistory load(CompoundTag root) {
        BuildingHistory history = new BuildingHistory();
        if (root == null || !root.contains("events", Tag.TAG_LIST)) return history;
        ListTag storedEntries = root.getList("events", Tag.TAG_COMPOUND);
        int start = Math.max(0, storedEntries.size() - MAX_EVENTS);
        for (int i = start; i < storedEntries.size(); i++) {
            CompoundTag stored = storedEntries.getCompound(i);
            EventType type;
            try {
                type = EventType.valueOf(stored.getString("type"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String description = stored.getString("description");
            if (!description.isBlank()) {
                history.record(stored.getLong("day"), type, stored.getString("actor"), description);
            }
        }
        return history;
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
