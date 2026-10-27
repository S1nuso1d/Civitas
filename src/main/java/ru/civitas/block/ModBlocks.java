package ru.civitas.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.civitas.CivitasMod;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CivitasMod.MOD_ID);

    public static final RegistryObject<Block> WAREHOUSE = BLOCKS.register(
            "warehouse",
            () -> new SettlementWarehouseBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .requiresCorrectToolForDrops())
    );

    private ModBlocks() {
    }
}
