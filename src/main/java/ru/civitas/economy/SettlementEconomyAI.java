package ru.civitas.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import ru.civitas.CivitasConfig;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.data.StorageFoodExtension;

import java.util.List;

/** Daily server-side food upkeep, growth, and economy history. */
public final class SettlementEconomyAI {
    private static final int TICKS_PER_DAY = 24_000;
    private static final int MAX_BEDS_SCAN = 24;
    private static final List<Item> WOOD_ITEMS = List.of(Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG,
            Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
            Items.CRIMSON_STEM, Items.WARPED_STEM);
    private static final List<Item> STONE_ITEMS = List.of(Items.COBBLESTONE, Items.STONE, Items.DEEPSLATE,
            Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE);

    private SettlementEconomyAI() { }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 0) return;

        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.hasStarterVillage()) return;
        BlockPos center = data.getStarterVillageCenter();
        if (!level.hasChunkAt(center)
                || !(level.getBlockEntity(center) instanceof SettlementWarehouseBlockEntity warehouse)) return;

        long day = Math.max(0L, level.getDayTime() / TICKS_PER_DAY);
        long lastDay = data.getLastEconomyDay();
        if (lastDay < 0) {
            data.setLastEconomyDay(day);
            recordDailySnapshot(level, data, warehouse, day);
            return;
        }
        if (day < lastDay) {
            data.setLastEconomyDay(day);
            data.setGrowthProgressDays(0);
            data.getEconomyHistory().recordEvent(day, "Время мира изменено; дневной цикл переинициализирован");
            data.setDirty();
            return;
        }
        if (day == lastDay) return;

        long firstDay = Math.max(lastDay + 1, day - 29);
        for (long processedDay = firstDay; processedDay <= day; processedDay++) {
            processDay(level, data, warehouse, center, processedDay);
            data.setLastEconomyDay(processedDay);
        }
    }

    private static void processDay(ServerLevel level, CivitasSavedData data,
                                   SettlementWarehouseBlockEntity warehouse, BlockPos center, long day) {
        int residents = countResidents(level, center);
        data.setPopulation(residents);
        int dailyNeed = (int) Math.min(Integer.MAX_VALUE,
                (long) residents * CivitasConfig.FOOD_PER_DAY.get());

        boolean fed = false;
        int consumed = 0;
        if (residents > 0 && dailyNeed > 0) {
            consumed = consumeDailyNeed(data, warehouse, dailyNeed);
            fed = consumed >= dailyNeed;
            data.getEconomyHistory().recordFoodConsumption(day, consumed);
            if (fed) {
                if (data.getHungerDays() > 0) {
                    data.getEconomyHistory().recordEvent(day, "Запасы еды восстановлены; голод закончился");
                }
                data.setHungerDays(0);
                data.setGrowthProgressDays(Math.min(CivitasConfig.GROWTH_INTERVAL_DAYS.get(),
                        data.getGrowthProgressDays() + 1));
            } else {
                if (data.getHungerDays() == 0) {
                    data.getEconomyHistory().recordEvent(day, "Голод: не хватает "
                            + Math.max(0, dailyNeed - consumed) + " ед. еды");
                }
                data.setHungerDays(data.getHungerDays() + 1);
                data.setGrowthProgressDays(0);
            }
        } else {
            data.setHungerDays(0);
            data.setGrowthProgressDays(0);
        }

        int foodAfterUpkeep = StorageFoodExtension.totalFoodValue(warehouse);
        int beds = countBeds(level, center);
        int interval = CivitasConfig.GROWTH_INTERVAL_DAYS.get();
        long reserveNeed = (long) dailyNeed * CivitasConfig.GROWTH_FOOD_RESERVE.get();
        foodAfterUpkeep = (int) Math.min(Integer.MAX_VALUE, (long) foodAfterUpkeep + data.getFoodCredit());
        if (residents > 0 && fed && data.getGrowthProgressDays() >= interval
                && beds > residents && foodAfterUpkeep >= reserveNeed) {
            BlockPos spawn = findSpawnPosition(level, center);
            if (spawn != null) {
                Villager newcomer = EntityType.VILLAGER.create(level);
                if (newcomer != null) {
                    newcomer.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                            level.random.nextFloat() * 360.0F, 0.0F);
                    String[] names = {"Алексей", "Мирон", "Илья", "Вера", "Анна", "Марта", "Лев", "Ева", "Никита", "София"};
                    newcomer.setCustomName(Component.literal(names[level.random.nextInt(names.length)]));
                    newcomer.setPersistenceRequired();
                    if (level.addFreshEntity(newcomer)) {
                        residents++;
                        data.setPopulation(residents);
                        data.setGrowthProgressDays(0);
                        data.getEconomyHistory().recordEvent(day, "В деревне появился новый житель");
                    }
                }
            }
        }

        recordDailySnapshot(level, data, warehouse, day);
        data.setDirty();
    }

    /** Uses any saved nutrition credit first and retains over-consumed value for following days. */
    private static int consumeDailyNeed(CivitasSavedData data, SettlementWarehouseBlockEntity warehouse, int need) {
        int credit = data.getFoodCredit();
        int remainingNeed = Math.max(0, need - Math.min(credit, need));
        int removed = remainingNeed > 0 ? StorageFoodExtension.consumeFood(warehouse, remainingNeed) : 0;
        SettlementEconomyRules.FoodConsumptionResult result =
                SettlementEconomyRules.resolveFoodConsumption(credit, removed, need);
        data.setFoodCredit(result.nextCredit());
        return result.suppliedNutrition();
    }

    private static void recordDailySnapshot(ServerLevel level, CivitasSavedData data,
                                            SettlementWarehouseBlockEntity warehouse, long day) {
        int residents = countResidents(level, data.getStarterVillageCenter());
        data.setPopulation(residents);
        data.getEconomyHistory().recordDaily(day,
                countItems(warehouse, WOOD_ITEMS),
                countItems(warehouse, STONE_ITEMS),
                (int) Math.min(Integer.MAX_VALUE,
                        (long) StorageFoodExtension.totalFoodValue(warehouse) + data.getFoodCredit()),
                residents);
        data.setDirty();
    }

    public static int countResidents(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(Villager.class,
                new AABB(center).inflate(MAX_BEDS_SCAN, 12, MAX_BEDS_SCAN)).size();
    }

    public static int countBeds(ServerLevel level, BlockPos center) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-MAX_BEDS_SCAN, -8, -MAX_BEDS_SCAN),
                center.offset(MAX_BEDS_SCAN, 8, MAX_BEDS_SCAN))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) count++;
        }
        return count;
    }

    public static int countItem(SettlementWarehouseBlockEntity warehouse, Item item) {
        if (warehouse == null) return 0;
        int total = 0;
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            var stack = warehouse.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total = (int) Math.min(Integer.MAX_VALUE, (long) total + stack.getCount());
            }
        }
        return total;
    }

    private static int countItems(SettlementWarehouseBlockEntity warehouse, List<Item> items) {
        long total = 0L;
        for (Item item : items) total += countItem(warehouse, item);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos center) {
        for (BlockPos bed : BlockPos.betweenClosed(center.offset(-MAX_BEDS_SCAN, -8, -MAX_BEDS_SCAN),
                center.offset(MAX_BEDS_SCAN, 8, MAX_BEDS_SCAN))) {
            if (!level.hasChunkAt(bed)) continue;
            BlockState state = level.getBlockState(bed);
            if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                int x = bed.getX() + direction.getStepX();
                int z = bed.getZ() + direction.getStepZ();
                BlockPos horizontal = new BlockPos(x, bed.getY(), z);
                if (!level.hasChunkAt(horizontal)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                BlockPos head = feet.above();
                BlockPos floor = feet.below();
                if (!level.hasChunkAt(head) || !level.hasChunkAt(floor)) continue;
                BlockState feetState = level.getBlockState(feet);
                BlockState headState = level.getBlockState(head);
                if ((!feetState.isAir() && !feetState.canBeReplaced())
                        || (!headState.isAir() && !headState.canBeReplaced())
                        || !feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()
                        || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                        || !level.getFluidState(floor).isEmpty()) continue;
                AABB occupied = new AABB(feet).inflate(0.8D, 1.5D, 0.8D);
                if (!level.getEntitiesOfClass(Villager.class, occupied).isEmpty()) continue;
                return feet.immutable();
            }
        }
        return null;
    }
}
