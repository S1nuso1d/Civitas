package ru.civitas.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded persistent ledger of daily stocks, food flows and settlement events. */
public final class EconomyHistory {
    public static final class DailyRecord {
        public long day;
        public int wood, stone, food, population, foodProduced, foodConsumed;
        public DailyRecord() { }
        public DailyRecord(long day) { this.day = day; }
    }

    private final int capacity;
    private final Deque<DailyRecord> days = new ArrayDeque<>();
    private final Deque<String> events = new ArrayDeque<>();

    public EconomyHistory(int capacity) { this.capacity = Math.max(7, Math.min(90, capacity)); }

    public void recordDaily(long day, int wood, int stone, int food, int population) {
        DailyRecord r = getOrCreate(day);
        r.wood = Math.max(0, wood); r.stone = Math.max(0, stone);
        r.food = Math.max(0, food); r.population = Math.max(0, population);
        trim();
    }
    public void recordFoodProduction(long day, int value) {
        if (value <= 0) return;
        DailyRecord r = getOrCreate(day); r.foodProduced = safeAdd(r.foodProduced, value); trim();
    }
    public void recordFoodConsumption(long day, int value) {
        if (value <= 0) return;
        DailyRecord r = getOrCreate(day); r.foodConsumed = safeAdd(r.foodConsumed, value); trim();
    }
    public void recordEvent(long day, String message) {
        if (message == null || message.isBlank()) return;
        events.addLast("День " + Math.max(0, day) + ": " + message);
        while (events.size() > 32) events.removeFirst();
    }

    public List<DailyRecord> snapshot() {
        List<DailyRecord> result = new ArrayList<>();
        for (DailyRecord r : days) {
            DailyRecord c = new DailyRecord(r.day);
            c.wood=r.wood; c.stone=r.stone; c.food=r.food; c.population=r.population;
            c.foodProduced=r.foodProduced; c.foodConsumed=r.foodConsumed;
            result.add(c);
        }
        return List.copyOf(result);
    }
    public List<String> recentEvents() { return List.copyOf(events); }
    public List<String> formattedRecentDays(int maxLines) {
        List<DailyRecord> records = snapshot();
        int start = Math.max(0, records.size() - Math.max(0, maxLines));
        List<String> lines = new ArrayList<>();
        for (int i=start; i<records.size(); i++) {
            DailyRecord r=records.get(i);
            lines.add("День " + r.day + ": дерево " + r.wood + ", камень " + r.stone
                    + ", еда " + r.food + " (+" + r.foodProduced + "/-" + r.foodConsumed
                    + "), жители " + r.population);
        }
        return List.copyOf(lines);
    }
    public int deltaWood(int daysBack) { return delta(daysBack, 0); }
    public int deltaStone(int daysBack) { return delta(daysBack, 1); }
    public int deltaFood(int daysBack) { return delta(daysBack, 2); }

    private int delta(int daysBack, int field) {
        if (days.size() < 2) return 0;
        DailyRecord latest=days.peekLast();
        long target=latest.day-Math.max(1, daysBack);
        DailyRecord baseline=days.peekFirst();
        for (DailyRecord r:days) { if(r.day<=target) baseline=r; else break; }
        return read(latest, field)-read(baseline, field);
    }
    private static int read(DailyRecord r,int field) {
        return switch(field) { case 0 -> r.wood; case 1 -> r.stone; default -> r.food; };
    }
    private DailyRecord getOrCreate(long day) {
        for(DailyRecord r:days) if(r.day==day) return r;
        DailyRecord r=new DailyRecord(day); days.addLast(r); return r;
    }
    private static int safeAdd(int a,int b) { return (int)Math.min(Integer.MAX_VALUE,(long)a+b); }
    private void trim() { while(days.size()>capacity) days.removeFirst(); }

    public void save(CompoundTag parent, String key) {
        CompoundTag root=new CompoundTag(); ListTag list=new ListTag();
        for(DailyRecord r:days) {
            CompoundTag t=new CompoundTag(); t.putLong("day",r.day); t.putInt("wood",r.wood);
            t.putInt("stone",r.stone); t.putInt("food",r.food); t.putInt("population",r.population);
            t.putInt("food_produced",r.foodProduced); t.putInt("food_consumed",r.foodConsumed); list.add(t);
        }
        root.put("days",list);
        ListTag ev=new ListTag();
        for(String message:events) { CompoundTag t=new CompoundTag(); t.putString("message",message); ev.add(t); }
        root.put("events",ev); parent.put(key,root);
    }
    public static EconomyHistory load(CompoundTag parent,String key,int capacity) {
        EconomyHistory h=new EconomyHistory(capacity);
        if(parent==null || !parent.contains(key,Tag.TAG_COMPOUND)) return h;
        CompoundTag root=parent.getCompound(key);
        ListTag days=root.getList("days",Tag.TAG_COMPOUND);
        for(int i=Math.max(0,days.size()-h.capacity);i<days.size();i++) {
            CompoundTag t=days.getCompound(i); DailyRecord r=new DailyRecord(t.getLong("day"));
            r.wood=Math.max(0,t.getInt("wood")); r.stone=Math.max(0,t.getInt("stone"));
            r.food=Math.max(0,t.getInt("food")); r.population=Math.max(0,t.getInt("population"));
            r.foodProduced=Math.max(0,t.getInt("food_produced")); r.foodConsumed=Math.max(0,t.getInt("food_consumed"));
            h.days.addLast(r);
        }
        ListTag events=root.getList("events",Tag.TAG_COMPOUND);
        for(int i=Math.max(0,events.size()-32);i<events.size();i++) {
            String message=events.getCompound(i).getString("message");
            if(!message.isBlank()) h.events.addLast(message);
        }
        return h;
    }
    public void restoreFrom(EconomyHistory other) {
        days.clear(); events.clear();
        if(other==null)return;
        for(DailyRecord r:other.snapshot()) { days.addLast(r); trim(); }
        events.addAll(other.recentEvents());
        while(events.size()>32) events.removeFirst();
    }
}
