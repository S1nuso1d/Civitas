package ru.civitas.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import ru.civitas.block.SettlementWarehouseBlockEntity;
import ru.civitas.data.CivitasSavedData;

/**
 * First physical worker prototype. A tagged village villager walks to nearby
 * natural tree logs, chops the whole connected tree with visible timed progress,
 * and physically returns to the starter warehouse to deposit the complete harvest.
 */
public final class LumberjackWorkerAI {
    private static final String ROLE_KEY = "civitas.worker_role";
    private static final String CARRIED_ITEM_KEY = "civitas.worker_carried_item";
    private static final String CARRIED_COUNT_KEY = "civitas.worker_carried_count";
    private static final String CHOP_PROGRESS_KEY = "civitas.worker_chop_progress";
    private static final String PAUSED_KEY = "civitas.worker_paused";
    private static final String ISSUE_KEY = "civitas.worker_issue";
    private static final String TARGET_TREE_ASSIGNED_KEY = "civitas.worker_tree_target_assigned";
    private static final String TARGET_TREE_X_KEY = "civitas.worker_tree_target_x";
    private static final String TARGET_TREE_Y_KEY = "civitas.worker_tree_target_y";
    private static final String TARGET_TREE_Z_KEY = "civitas.worker_tree_target_z";
    private static final String TARGET_TREE_SIZE_KEY = "civitas.worker_tree_target_size";
    private static final String WORK_POS_ASSIGNED_KEY = "civitas.worker_chop_position_assigned";
    private static final String WORK_POS_X_KEY = "civitas.worker_chop_position_x";
    private static final String WORK_POS_Y_KEY = "civitas.worker_chop_position_y";
    private static final String WORK_POS_Z_KEY = "civitas.worker_chop_position_z";
    private static final int MAX_CARRIED = 8;
    private static final int WORK_INTERVAL = 20;
    private static final int TREE_SEARCH_RADIUS = 18;
    private static final int STARTER_VILLAGE_PROTECTION_RADIUS = 14;
    private static final int MAX_TREE_LOGS = 512;

    private LumberjackWorkerAI() {
    }

