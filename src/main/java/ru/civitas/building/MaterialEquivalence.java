package ru.civitas.building;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.Set;

/** Symmetric groups of common substitute blocks for future blueprint-aware builds. */
public final class MaterialEquivalence {
    private static final Map<Block, Set<Block>> EQUIVALENTS = Map.of(
            Blocks.OAK_PLANKS, Set.of(Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS,
                    Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
                    Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS, Blocks.BAMBOO_PLANKS),
            Blocks.OAK_LOG, Set.of(Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.JUNGLE_LOG,
                    Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG),
            Blocks.COBBLESTONE, Set.of(Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE),
            Blocks.GLASS, Set.of(Blocks.GLASS_PANE, Blocks.WHITE_STAINED_GLASS)
    );

    private MaterialEquivalence() { }

    public static Set<Block> equivalentsOf(Block requested) {
        if (requested == null) return Set.of();
        return EQUIVALENTS.getOrDefault(requested, Set.of());
    }
}
