package ru.civitas.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;

import java.util.List;

/** Daily food consumption and conditional population growth for the starter settlement. */
public final class SettlementEconomyAI {
    private static final int TICKS_PER_DAY = 24_000;
    private static final int GROWTH_DAYS_REQUIRED = 3;
    private static final int MAX_BEDS_SCAN = 24;

    private static final List<Item> FOOD = List.of(
            Items.BREAD, Items.BAKED_POTATO, Items.POTATO, Items.CARROT, Items.BEETROOT
    );

    private SettlementEconomyAI() { }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 0) return;

        CivitasSavedData data = CivitasSavedData.get(level.getServer());
        if (!data.hasStarterVillage()) return;
        long day = Math.max(0L, level.getDayTime() / TICKS_PER_DAY);
        if (data.getLastEconomyDay() < 0L) {
            data.setLastEconomyDay(day);
            return;
        }
        if (day <= data.getLastEconomyDay()) return;

        // Process each elapsed day at most once per tick; normal gameplay advances one day at a time.
        data.setLastEconomyDay(day);
        BlockPos center = data.getStarterVillageCenter();
        if (!level.hasChunkAt(center)
                || !(level.getBlockEntity(center) instanceof SettlementWarehouseBlockEntity warehouse)) return;

        AABB area = new AABB(center).inflate(MAX_BEDS_SCAN, 12, MAX_BEDS_SCAN);
        int residents = level.getEntitiesOfClass(Villager.class, area).size();
        data.setPopulation(residents);
        int availableFood = countFood(warehouse);
        int dailyNeed = Math.max(1, residents);
        boolean fed = availableFood >= dailyNeed;
        if (fed) {
            removeFood(warehouse, dailyNeed);
            data.setHungerDays(0);
            data.setGrowthProgressDays(data.getGrowthProgressDays() + 1);
        } else {
            removeFood(warehouse, availableFood);
            data.setHungerDays(data.getHungerDays() + 1);
            data.setGrowthProgressDays(0);
        }

        if (fed && data.getGrowthProgressDays() >= GROWTH_DAYS_REQUIRED) {
            int beds = countBeds(level, center);
            if (beds > residents) {
                BlockPos spawn = findSpawnPosition(level, center);
                if (spawn != null) {
                    Villager newcomer = EntityType.VILLAGER.create(level);
                    if (newcomer != null) {
                        newcomer.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                                level.random.nextFloat() * 360.0F, 0.0F);
                        newcomer.setPersistenceRequired();
                        if (level.addFreshEntity(newcomer)) {
                            data.setPopulation(residents + 1);
                            data.setGrowthProgressDays(0);
                        }
                    }
                }
            }
        }
    }

    public static int countFood(SettlementWarehouseBlockEntity warehouse) {
        int total = 0;
        for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
            ItemStack stack = warehouse.getItem(slot);
            if (!stack.isEmpty() && FOOD.contains(stack.getItem())) {
                total = (int) Math.min(Integer.MAX_VALUE, (long) total + stack.getCount());
            }
        }
        return total;
    }

    private static void removeFood(SettlementWarehouseBlockEntity warehouse, int amount) {
        int remaining = Math.max(0, amount);
        for (Item food : FOOD) {
            for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = warehouse.getItem(slot);
                if (stack.isEmpty() || stack.getItem() != food) continue;
                int moved = Math.min(remaining, stack.getCount());
                stack.shrink(moved);
                remaining -= moved;
                if (stack.isEmpty()) warehouse.setItem(slot, ItemStack.EMPTY);
                else warehouse.setChanged();
            }
            if (remaining == 0) return;
        }
    }

    private static int countBeds(ServerLevel level, BlockPos center) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-MAX_BEDS_SCAN, -8, -MAX_BEDS_SCAN),
                center.offset(MAX_BEDS_SCAN, 8, MAX_BEDS_SCAN))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) count++;
        }
        return count;
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
                if (!level.hasChunkAt(new BlockPos(x, bed.getY(), z))) continue;
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
