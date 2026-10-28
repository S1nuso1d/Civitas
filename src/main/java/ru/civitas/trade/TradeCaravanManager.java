package ru.civitas.trade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import ru.civitas.CivitasConfig;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Server-owned lifecycle of caravans. Runs once per second and never treats an unloaded trader as dead immediately. */
public final class TradeCaravanManager {
    private static final int ARRIVAL_DISTANCE = 40;
    private static final int MAX_CARAVANS = 1;
    private static final int MISSING_GRACE_TICKS = 1200;
    private TradeCaravanManager() { }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 0) return;
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.hasStarterVillage()) return;
        BlockPos center = data.getStarterVillageCenter();
        if (!level.hasChunkAt(center)) return;
        if (data.getTradePoint() == null) {
            // Avoid scanning the surrounding area every second if a settlement has no bell.
            if (level.getGameTime() % 1200L != 0L) return;
            data.setTradePoint(findTradePoint(level, center));
        }
        BlockPos tradePoint = data.getTradePoint();
        if (tradePoint == null || !level.hasChunkAt(tradePoint)) return;

        List<TradeCaravan> caravans = data.getActiveTradeCaravans();
        for (int i = caravans.size() - 1; i >= 0; i--) {
            TradeCaravan caravan = caravans.get(i);
            boolean done = update(level, data, caravan);
            if (done) {
                TradePartner partner = data.getTradePartner(caravan.partnerId());
                data.recordTrade("Караван «" + (partner == null ? "неизвестный партнёр" : partner.name()) + "» покинул поселение");
                caravans.remove(i);
                data.markTradeDataDirty();
            }
        }

        long now = level.getGameTime();
        if (caravans.size() < MAX_CARAVANS && (data.getNextCaravanTick() == 0 || now >= data.getNextCaravanTick())) {
            spawn(level, data, center, tradePoint, now);
        }
    }

    private static boolean update(ServerLevel level, CivitasSavedData data, TradeCaravan caravan) {
        Entity entity = level.getEntity(caravan.traderId());
        if (!(entity instanceof Villager trader) || !trader.isAlive()) {
            // Entity lookup only sees loaded entities. Give a missing trader time to load back in.
            // An unloaded entity is not a dead entity. Death is handled by LivingDeathEvent.
            return false;
        }
        caravan.resetMissing();
        if (trader.getPersistentData().getBoolean("civitas.trader.attacked")) {
            data.addTradeReputation(caravan.partnerId(), -10);
            data.recordTrade("Караван атакован: репутация партнёра −10");
            caravan.setState(TradeCaravan.State.LEAVE);
            trader.getPersistentData().remove("civitas.trader.attacked");
        }
        switch (caravan.state()) {
            case APPROACH -> {
                if (trader.blockPosition().distSqr(caravan.tradePoint()) <= 9.0D) {
                    trader.getNavigation().stop();
                    caravan.setState(TradeCaravan.State.WAIT);
                    data.recordTrade("Караван прибыл: " + partnerName(data, caravan.partnerId()));
                    data.markTradeDataDirty();
                } else if (trader.getNavigation().isDone()) {
                    trader.getNavigation().moveTo(caravan.tradePoint().getX() + 0.5,
                            caravan.tradePoint().getY(), caravan.tradePoint().getZ() + 0.5, 1.0D);
                }
            }
            case WAIT -> {
                caravan.tickWait();
                data.markTradeDataDirty();
                if (caravan.waitTicksLeft() <= 0 || caravan.tradesUsed() >= caravan.tradeLimit()) {
                    caravan.setState(TradeCaravan.State.LEAVE);
                    data.markTradeDataDirty();
                }
            }
            case LEAVE -> {
                if (trader.blockPosition().distSqr(caravan.spawnPoint()) <= 16.0D) {
                    trader.discard();
                    return true;
                }
                if (trader.getNavigation().isDone()) {
                    trader.getNavigation().moveTo(caravan.spawnPoint().getX() + 0.5,
                            caravan.spawnPoint().getY(), caravan.spawnPoint().getZ() + 0.5, 1.1D);
                }
            }
        }
        return false;
    }

    private static void spawn(ServerLevel level, CivitasSavedData data, BlockPos center, BlockPos tradePoint, long now) {
        TradePartner partner = choosePartner(data, now);
        Random random = new Random(partner.id().getMostSignificantBits() ^ now);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        BlockPos rough = center.offset((int)(Math.cos(angle) * ARRIVAL_DISTANCE), 0,
                (int)(Math.sin(angle) * ARRIVAL_DISTANCE));
        BlockPos spawn = findSurface(level, rough);
        if (spawn == null) {
            data.setNextCaravanTick(now + 24000L);
            return;
        }
        Villager trader = EntityType.VILLAGER.create(level);
        if (trader == null) {
            data.setNextCaravanTick(now + 1200L);
            return;
        }
        trader.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        trader.setCustomName(Component.literal("Торговец из «" + partner.name() + "»"));
        trader.setCustomNameVisible(true);
        trader.setVillagerData(trader.getVillagerData().setProfession(VillagerProfession.NITWIT).setLevel(1));
        trader.setPersistenceRequired();
        trader.getPersistentData().putBoolean("civitas.trader", true);
        trader.getPersistentData().putUUID("civitas.trader.partner", partner.id());
        if (!level.addFreshEntity(trader)) {
            data.setNextCaravanTick(now + 1200L);
            return;
        }
        TradeCaravan caravan = new TradeCaravan(partner.id(), trader.getUUID(), tradePoint, spawn,
                partner.personality().tradeLimit);
        caravan.offers().addAll(TradeOfferGenerator.generate(partner.id(),
                Math.max(0L, level.getDayTime() / 24000L), partner.personality(), data.getEconomyHistory()));
        caravan.setWaitTicksLeft(CivitasConfig.CARAVAN_WAIT_SECONDS.get() * 20);
        data.getActiveTradeCaravans().add(caravan);
        int personalityOffset = partner.personality() == TradePersonality.FRIENDLY ? -1
                : partner.personality() == TradePersonality.WARLIKE ? 1 : 0;
        int intervalDays = Math.max(1, Math.min(30,
                CivitasConfig.CARAVAN_BASE_INTERVAL_DAYS.get() + partner.distance() / 100 + personalityOffset));
        data.setNextCaravanTick(now + (long) intervalDays * 24000L);
        data.recordTrade("Караван из «" + partner.name() + "» прибывает");
        data.markTradeDataDirty();
    }

    private static TradePartner choosePartner(CivitasSavedData data, long now) {
        var existing = data.getTradePartners();
        Random random = new Random(now ^ 0x435649544153L);
        if (!existing.isEmpty() && random.nextDouble() < 0.6D) {
            List<TradePartner> list = new ArrayList<>(existing.values());
            return list.get(random.nextInt(list.size()));
        }
        TradePersonality[] personalities = TradePersonality.values();
        TradePersonality personality = personalities[random.nextInt(personalities.length)];
        TradePartner partner = new TradePartner(UUID.randomUUID(), TradePartner.generateName(random),
                personality, 100 + random.nextInt(500));
        data.putTradePartner(partner);
        return partner;
    }

    private static BlockPos findTradePoint(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-24, -8, -24), center.offset(24, 8, 24))) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.BELL)) continue;
            int distance = (int)pos.distSqr(center);
            if (distance < bestDistance) { best = pos.immutable(); bestDistance = distance; }
        }
        return best == null ? null : best.immutable();
    }

    private static BlockPos findSurface(ServerLevel level, BlockPos rough) {
        if (!level.hasChunkAt(rough)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rough.getX(), rough.getZ());
        BlockPos feet = new BlockPos(rough.getX(), y, rough.getZ());
        if (!level.hasChunkAt(feet) || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                || !level.getFluidState(feet).isEmpty()) return null;
        return feet;
    }


    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager trader)
                || !trader.getPersistentData().getBoolean("civitas.trader")
                || !(trader.level() instanceof ServerLevel level)) return;
        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        for (int i = data.getActiveTradeCaravans().size() - 1; i >= 0; i--) {
            TradeCaravan caravan = data.getActiveTradeCaravans().get(i);
            if (!caravan.traderId().equals(trader.getUUID())) continue;
            data.addTradeReputation(caravan.partnerId(), -10);
            data.recordTrade("Торговец погиб: репутация партнёра −10");
            data.getActiveTradeCaravans().remove(i);
            data.setNextCaravanTick(level.getGameTime() + 24000L);
            data.markTradeDataDirty();
        }
    }

    private static String partnerName(CivitasSavedData data, UUID id) {
        TradePartner partner = data.getTradePartner(id);
        return partner == null ? "неизвестный партнёр" : partner.name();
    }
}
