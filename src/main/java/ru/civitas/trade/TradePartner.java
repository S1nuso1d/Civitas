package ru.civitas.trade;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public record TradePartner(UUID id, String name, TradePersonality personality, int distance) {
    private static final String[][] NAME_PARTS = {
        {"Северный", "Южный", "Восточный", "Западный", "Горный", "Лесной", "Речной", "Степной", "Солнечный", "Лунный", "Древний", "Новый"},
        {"Торговый Союз", "Королевство", "Княжество", "Гильдия", "Орден", "Клан", "Совет", "Дом", "Сообщество", "Ложа"}
    };

    public TradePartner {
        if (id == null) id = UUID.randomUUID();
        name = name == null || name.isBlank() ? "Неизвестный партнёр" : name.substring(0, Math.min(64, name.length()));
        if (personality == null) personality = TradePersonality.NEUTRAL;
        distance = Math.max(0, Math.min(100_000, distance));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("personality", personality.name());
        tag.putInt("distance", distance);
        return tag;
    }

    public static TradePartner load(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        return new TradePartner(id, tag.getString("name"),
                TradePersonality.byName(tag.getString("personality")), tag.getInt("distance"));
    }

    public static String generateName(java.util.Random random) {
        return NAME_PARTS[0][random.nextInt(NAME_PARTS[0].length)] + " "
                + NAME_PARTS[1][random.nextInt(NAME_PARTS[1].length)];
    }

    public int arrivalIntervalDays() {
        int base = 3 + distance / 100;
        if (personality == TradePersonality.FRIENDLY) base--;
        if (personality == TradePersonality.WARLIKE) base++;
        return Math.max(2, Math.min(30, base));
    }
}