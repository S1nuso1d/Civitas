package ru.civitas.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent, server-authoritative direct orders for individual villagers. */
public final class PersonalOrders {
    public enum Order { NONE, FOLLOW, STAY, GO_HOME, GUARD }

    private static final String ORDER_KEY = "civitas.personal_order";
    private static final String TARGET_KEY = "civitas.personal_order_target";
    private static final String NEXT_UPDATE_KEY = "civitas.personal_order_next_update";
    private static final String BED_TARGET_KEY = "civitas.personal_order_bed_target";
    private static final String BED_SCAN_KEY = "civitas.personal_order_bed_scan";
    private static final String ISSUE_KEY = "civitas.worker_issue";

    private PersonalOrders() { }

    public static Order get(Villager villager) {
        try {
            return Order.valueOf(villager.getPersistentData().getString(ORDER_KEY));
        } catch (IllegalArgumentException ignored) {
            return Order.NONE;
        }
    }

    public static void set(Villager villager, Order order, BlockPos target) {
        var tag = villager.getPersistentData();
        tag.putString(ORDER_KEY, order.name());
        if (target != null) tag.putLong(TARGET_KEY, target.asLong());
        else tag.remove(TARGET_KEY);
        tag.remove(NEXT_UPDATE_KEY);
        tag.remove(BED_TARGET_KEY);
        tag.remove(BED_SCAN_KEY);
        villager.getNavigation().stop();
        tag.putString(ISSUE_KEY, status(order));
    }

    public static boolean tickOverride(ServerLevel level, Villager villager) {
        Order order = get(villager);
        if (order == Order.NONE) return false;

        var tag = villager.getPersistentData();
        long now = level.getGameTime();
        if (now < tag.getLong(NEXT_UPDATE_KEY)) return true;
        tag.putLong(NEXT_UPDATE_KEY, now + 10L);

        switch (order) {
            case FOLLOW -> {
                Player player = level.getNearestPlayer(villager, 64.0D);
                if (player == null) {
                    villager.getNavigation().stop();
                    tag.putString(ISSUE_KEY, "Ожидает игрока");
                } else if (villager.distanceToSqr(player) > 9.0D) {
                    villager.getNavigation().moveTo(player, 1.1D);
                    tag.putString(ISSUE_KEY, "Следует за игроком");
                } else {
                    villager.getNavigation().stop();
                    tag.putString(ISSUE_KEY, "Рядом с игроком");
                }
            }
            case STAY -> {
                villager.getNavigation().stop();
                tag.putString(ISSUE_KEY, "Стоит на месте");
            }
            case GO_HOME -> {
                BlockPos bed = tag.contains(BED_TARGET_KEY) ? BlockPos.of(tag.getLong(BED_TARGET_KEY)) : null;
                if (bed == null || !level.hasChunkAt(bed) || !(level.getBlockState(bed).getBlock() instanceof BedBlock)) {
                    if (now >= tag.getLong(BED_SCAN_KEY)) {
                        bed = findNearbyBed(level, villager.blockPosition(), 24);
                        tag.putLong(BED_SCAN_KEY, now + 200L);
                        if (bed != null) tag.putLong(BED_TARGET_KEY, bed.asLong());
                        else tag.remove(BED_TARGET_KEY);
                    }
                }
                if (bed == null) {
                    villager.getNavigation().stop();
                    tag.putString(ISSUE_KEY, "Кровать не найдена");
                } else if (villager.blockPosition().distSqr(bed) > 4.0D) {
                    villager.getNavigation().moveTo(bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D, 1.0D);
                    tag.putString(ISSUE_KEY, "Идёт домой");
                } else {
                    villager.getNavigation().stop();
                    tag.putString(ISSUE_KEY, "Отдыхает дома");
                }
            }
            case GUARD -> {
                if (!tag.contains(TARGET_KEY)) {
                    villager.getNavigation().stop();
                    tag.putString(ISSUE_KEY, "Точка охраны не задана");
                    break;
                }
                BlockPos target = BlockPos.of(tag.getLong(TARGET_KEY));
                if (villager.blockPosition().distSqr(target) > 4.0D) {
                    villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
                } else {
                    villager.getNavigation().stop();
                    villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
                }
                tag.putString(ISSUE_KEY, "Охраняет точку");
            }
            default -> { return false; }
        }
        return true;
    }

    private static BlockPos findNearbyBed(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BedBlock)) continue;
                    double distance = pos.distSqr(center);
                    if (distance < bestDistance) {
                        best = pos.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static String status(Order order) {
        return switch (order) {
            case NONE -> "Работает по профессии";
            case FOLLOW -> "Следует за игроком";
            case STAY -> "Стоит на месте";
            case GO_HOME -> "Идёт домой";
            case GUARD -> "Охраняет точку";
        };
    }
}
