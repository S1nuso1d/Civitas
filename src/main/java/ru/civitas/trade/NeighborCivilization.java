package ru.civitas.trade;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Persistent lightweight simulation of a neighbouring civilization. */
public final class NeighborCivilization {
    public enum Stance {
        NEUTRAL("Нейтралитет"), ALLIED("Союз"), RIVAL("Соперничество"), WAR("Война");
        private final String displayName;
        Stance(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public static Stance parse(String value) {
            if (value == null) return NEUTRAL;
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "allied", "ally", "союз" -> ALLIED;
                case "rival", "соперничество", "соперник" -> RIVAL;
                case "war", "война" -> WAR;
                default -> NEUTRAL;
            };
        }
    }

    private final UUID id;
    private final String name;
    private final int distance;
    private Stance stance;
    private int population, food, timber, stone, iron, territory, stability, hungerDays;
    private long lastSimulatedDay;
    private int siteX, siteY, siteZ;
    private boolean siteAssigned, siteGenerated;

    public NeighborCivilization(TradePartner partner, long seed) {
        id = partner.id();
        name = partner.name();
        distance = clamp(partner.distance(), 0, 100_000);
        java.util.Random random = new java.util.Random(seed ^ id.getMostSignificantBits());
        stance = Stance.NEUTRAL;
        population = 12 + random.nextInt(29);
        food = 80 + random.nextInt(121);
        timber = 40 + random.nextInt(101);
        stone = 20 + random.nextInt(61);
        iron = random.nextInt(25);
        territory = 1 + random.nextInt(4);
        stability = 65 + random.nextInt(31);
        lastSimulatedDay = -1;
    }

