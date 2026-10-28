package ru.civitas.worker;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small, server-authoritative presentation and schedule helpers shared by Civitas workers.
 * Effects are emitted only during the existing 20-tick work cycle.
 */
public final class WorkerLifeCycle {
    private static final String NIGHT_KEY = "civitas.worker_night_rest";
    private static final String LAST_CHAT_KEY = "civitas.worker_last_chat";
    private static final String LAST_SOUND_KEY = "civitas.worker_last_sound";
    private static final ResourceLocation ROAD_SPEED_ID = ResourceLocation.fromNamespaceAndPath("civitas", "road_speed");
    private static final Set<Block> ROAD_BLOCKS = Set.of(Blocks.DIRT_PATH, Blocks.GRAVEL, Blocks.STONE_BRICKS,
            Blocks.COBBLESTONE, Blocks.POLISHED_ANDESITE, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);
    private static final Set<String> ROLES = Set.of("lumberjack", "builder", "farmer");
    private static final Map<String, String[]> CHAT = Map.of(
            "lumberjack", new String[]{"Крепкое дерево попалось.", "Ещё немного — и на склад.", "Работы в лесу хватает."},
            "builder", new String[]{"Этот дом будет крепким.", "Работа за работой — и деревня растёт.", "Надо всё сделать аккуратно."},
            "farmer", new String[]{"Хороший урожай будет.", "Пора собрать созревшее.", "Земля сегодня щедрая."}
    );

    private WorkerLifeCycle() { }

    /** Adds one stable +35% base-speed modifier on roads and removes it elsewhere. */
    public static void updateRoadSpeed(Villager villager) {
        AttributeInstance speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        BlockPos below = villager.blockPosition().below();
        boolean assignedWorker = ROLES.contains(villager.getPersistentData().getString("civitas.worker_role"));
        boolean onRoad = assignedWorker && villager.level().hasChunkAt(below)
                && ROAD_BLOCKS.contains(villager.level().getBlockState(below).getBlock());
        boolean hasModifier = speed.hasModifier(ROAD_SPEED_ID);
        if (onRoad && !hasModifier) {
            speed.addTransientModifier(new AttributeModifier(ROAD_SPEED_ID, 0.35D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!onRoad && hasModifier) {
            speed.removeModifier(ROAD_SPEED_ID);
        }
    }

    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            String role = villager.getPersistentData().getString("civitas.worker_role");
            AttributeInstance speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
            if (ROLES.contains(role) || (speed != null && speed.hasModifier(ROAD_SPEED_ID))) {
                updateRoadSpeed(villager);
            }
        }
    }

    public static boolean isNight(ServerLevel level) {
        long time = Math.floorMod(level.getDayTime(), 24_000L);
        return time >= 13_000L && time < 23_000L;
    }

    /** Returns true while a worker should rest; existing target and cargo data are untouched. */
    public static boolean shouldRest(ServerLevel level, Villager villager, String role) {
        boolean night = isNight(level);
        var tag = villager.getPersistentData();
        boolean wasNight = tag.getBoolean(NIGHT_KEY);
        if (night) {
            if (!wasNight) {
                tag.putBoolean(NIGHT_KEY, true);
                villager.getNavigation().stop();
            }
            if (villager.tickCount % 100 == 0) {
                villager.setCustomName(Component.literal(roleName(role) + " — отдыхает (ночь)"));
                villager.setCustomNameVisible(true);
            }
            return true;
        }
        if (wasNight) {
            tag.putBoolean(NIGHT_KEY, false);
            tag.remove(LAST_CHAT_KEY);
            // Worker AI reuses its persisted target on the next regular update.
        }
        return false;
    }

