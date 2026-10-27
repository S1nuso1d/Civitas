package ru.civitas.block;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.civitas.CivitasMod;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CivitasMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<SettlementWarehouseBlockEntity>> WAREHOUSE =
            BLOCK_ENTITIES.register("warehouse",
                    () -> BlockEntityType.Builder.of(
                            SettlementWarehouseBlockEntity::new,
                            ModBlocks.WAREHOUSE.get()
                    ).build(null));

    private ModBlockEntities() {
    }
}
