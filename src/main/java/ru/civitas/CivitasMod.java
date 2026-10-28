package ru.civitas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ru.civitas.block.ModBlockEntities;
import ru.civitas.block.ModBlocks;
import ru.civitas.command.CivitasCommands;
import ru.civitas.data.CivitasSavedData;
import ru.civitas.item.ModItems;
import ru.civitas.menu.ModMenus;
import ru.civitas.network.CivitasNetwork;
import ru.civitas.world.StarterVillageGenerator;
import ru.civitas.worker.LumberjackWorkerAI;
import ru.civitas.worker.ConstructionWorkerAI;
import ru.civitas.worker.FarmerWorkerAI;
import net.minecraftforge.fml.config.ModConfig;
import ru.civitas.economy.SettlementEconomyAI;
import ru.civitas.trade.TradeCaravanManager;
import ru.civitas.trade.TradeCaravan;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.entity.npc.Villager;

@Mod(CivitasMod.MOD_ID)
public final class CivitasMod {
    public static final String MOD_ID = "civitas";
    private static final String CODEX_RECEIVED_KEY = "civitas.codex_received";
    private static final String VILLAGE_SPAWN_DONE_KEY = "civitas.village_spawn_done";

    public CivitasMod(FMLJavaModLoadingContext context) {
        ModBlocks.BLOCKS.register(context.getModEventBus());
        ModBlockEntities.BLOCK_ENTITIES.register(context.getModEventBus());
        ModItems.ITEMS.register(context.getModEventBus());
        ModMenus.MENUS.register(context.getModEventBus());
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CivitasConfig.SPEC);
        CivitasNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        MinecraftForge.EVENT_BUS.addListener(this::onBuildCreativeTab);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(LumberjackWorkerAI::onLivingTick);
        MinecraftForge.EVENT_BUS.addListener(LumberjackWorkerAI::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(ConstructionWorkerAI::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(FarmerWorkerAI::onLivingTick);
        MinecraftForge.EVENT_BUS.addListener(FarmerWorkerAI::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(SettlementEconomyAI::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(TradeCaravanManager::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(TradeCaravanManager::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(this::onTradeInteract);
        MinecraftForge.EVENT_BUS.addListener(this::onTradeHurt);
    }


    private void onTradeInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Villager villager)
                || !villager.getPersistentData().getBoolean("civitas.trader")
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        CivitasSavedData data = CivitasSavedData.get(player.getServer());
        TradeCaravan caravan = data.getActiveTradeCaravans().stream()
                .filter(c -> c.traderId().equals(villager.getUUID())).findFirst().orElse(null);
        if (caravan == null || !caravan.canTrade() || player.distanceToSqr(villager) > 64.0D) return;
        ru.civitas.network.CivitasNetwork.openTradeScreen(player, caravan, data);
        event.setCanceled(true);
    }

    private void onTradeHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Villager villager
                && villager.getPersistentData().getBoolean("civitas.trader")) {
            villager.getPersistentData().putBoolean("civitas.trader.attacked", true);
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CivitasSavedData data = CivitasSavedData.get(player.getServer());
        ServerLevel overworld = player.getServer().overworld();
        if (!data.hasStarterVillage()) {
            BlockPos center = StarterVillageGenerator.create(overworld, overworld.getSharedSpawnPos());
            data.setStarterVillageCenter(center);
            if (!data.isFounded()) {
                data.found("Новая деревня", player, center);
            }
        }

        BlockPos village = data.getStarterVillageCenter();
        LumberjackWorkerAI.ensureWorkerExists(overworld, village);
        if (!player.getPersistentData().getBoolean(VILLAGE_SPAWN_DONE_KEY)) {
            player.teleportTo(overworld, village.getX() + 0.5D, village.getY() + 1.0D, village.getZ() + 2.5D, 0.0F, 0.0F);
            player.getPersistentData().putBoolean(VILLAGE_SPAWN_DONE_KEY, true);
            player.sendSystemMessage(Component.literal("Ты прибыл в деревню Civitas. Здесь начинается развитие твоей цивилизации."));
        }

        if (!player.getPersistentData().getBoolean(CODEX_RECEIVED_KEY)) {
            ItemStack codex = ModItems.CIVILIZATION_CODEX.get().getDefaultInstance();
            if (!player.getInventory().add(codex)) {
                player.drop(codex, false);
            }
            player.getPersistentData().putBoolean(CODEX_RECEIVED_KEY, true);
            player.sendSystemMessage(Component.translatable("message.civitas.welcome"));
            player.sendSystemMessage(Component.literal("Подсказка: используй Кодекс цивилизации в инвентаре, чтобы открыть панель управления."));
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel overworld = player.getServer().overworld();
        CivitasSavedData data = CivitasSavedData.get(player.getServer());
        if (!data.hasStarterVillage()) {
            return;
        }

        BlockPos village = data.getStarterVillageCenter();
        player.teleportTo(overworld, village.getX() + 0.5D, village.getY() + 1.0D, village.getZ() + 2.5D, 0.0F, 0.0F);
        player.sendSystemMessage(Component.literal("Ты возвращаешься в деревню Civitas."));
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.WAREHOUSE_ITEM.get());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CivitasCommands.register(event);
    }
}