    /** Opens only an adjacent, unpowered door while moving and closes it after passage. */
    public static void openNearbyDoor(ServerLevel level, Villager villager) {
        closeTrackedDoor(level, villager);
        if (villager.getNavigation().isDone()) return;
        BlockPos origin = villager.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = origin.relative(direction);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.OPEN)
                    || state.getValue(DoorBlock.POWERED)) continue;
            level.setBlock(pos, state.setValue(DoorBlock.OPEN, true), 10);
            var tag = villager.getPersistentData();
            tag.putInt("civitas.worker_door_x", pos.getX());
            tag.putInt("civitas.worker_door_y", pos.getY());
            tag.putInt("civitas.worker_door_z", pos.getZ());
            tag.putLong("civitas.worker_door_opened_at", level.getGameTime());
            level.playSound(null, pos, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.45F, 1.0F);
            break;
        }
    }

    private static void closeTrackedDoor(ServerLevel level, Villager villager) {
        var tag = villager.getPersistentData();
        long openedAt = tag.getLong("civitas.worker_door_opened_at");
        if (openedAt <= 0L || level.getGameTime() - openedAt < 30L) return;
        BlockPos pos = new BlockPos(tag.getInt("civitas.worker_door_x"),
                tag.getInt("civitas.worker_door_y"), tag.getInt("civitas.worker_door_z"));
        if (!level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock) || !state.getValue(DoorBlock.OPEN)) {
            tag.remove("civitas.worker_door_opened_at");
            return;
        }
        if (villager.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) <= 4.0D
                || !level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(pos).inflate(1.5D),
                        other -> other != villager).isEmpty()) return;
        if (!state.getValue(DoorBlock.POWERED)) {
            level.setBlock(pos, state.setValue(DoorBlock.OPEN, false), 10);
        }
        tag.remove("civitas.worker_door_opened_at");
    }

    public static int progressIncrement(ServerLevel level, Villager villager, String role, int normal) {
        if ("farmer".equals(role) && level.isRainingAt(villager.blockPosition())) {
            return Math.max(1, (int) Math.floor(normal * 0.75D));
        }
        return normal;
    }

    public static void workFeedback(ServerLevel level, Villager villager, String role, BlockPos target) {
        if (target == null || !level.hasChunkAt(target)) return;
        var tag = villager.getPersistentData();
        long now = level.getGameTime();
        if (now - tag.getLong(LAST_SOUND_KEY) >= 20L) {
            SoundEvent sound = switch (role) {
                case "lumberjack" -> SoundEvents.WOOD_HIT;
                case "builder" -> SoundEvents.STONE_PLACE;
                default -> SoundEvents.CROP_BREAK;
            };
            level.playSound(null, target, sound, SoundSource.BLOCKS, 0.45F,
                    0.9F + level.random.nextFloat() * 0.2F);
            tag.putLong(LAST_SOUND_KEY, now);
        }
        if (now % 20L == 0L) {
            switch (role) {
                case "lumberjack" -> {
                    level.sendParticles(ParticleTypes.CRIT, target.getX() + 0.5D, target.getY() + 0.5D,
                            target.getZ() + 0.5D, 2, 0.22D, 0.22D, 0.22D, 0.03D);
                    level.sendParticles(ParticleTypes.POOF, target.getX() + 0.5D, target.getY() + 0.4D,
                            target.getZ() + 0.5D, 1, 0.15D, 0.15D, 0.15D, 0.01D);
                }
                case "builder" -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                        2, 0.18D, 0.18D, 0.18D, 0.01D);
                default -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                        2, 0.2D, 0.2D, 0.2D, 0.0D);
            }
        }
        if (now - tag.getLong(LAST_CHAT_KEY) >= 600L && level.random.nextFloat() < 0.04F) {
            var players = level.players();
            if (!players.isEmpty()) {
                var line = CHAT.get(role);
                if (line != null) {
                    var player = players.stream().filter(p -> p.distanceToSqr(villager) <= 64.0D).findFirst().orElse(null);
                    if (player != null) {
                        player.displayClientMessage(Component.literal("<" + villager.getName().getString() + "> "
                                + line[level.random.nextInt(line.length)]), false);
                        tag.putLong(LAST_CHAT_KEY, now);
                    }
                }
            }
        }
    }

    private static String roleName(String role) {
        return switch (role) {
            case "lumberjack" -> "Лесоруб Civitas";
            case "builder" -> "Строитель Civitas";
            case "farmer" -> "Фермер Civitas";
            default -> "Рабочий Civitas";
        };
    }
}
