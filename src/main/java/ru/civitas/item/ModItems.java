package ru.civitas.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.civitas.CivitasMod;
import ru.civitas.block.ModBlocks;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CivitasMod.MOD_ID);

    public static final RegistryObject<CivilizationCodexItem> CIVILIZATION_CODEX = ITEMS.register(
            "civilization_codex",
            () -> new CivilizationCodexItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<BlueprintPlannerItem> BLUEPRINT_PLANNER = ITEMS.register(
            "blueprint_planner",
            () -> new BlueprintPlannerItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<BlockItem> WAREHOUSE_ITEM = ITEMS.register(
            "warehouse",
            () -> new BlockItem(ModBlocks.WAREHOUSE.get(), new Item.Properties())
    );

    private ModItems() {
    }
}