    /**
     * Assign one resident as lumberjack. This also upgrades older worlds without
     * rebuilding their starter village.
     */
    public static void ensureWorkerExists(ServerLevel level, BlockPos villageCenter) {
        AABB area = new AABB(
                villageCenter.getX() - 24, villageCenter.getY() - 12, villageCenter.getZ() - 24,
                villageCenter.getX() + 24, villageCenter.getY() + 12, villageCenter.getZ() + 24
        );
        java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        Villager chosen = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Villager villager : villagers) {
            if ("lumberjack".equals(villager.getPersistentData().getString(ROLE_KEY))) {
                return;
            }
            double distance = villager.distanceToSqr(
                    villageCenter.getX() + 0.5D, villageCenter.getY() + 1.0D, villageCenter.getZ() + 0.5D);
            if (distance < nearestDistance) {
                chosen = villager;
                nearestDistance = distance;
            }
        }
        if (chosen == null) {
            chosen = EntityType.VILLAGER.create(level);
            if (chosen == null) {
                return;
            }
            chosen.moveTo(villageCenter.getX() + 0.5D, villageCenter.getY() + 1.0D,
                    villageCenter.getZ() + 2.5D, 0.0F, 0.0F);
            level.addFreshEntity(chosen);
        }
        chosen.getPersistentData().putString(ROLE_KEY, "lumberjack");
        chosen.setCustomName(Component.literal("Лесоруб Civitas"));
        chosen.setCustomNameVisible(true);
        chosen.setPersistenceRequired();
    }

    /** Returns the Civitas role currently assigned to a villager, or an empty string. */
    public static String getWorkerRole(Villager villager) {
        return villager.getPersistentData().getString(ROLE_KEY);
    }

    /** Human-readable activity, cargo, pause state and last blocking issue. */
    public static String getWorkerStatus(Villager villager) {
        var persistent = villager.getPersistentData();
        String role = getWorkerRole(villager);
        if (persistent.getBoolean(PAUSED_KEY) && "lumberjack".equals(role)) return "Приостановлен";
        if ("builder".equals(role)) {
            String blocked = persistent.getString("civitas.builder_blocked_reason");
            if (!blocked.isBlank()) return "Стройка ожидает: " + blocked;
            int buildTicks = Math.max(0, persistent.getInt("civitas.builder_work_progress_ticks"));
            if (buildTicks > 0) return "Строит: " + Math.min(3, buildTicks / 20) + "/3 сек";
            if (persistent.getBoolean("civitas.builder_target_assigned")) {
                return villager.getNavigation().isDone() ? "Готовится к строительству" : "Идёт на площадку";
            }
            return "Ожидает заказ строительства";
        }
        String issue = persistent.getString(ISSUE_KEY);
        if (!issue.isBlank()) return issue;
        int carried = Math.max(0, persistent.getInt(CARRIED_COUNT_KEY));
        if (carried > 0) {
            return carried >= MAX_CARRIED
                    ? "Несёт брёвна: " + carried + " (доставка)"
                    : "Несёт брёвна: " + carried + "/" + MAX_CARRIED;
        }
        if (persistent.getInt(CHOP_PROGRESS_KEY) > 0) {
            int progress = persistent.getInt(CHOP_PROGRESS_KEY);
            int total = Math.max(progress, persistent.getInt(TARGET_TREE_SIZE_KEY));
            return "Рубит дерево: " + progress + "/" + total + " сек";
        }
        if (!"lumberjack".equals(getWorkerRole(villager))) return "Нет задачи";
        return villager.getNavigation().isDone() ? "Ищет дерево" : "Идёт к дереву";
    }

    public static boolean setLumberjackPaused(ServerLevel level, BlockPos villageCenter, boolean paused) {
        AABB area = new AABB(villageCenter).inflate(24, 12, 24);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, area)) {
            if ("lumberjack".equals(getWorkerRole(villager))) {
                return setLumberjackPaused(villager, paused);
            }
        }
        return false;
    }

    /** Pauses or resumes the explicitly selected lumberjack without changing any other resident. */
    public static boolean setLumberjackPaused(Villager villager, boolean paused) {
        if (villager == null || !"lumberjack".equals(getWorkerRole(villager))) return false;
        var persistent = villager.getPersistentData();
        if (persistent.getBoolean(PAUSED_KEY) == paused) return true;
        persistent.putBoolean(PAUSED_KEY, paused);
        if (paused) {
            villager.getNavigation().stop();
            updateWorkerName(villager, " — пауза");
        } else {
            persistent.putString(ISSUE_KEY, "");
            updateWorkerName(villager, "");
        }
        return true;
    }

    /** Assigns the explicitly selected villager, without stealing another worker's active cargo. */
    public static boolean assignLumberjackTo(ServerLevel level, BlockPos villageCenter, Villager chosen) {
        if (chosen == null || chosen.level() != level) return false;
        if ("lumberjack".equals(getWorkerRole(chosen))
                && chosen.getPersistentData().getInt(CARRIED_COUNT_KEY) > 0) {
            return true;
        }

        AABB area = new AABB(villageCenter).inflate(24, 12, 24);
        if (!area.contains(chosen.getX(), chosen.getY(), chosen.getZ())) return false;
        java.util.List<Villager> residents = level.getEntitiesOfClass(Villager.class, area);
        for (Villager resident : residents) {
            if (resident != chosen && "lumberjack".equals(getWorkerRole(resident))
                    && resident.getPersistentData().getInt(CARRIED_COUNT_KEY) > 0) {
                return false;
            }
        }

        // Keep a single lumberjack until multi-worker forestry is added. A
        // worker carrying logs cannot be reassigned before its delivery.
        for (Villager resident : residents) {
            if (resident != chosen && "lumberjack".equals(getWorkerRole(resident))) {
                releaseLumberjackRole(resident);
            }
        }
        if ("builder".equals(getWorkerRole(chosen))) {
            ConstructionWorkerAI.releaseBuilderRole(chosen);
        }
        if ("farmer".equals(getWorkerRole(chosen))
                && !FarmerWorkerAI.releaseFarmerRole(chosen)) {
            return false;
        }
        clearTreeTarget(chosen.getPersistentData());
        chosen.getPersistentData().putInt(CHOP_PROGRESS_KEY, 0);
        chosen.getPersistentData().putString(ISSUE_KEY, "");
        chosen.getPersistentData().putBoolean(PAUSED_KEY, false);
        WorkerNavigationGuard.reset(chosen, "lumberjack");
        chosen.getPersistentData().putString(ROLE_KEY, "lumberjack");
        updateWorkerName(chosen, "");
        return true;
    }

    /** Releases an idle lumberjack, preserving cargo that has not been delivered. */
    public static boolean releaseLumberjackRole(Villager villager) {
        if (villager == null) return false;
        var persistent = villager.getPersistentData();
        if ("lumberjack".equals(getWorkerRole(villager))
                && persistent.getInt(CARRIED_COUNT_KEY) > 0) {
            return false;
        }
        persistent.putString(ROLE_KEY, "");
        persistent.putBoolean(PAUSED_KEY, false);
        persistent.putString(ISSUE_KEY, "");
        persistent.putInt(CHOP_PROGRESS_KEY, 0);
        clearTreeTarget(persistent);
        villager.getNavigation().stop();
        if (villager.getCustomName() != null
                && villager.getCustomName().getString().startsWith("Лесоруб Civitas")) {
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
        }
        return true;
    }

    public static boolean clearLumberjackRole(ServerLevel level, BlockPos villageCenter) {
        AABB area = new AABB(villageCenter).inflate(24, 12, 24);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, area)) {
            if ("lumberjack".equals(getWorkerRole(villager))) {
                return releaseLumberjackRole(villager);
            }
        }
        return false;
    }

    /** Keeps the existing one-click command while preferring residents without another job. */
    public static boolean assignNearestLumberjack(ServerLevel level, BlockPos villageCenter) {
        AABB area = new AABB(villageCenter).inflate(24, 12, 24);
        java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        if (villagers.isEmpty()) {
            ensureWorkerExists(level, villageCenter);
            villagers = level.getEntitiesOfClass(Villager.class, area);
        }
        if (villagers.isEmpty()) return false;

        for (Villager villager : villagers) {
            if ("lumberjack".equals(getWorkerRole(villager))
                    && villager.getPersistentData().getInt(CARRIED_COUNT_KEY) > 0) {
                return true;
            }
        }

        Villager chosen = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Villager villager : villagers) {
            String role = getWorkerRole(villager);
            if (!role.isBlank() && !"lumberjack".equals(role)) continue;
            double distance = villager.distanceToSqr(
                    villageCenter.getX() + 0.5D, villageCenter.getY() + 1.0D,
                    villageCenter.getZ() + 0.5D);
            if (distance < nearestDistance) {
                chosen = villager;
                nearestDistance = distance;
            }
        }
        return chosen != null && assignLumberjackTo(level, villageCenter, chosen);
    }

    /**
     * Recovers a lumberjack's virtual cargo on death: use warehouse capacity first,
     * then materialize any remainder as ordinary item entities at the death location.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        var persistent = villager.getPersistentData();
        int carried = Math.max(0, persistent.getInt(CARRIED_COUNT_KEY));
        String itemId = persistent.getString(CARRIED_ITEM_KEY);
        if (carried <= 0 || itemId.isBlank()) {
            return;
        }

        if (level.dimension() == Level.OVERWORLD) {
            CivitasSavedData worldData = CivitasSavedData.get(level.getServer());
            if (worldData.hasStarterVillage()) {
                BlockPos warehousePos = worldData.getStarterVillageCenter();
                if (level.getBlockEntity(warehousePos) instanceof SettlementWarehouseBlockEntity warehouse) {
                    depositCarriedLogs(warehouse, persistent, itemId, carried);
                    carried = Math.max(0, persistent.getInt(CARRIED_COUNT_KEY));
                }
            }
        }

        if (carried <= 0) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            // Do not turn corrupt saved item identifiers into an arbitrary item.
            persistent.putInt(CARRIED_COUNT_KEY, 0);
            persistent.putString(CARRIED_ITEM_KEY, "");
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR) {
            persistent.putInt(CARRIED_COUNT_KEY, 0);
            persistent.putString(CARRIED_ITEM_KEY, "");
            return;
        }

        int remaining = carried;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemEntity drop = new ItemEntity(level, villager.getX(), villager.getY() + 0.25D,
                    villager.getZ(), new ItemStack(item, stackSize));
            if (!level.addFreshEntity(drop)) {
                break;
            }
            remaining -= stackSize;
        }
        persistent.putInt(CARRIED_COUNT_KEY, remaining);
        persistent.putString(CARRIED_ITEM_KEY, remaining == 0 ? "" : itemId);
    }

    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }
        if (!"lumberjack".equals(villager.getPersistentData().getString(ROLE_KEY))) {
            return;
        }
        if (villager.getPersistentData().getBoolean(PAUSED_KEY)) {
            villager.getNavigation().stop();
            return;
        }
        if (villager.tickCount % WORK_INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        tickWorker(level, villager);
    }

    private static void tickWorker(ServerLevel level, Villager villager) {
        CivitasSavedData worldData = CivitasSavedData.get(level.getServer());
        if (!worldData.hasStarterVillage() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        BlockPos warehousePos = worldData.getStarterVillageCenter();
        if (!(level.getBlockEntity(warehousePos) instanceof SettlementWarehouseBlockEntity warehouse)) {
            villager.getPersistentData().putString(ISSUE_KEY, "Склад не найден");
            villager.getNavigation().stop();
            updateWorkerName(villager, " — нет склада");
            return;
        }

        var persistent = villager.getPersistentData();
        String carriedItemId = persistent.getString(CARRIED_ITEM_KEY);
        int carriedCount = Math.max(0, persistent.getInt(CARRIED_COUNT_KEY));
        if (carriedCount == 0) {
            carriedItemId = "";
            persistent.putString(CARRIED_ITEM_KEY, "");
        }

        // An exhausted path must not silently discard cargo. Deliver the saved
        // load first, then leave an explicit issue until the player reassigns the worker.
        if (WorkerNavigationGuard.isExhausted(villager, "lumberjack")) {
            persistent.putString(ISSUE_KEY,
                    "Маршрут заблокирован после 3 попыток. Переназначь лесоруба для возобновления работы.");
            if (carriedCount > 0) {
                BlockPos safeDeliveryStand = warehousePos.offset(0, 1, 2);
                if (villager.distanceToSqr(safeDeliveryStand.getX() + 0.5D, safeDeliveryStand.getY(),
                        safeDeliveryStand.getZ() + 0.5D) > 5.0D) {
                    villager.getNavigation().moveTo(safeDeliveryStand.getX() + 0.5D, safeDeliveryStand.getY(),
                            safeDeliveryStand.getZ() + 0.5D, 0.9D);
                } else {
                    villager.getNavigation().stop();
                    depositCarriedLogs(warehouse, persistent, carriedItemId, carriedCount);
                }
            } else {
                villager.getNavigation().stop();
            }
            updateWorkerName(villager, " — путь заблокирован");
            return;
        }

        // Keep one tree assigned while the villager walks and chops. Re-scanning
        // for the nearest log every second made the target jump between trunk
        // blocks, so the worker could orbit the tree or abandon a half-finished cut.
        BlockPos treeLog = readTreeTarget(persistent);
        if (treeLog != null && !isHarvestableTarget(level, treeLog, worldData, warehousePos)) {
            clearTreeTarget(persistent);
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            treeLog = null;
        }
        if (treeLog == null && carriedCount < MAX_CARRIED) {
            treeLog = findNearestTreeLog(level, villager.blockPosition(), worldData, warehousePos);
            if (treeLog != null) {
                writeTreeTarget(persistent, treeLog);
            }
        }

        java.util.List<BlockPos> treeLogs = treeLog == null
                ? java.util.List.of()
                : findConnectedTreeLogs(level, treeLog, worldData, warehousePos);
        if (treeLog != null && treeLogs.isEmpty()) {
            clearTreeTarget(persistent);
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            treeLog = null;
        }

        String targetItemId = treeLog == null ? "" :
                BuiltInRegistries.ITEM.getKey(level.getBlockState(treeLog).getBlock().asItem()).toString();
        boolean warehouseBlocked = persistent.getString(ISSUE_KEY).contains("Склад заполнен");
        boolean mustDeliver = warehouseBlocked || carriedCount >= MAX_CARRIED
                || carriedCount > 0 && (treeLog == null || !carriedItemId.equals(targetItemId));

        BlockPos deliveryStand = warehousePos.offset(0, 1, 2);
        if (mustDeliver) {
            updateWorkerName(villager, " — несёт брёвна");
            if (villager.distanceToSqr(deliveryStand.getX() + 0.5D, deliveryStand.getY(),
                    deliveryStand.getZ() + 0.5D) > 5.0D) {
                villager.getNavigation().moveTo(deliveryStand.getX() + 0.5D, deliveryStand.getY(),
                        deliveryStand.getZ() + 0.5D, 0.9D);
                return;
            }
            villager.getNavigation().stop();
            depositCarriedLogs(warehouse, persistent, carriedItemId, carriedCount);
            updateWorkerName(villager, persistent.getInt(CARRIED_COUNT_KEY) > 0
                    ? " — склад заполнен" : "");
            return;
        }

        if (treeLog == null) {
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            persistent.putInt(TARGET_TREE_SIZE_KEY, 0);
            if (carriedCount > 0) {
                updateWorkerName(villager, " — несёт брёвна");
                if (villager.distanceToSqr(deliveryStand.getX() + 0.5D, deliveryStand.getY(),
                        deliveryStand.getZ() + 0.5D) > 5.0D) {
                    villager.getNavigation().moveTo(deliveryStand.getX() + 0.5D, deliveryStand.getY(),
                            deliveryStand.getZ() + 0.5D, 0.9D);
                } else {
                    villager.getNavigation().stop();
                    depositCarriedLogs(warehouse, persistent, carriedItemId, carriedCount);
                }
            } else {
                persistent.putString(ISSUE_KEY, "Нет деревьев поблизости");
                updateWorkerName(villager, " — ищет дерево");
                villager.getNavigation().stop();
            }
            return;
        }

        persistent.putString(ISSUE_KEY, "");
        // Store the work point for the lifetime of a tree target. Re-picking
        // the nearest side every second can make the villager circle the trunk.
        BlockPos workPos = readWorkPosition(persistent);
        if (workPos == null || !isWorkPositionSafe(level, treeLog, workPos)) {
            workPos = findWorkPosition(level, treeLog, villager);
            if (workPos != null) writeWorkPosition(persistent, workPos);
        }
        if (workPos == null) {
            persistent.putString(ISSUE_KEY, "Нет безопасного места для рубки");
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            clearTreeTarget(persistent);
            updateWorkerName(villager, " — нет места для рубки");
            if (carriedCount > 0) {
                if (villager.distanceToSqr(deliveryStand.getX() + 0.5D, deliveryStand.getY(),
                        deliveryStand.getZ() + 0.5D) > 5.0D) {
                    villager.getNavigation().moveTo(deliveryStand.getX() + 0.5D, deliveryStand.getY(),
                            deliveryStand.getZ() + 0.5D, 0.9D);
                } else {
                    villager.getNavigation().stop();
                    depositCarriedLogs(warehouse, persistent, carriedItemId, carriedCount);
                }
            } else {
                villager.getNavigation().stop();
            }
            return;
        }

        if (villager.distanceToSqr(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D) > 4.0D) {
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            WorkerNavigationGuard.Outcome navigation = WorkerNavigationGuard.inspect(
                    villager, "lumberjack", WorkerNavigationPolicy.STAGNANT_CHECK_LIMIT, WorkerNavigationPolicy.MAX_RETRIES);
            if (navigation == WorkerNavigationGuard.Outcome.EXHAUSTED) {
                villager.getNavigation().stop();
                persistent.putString(ISSUE_KEY,
                        "Не удалось добраться до дерева после 3 попыток. Переназначь лесоруба для повтора.");
                clearTreeTarget(persistent);
                updateWorkerName(villager, " — путь заблокирован");
            } else if (navigation == WorkerNavigationGuard.Outcome.RETRY) {
                villager.getNavigation().stop();
                villager.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(),
                        workPos.getZ() + 0.5D, 0.9D);
                updateWorkerName(villager, " — повторный поиск пути");
            } else if (villager.getNavigation().isDone()) {
                villager.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(),
                        workPos.getZ() + 0.5D, 0.9D);
            }
            return;
        }

        WorkerNavigationGuard.reset(villager, "lumberjack");
        villager.getNavigation().stop();
        java.util.List<BlockPos> currentTree = findConnectedTreeLogs(level, treeLog, worldData, warehousePos);
        if (currentTree.isEmpty()) {
            clearTreeTarget(persistent);
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            updateWorkerName(villager, " — ищет дерево");
            return;
        }

        // One AI work step is one second. The full connected tree is removed at
        // once after one second per log, so no floating treetop is left behind.
        int requiredSeconds = Math.max(1, currentTree.size());
        persistent.putInt(TARGET_TREE_SIZE_KEY, requiredSeconds);
        int progress = persistent.getInt(CHOP_PROGRESS_KEY) + 1;
        persistent.putInt(CHOP_PROGRESS_KEY, Math.min(progress, requiredSeconds));
        updateChoppingName(villager, Math.min(progress, requiredSeconds), requiredSeconds);
        if (progress < requiredSeconds) {
            return;
        }

        BlockState rootState = level.getBlockState(treeLog);
        if (!rootState.is(BlockTags.LOGS) || isProtectedLog(treeLog, worldData, warehousePos)) {
            clearTreeTarget(persistent);
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            return;
        }

        Item logItem = rootState.getBlock().asItem();
        String actualItemId = BuiltInRegistries.ITEM.getKey(logItem).toString();
        if (logItem == net.minecraft.world.item.Items.AIR
                || carriedCount > 0 && !carriedItemId.equals(actualItemId)) {
            clearTreeTarget(persistent);
            persistent.putInt(CHOP_PROGRESS_KEY, 0);
            return;
        }

        int harvested = 0;
        for (BlockPos logPos : currentTree) {
            BlockState current = level.getBlockState(logPos);
            if (!current.is(BlockTags.LOGS) || current.getBlock().asItem() != logItem
                    || isProtectedLog(logPos, worldData, warehousePos)) {
                continue;
            }
            if (level.destroyBlock(logPos, false)) {
                harvested++;
            }
        }

        if (harvested > 0) {
            if (carriedCount == 0) {
                carriedItemId = actualItemId;
                persistent.putString(CARRIED_ITEM_KEY, carriedItemId);
            }
            persistent.putInt(CARRIED_COUNT_KEY, carriedCount + harvested);
        }
        clearTreeTarget(persistent);
        persistent.putInt(CHOP_PROGRESS_KEY, 0);
        persistent.putInt(TARGET_TREE_SIZE_KEY, 0);
        updateWorkerName(villager, harvested > 0 ? " — срубил " + harvested + " бр." : " — ищет дерево");
    }

    private static BlockPos findNearestTreeLog(ServerLevel level, BlockPos origin,
                                                CivitasSavedData worldData, BlockPos villageCenter) {
        // Scan the bounded search volume once, then check nearby foliage only
        // for candidates in distance order. Checking a 7x7x10 leaf volume for
        // every log in a dense forest was needlessly expensive each work cycle.
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        for (int dx = -TREE_SEARCH_RADIUS; dx <= TREE_SEARCH_RADIUS; dx++) {
            for (int dz = -TREE_SEARCH_RADIUS; dz <= TREE_SEARCH_RADIUS; dz++) {
                for (int dy = -6; dy <= 18; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(BlockTags.LOGS) && !isProtectedLog(pos, worldData, villageCenter)) {
                        candidates.add(pos.immutable());
                    }
                }
            }
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos -> origin.distSqr(pos)));

        for (BlockPos candidate : candidates) {
            if (!hasNearbyLeaves(level, candidate)) continue;
            BlockState state = level.getBlockState(candidate);
            Item targetItem = state.getBlock().asItem();
            java.util.List<BlockPos> connected = findConnectedTreeLogs(
                    level, candidate, worldData, villageCenter, targetItem);
            if (connected.isEmpty()) continue;

            // Target the lowest block in the connected tree, not a branch or a
            // canopy log. This puts the worker beside the trunk base consistently.
            BlockPos base = null;
            for (BlockPos log : connected) {
                if (base == null || log.getY() < base.getY()
                        || log.getY() == base.getY() && origin.distSqr(log) < origin.distSqr(base)) {
                    base = log;
                }
            }
            if (base != null) return base;
        }
        return null;
    }

    private static boolean isHarvestableTarget(ServerLevel level, BlockPos target,
                                                CivitasSavedData worldData, BlockPos villageCenter) {
        return level.hasChunkAt(target)
                && level.getBlockState(target).is(BlockTags.LOGS)
                && !isProtectedLog(target, worldData, villageCenter);
    }

    private static BlockPos readTreeTarget(net.minecraft.nbt.CompoundTag persistent) {
        if (!persistent.getBoolean(TARGET_TREE_ASSIGNED_KEY)) return null;
        return new BlockPos(persistent.getInt(TARGET_TREE_X_KEY),
                persistent.getInt(TARGET_TREE_Y_KEY), persistent.getInt(TARGET_TREE_Z_KEY));
    }

    private static void writeTreeTarget(net.minecraft.nbt.CompoundTag persistent, BlockPos target) {
        WorkerNavigationGuard.reset(persistent, "lumberjack");
        persistent.putBoolean(TARGET_TREE_ASSIGNED_KEY, true);
        persistent.putInt(TARGET_TREE_X_KEY, target.getX());
        persistent.putInt(TARGET_TREE_Y_KEY, target.getY());
        persistent.putInt(TARGET_TREE_Z_KEY, target.getZ());
        persistent.putInt(TARGET_TREE_SIZE_KEY, 0);
        persistent.putInt(CHOP_PROGRESS_KEY, 0);
        clearWorkPosition(persistent);
    }

    private static void clearTreeTarget(net.minecraft.nbt.CompoundTag persistent) {
        persistent.remove(TARGET_TREE_ASSIGNED_KEY);
        persistent.remove(TARGET_TREE_X_KEY);
        persistent.remove(TARGET_TREE_Y_KEY);
        persistent.remove(TARGET_TREE_Z_KEY);
        persistent.putInt(TARGET_TREE_SIZE_KEY, 0);
        clearWorkPosition(persistent);
    }

    private static BlockPos readWorkPosition(net.minecraft.nbt.CompoundTag persistent) {
        if (!persistent.getBoolean(WORK_POS_ASSIGNED_KEY)) return null;
        return new BlockPos(persistent.getInt(WORK_POS_X_KEY),
                persistent.getInt(WORK_POS_Y_KEY), persistent.getInt(WORK_POS_Z_KEY));
    }

    private static void writeWorkPosition(net.minecraft.nbt.CompoundTag persistent, BlockPos pos) {
        persistent.putBoolean(WORK_POS_ASSIGNED_KEY, true);
        persistent.putInt(WORK_POS_X_KEY, pos.getX());
        persistent.putInt(WORK_POS_Y_KEY, pos.getY());
        persistent.putInt(WORK_POS_Z_KEY, pos.getZ());
    }

    private static void clearWorkPosition(net.minecraft.nbt.CompoundTag persistent) {
        persistent.remove(WORK_POS_ASSIGNED_KEY);
        persistent.remove(WORK_POS_X_KEY);
        persistent.remove(WORK_POS_Y_KEY);
        persistent.remove(WORK_POS_Z_KEY);
    }

    private static boolean isWorkPositionSafe(ServerLevel level, BlockPos logPos, BlockPos candidate) {
        if (Math.abs(candidate.getX() - logPos.getX())
                        + Math.abs(candidate.getZ() - logPos.getZ()) != 1
                || candidate.getY() > logPos.getY()
                || candidate.getY() < logPos.getY() - 8) {
            return false;
        }
        BlockPos below = candidate.below();
        BlockPos head = candidate.above();
        if (!level.hasChunkAt(candidate) || !level.hasChunkAt(below) || !level.hasChunkAt(head)) return false;
        BlockState feet = level.getBlockState(candidate);
        BlockState headState = level.getBlockState(head);
        BlockState floor = level.getBlockState(below);
        return (feet.isAir() || feet.canBeReplaced()) && feet.getFluidState().isEmpty()
                && (headState.isAir() || headState.canBeReplaced()) && headState.getFluidState().isEmpty()
                && floor.isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(below).isEmpty();
    }

    /** Collect only face-connected logs of the same tree species. */
    private static java.util.List<BlockPos> findConnectedTreeLogs(
            ServerLevel level, BlockPos start, CivitasSavedData worldData, BlockPos villageCenter) {
        BlockState state = level.getBlockState(start);
        if (!state.is(BlockTags.LOGS)) return java.util.List.of();
        return findConnectedTreeLogs(level, start, worldData, villageCenter, state.getBlock().asItem());
    }

    private static java.util.List<BlockPos> findConnectedTreeLogs(
            ServerLevel level, BlockPos start, CivitasSavedData worldData,
            BlockPos villageCenter, Item targetItem) {
        java.util.List<BlockPos> result = new java.util.ArrayList<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        queue.add(start.immutable());
        visited.add(start.immutable());

        while (!queue.isEmpty() && result.size() < MAX_TREE_LOGS) {
            BlockPos current = queue.removeFirst();
            if (!level.hasChunkAt(current) || isProtectedLog(current, worldData, villageCenter)) continue;
            BlockState currentState = level.getBlockState(current);
            if (!currentState.is(BlockTags.LOGS) || currentState.getBlock().asItem() != targetItem) continue;
            result.add(current.immutable());

            // Six-direction adjacency avoids combining two neighbouring trees
            // that merely touch at a corner.
            for (Direction direction : Direction.values()) {
                BlockPos candidate = current.relative(direction);
                if (visited.contains(candidate)
                        || Math.abs(candidate.getX() - start.getX()) > 10
                        || Math.abs(candidate.getZ() - start.getZ()) > 10
                        || candidate.getY() < start.getY() - 40
                        || candidate.getY() > start.getY() + 40) {
                    continue;
                }
                visited.add(candidate);
                if (level.hasChunkAt(candidate) && level.getBlockState(candidate).is(BlockTags.LOGS)
                        && level.getBlockState(candidate).getBlock().asItem() == targetItem
                        && !isProtectedLog(candidate, worldData, villageCenter)) {
                    queue.addLast(candidate);
                }
            }
        }
        return result;
    }

    private static boolean isProtectedLog(BlockPos pos, CivitasSavedData worldData, BlockPos villageCenter) {
        // The generated village footprint is 29x29 (from -14 to +14), which
        // includes all four starter houses, their roofs, paths and the core.
        return (Math.abs(pos.getX() - villageCenter.getX()) <= STARTER_VILLAGE_PROTECTION_RADIUS
                && Math.abs(pos.getZ() - villageCenter.getZ()) <= STARTER_VILLAGE_PROTECTION_RADIUS)
                || worldData.hasBuildingNear(pos, 5);
    }

    private static boolean hasNearbyLeaves(ServerLevel level, BlockPos logPos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 8; dy++) {
                    BlockPos candidate = logPos.offset(dx, dy, dz);
                    if (level.hasChunkAt(candidate) && level.getBlockState(candidate).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos findWorkPosition(ServerLevel level, BlockPos logPos, Villager villager) {
        BlockPos[] adjacent = {logPos.east(), logPos.west(), logPos.north(), logPos.south()};
        BlockPos bestPosition = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos side : adjacent) {
            for (int y = logPos.getY(); y >= Math.max(level.getMinBuildHeight() + 1, logPos.getY() - 8); y--) {
                BlockPos candidate = new BlockPos(side.getX(), y, side.getZ());
                BlockPos below = candidate.below();
                if (!level.hasChunkAt(candidate) || !level.hasChunkAt(below)) continue;
                BlockState feet = level.getBlockState(candidate);
                BlockState head = level.getBlockState(candidate.above());
                BlockState floor = level.getBlockState(below);
                if ((feet.isAir() || feet.canBeReplaced()) && feet.getFluidState().isEmpty()
                        && (head.isAir() || head.canBeReplaced()) && head.getFluidState().isEmpty()
                        && floor.isFaceSturdy(level, below, Direction.UP)
                        && level.getFluidState(below).isEmpty()) {
                    double distance = villager.distanceToSqr(
                            candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                    if (distance < bestDistance) {
                        bestPosition = candidate;
                        bestDistance = distance;
                    }
                    // The first valid Y is the highest safe standing block on this side.
                    break;
                }
            }
        }
        return bestPosition;
    }

    private static void updateWorkerName(Villager villager, String suffix) {
        String displayName = "Лесоруб Civitas" + (suffix == null ? "" : suffix);
        if (villager.getCustomName() == null
                || !villager.getCustomName().getString().equals(displayName)) {
            villager.setCustomName(Component.literal(displayName));
        }
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
    }

    private static void updateChoppingName(Villager villager, int progress, int total) {
        int safeTotal = Math.max(1, total);
        int filled = Math.max(0, Math.min(10, (int) Math.ceil(progress * 10.0D / safeTotal)));
        Component displayName = Component.literal("Лесоруб Civitas ")
                .append(Component.literal("[").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("■".repeat(filled)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("□".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("] " + progress + "/" + safeTotal + " сек")
                        .withStyle(ChatFormatting.YELLOW));
        if (villager.getCustomName() == null
                || !villager.getCustomName().getString().equals(displayName.getString())) {
            villager.setCustomName(displayName);
        }
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
    }

    private static void depositCarriedLogs(SettlementWarehouseBlockEntity warehouse,
                                           net.minecraft.nbt.CompoundTag persistent,
                                           String itemId, int carriedCount) {
        if (carriedCount <= 0 || itemId == null || itemId.isBlank()) {
            persistent.putInt(CARRIED_COUNT_KEY, 0);
            persistent.putString(CARRIED_ITEM_KEY, "");
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR) {
            return;
        }

        int remaining = carriedCount;
        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = warehouse.getItem(slot);
            if (existing.isEmpty() || existing.getItem() != item) {
                continue;
            }
            int moved = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
            if (moved > 0) {
                existing.grow(moved);
                remaining -= moved;
                warehouse.setChanged();
            }
        }

        for (int slot = 0; slot < warehouse.getContainerSize() && remaining > 0; slot++) {
            if (!warehouse.getItem(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(remaining, item.getDefaultMaxStackSize());
            warehouse.setItem(slot, new ItemStack(item, moved));
            remaining -= moved;
        }

        persistent.putInt(CARRIED_COUNT_KEY, remaining);
        persistent.putString(CARRIED_ITEM_KEY, remaining == 0 ? "" : itemId);
        persistent.putString(ISSUE_KEY, remaining == 0 ? "" : "Склад заполнен — груз сохранён");
    }

}