    private NeighborCivilization(UUID id, String name, int distance, Stance stance, int population,
            int food, int timber, int stone, int iron, int territory, int stability,
            int hungerDays, long lastSimulatedDay) {
        this.id = id;
        this.name = name == null || name.isBlank() ? "Неизвестная цивилизация" : name.substring(0, Math.min(64, name.length()));
        this.distance = clamp(distance, 0, 100_000);
        this.stance = stance == null ? Stance.NEUTRAL : stance;
        this.population = clamp(population, 1, 100_000);
        this.food = clamp(food, 0, 1_000_000);
        this.timber = clamp(timber, 0, 1_000_000);
        this.stone = clamp(stone, 0, 1_000_000);
        this.iron = clamp(iron, 0, 1_000_000);
        this.territory = clamp(territory, 1, 100_000);
        this.stability = clamp(stability, 0, 100);
        this.hungerDays = clamp(hungerDays, 0, 1_000_000);
        this.lastSimulatedDay = Math.max(-1, lastSimulatedDay);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public int distance() { return distance; }
    public Stance stance() { return stance; }
    public int population() { return population; }
    public int food() { return food; }
    public int timber() { return timber; }
    public int stone() { return stone; }
    public int iron() { return iron; }
    public int territory() { return territory; }
    public int stability() { return stability; }
    public long lastSimulatedDay() { return lastSimulatedDay; }
    public boolean hasSite() { return siteAssigned; }
    public boolean siteGenerated() { return siteGenerated; }
    public net.minecraft.core.BlockPos site() {
        return siteAssigned ? new net.minecraft.core.BlockPos(siteX, siteY, siteZ) : net.minecraft.core.BlockPos.ZERO;
    }
    public void setSite(net.minecraft.core.BlockPos pos, boolean generated) {
        if (pos == null) return;
        siteX = pos.getX(); siteY = pos.getY(); siteZ = pos.getZ();
        siteAssigned = true; siteGenerated = generated;
    }
    public void markSiteGenerated() { if (siteAssigned) siteGenerated = true; }
    public void setStance(Stance value) { stance = value == null ? Stance.NEUTRAL : value; }
    public boolean permitsTrade() { return stance != Stance.WAR; }

    /** Bounded catch-up prevents long unloads from causing large simulation spikes. */
    public int advanceToDay(long targetDay) {
        if (targetDay <= lastSimulatedDay) return 0;
        int steps = (int) Math.min(30L, targetDay - lastSimulatedDay);
        for (int i = 0; i < steps; i++) simulateOneDay();
        lastSimulatedDay = targetDay;
        return steps;
    }

    private void simulateOneDay() {
        int workers = Math.max(1, population / 4);
        food = clamp(food + population * 2 + workers - population * 2, 0, 1_000_000);
        timber = clamp(timber + Math.max(1, workers / 2) - (territory > 12 ? 2 : 0), 0, 1_000_000);
        stone = clamp(stone + Math.max(0, workers / 3) - (territory > 8 ? 1 : 0), 0, 1_000_000);
        iron = clamp(iron + (workers >= 8 ? 1 : 0), 0, 1_000_000);

        // Diplomatic stance has tangible economic and social consequences.
        // These effects are intentionally small and are applied only during
        // the bounded daily simulation, never per tick.
        switch (stance) {
            case ALLIED -> {
                food = clamp(food + Math.max(1, population / 6), 0, 1_000_000);
                stability = clamp(stability + 1, 0, 100);
            }
            case RIVAL -> {
                stability = clamp(stability - 1, 0, 100);
                if (timber > 0 && territory > 4) timber--;
            }
            case WAR -> {
                stability = clamp(stability - 2, 0, 100);
                if (timber > 0) timber--;
                if (iron > 0 && population >= 20) iron--;
                food = Math.max(0, food - Math.max(1, population / 10));
            }
            case NEUTRAL -> { }
        }

        if (food < population * 2) {
            hungerDays = Math.min(1_000_000, hungerDays + 1);
            stability = clamp(stability - 3, 0, 100);
            if (hungerDays >= 3 && population > 1) { population--; hungerDays = 0; }
        } else {
            hungerDays = 0;
            stability = clamp(stability + 1, 0, 100);
            if (food > population * 5 && stability >= 70 && population < 100_000) {
                population++;
                food -= 4;
            }
        }
        int expansionThreshold = stance == Stance.ALLIED ? 72 : 80;
        if (stability >= expansionThreshold && population >= territory * 12
                && timber >= 30 && stone >= 20 && territory < 100_000) {
            timber -= 15; stone -= 10; territory++;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id); tag.putString("name", name); tag.putInt("distance", distance);
        tag.putString("stance", stance.name()); tag.putInt("population", population);
        tag.putInt("food", food); tag.putInt("timber", timber); tag.putInt("stone", stone);
        tag.putInt("iron", iron); tag.putInt("territory", territory); tag.putInt("stability", stability);
        tag.putInt("hunger_days", hungerDays); tag.putLong("last_simulated_day", lastSimulatedDay);
        tag.putBoolean("site_assigned", siteAssigned);
        if (siteAssigned) {
            tag.putInt("site_x", siteX); tag.putInt("site_y", siteY); tag.putInt("site_z", siteZ);
        }
        tag.putBoolean("site_generated", siteGenerated);
        return tag;
    }

    public static NeighborCivilization load(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        Stance stance;
        try { stance = Stance.valueOf(tag.getString("stance")); }
        catch (IllegalArgumentException ignored) { stance = Stance.NEUTRAL; }
        NeighborCivilization civilization = new NeighborCivilization(id, tag.getString("name"), tag.getInt("distance"), stance,
                tag.getInt("population"), tag.getInt("food"), tag.getInt("timber"), tag.getInt("stone"),
                tag.getInt("iron"), tag.getInt("territory"), tag.getInt("stability"),
                tag.getInt("hunger_days"), tag.contains("last_simulated_day") ? tag.getLong("last_simulated_day") : -1);
        if (tag.getBoolean("site_assigned")) {
            civilization.setSite(new net.minecraft.core.BlockPos(tag.getInt("site_x"), tag.getInt("site_y"),
                    tag.getInt("site_z")), tag.getBoolean("site_generated"));
        }
        return civilization;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}