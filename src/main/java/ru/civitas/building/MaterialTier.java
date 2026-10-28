package ru.civitas.building;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import ru.civitas.world.BuildingDefinition;

import java.util.Map;
import java.util.Set;

/** Material quality controls the passive durability of registered buildings. */
public enum MaterialTier {
    PRIMITIVE(1, 1.0D, Set.of(
            Blocks.DIRT, Blocks.GRAVEL, Blocks.OAK_LOG, Blocks.BIRCH_LOG,
            Blocks.SPRUCE_LOG, Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS,
            Blocks.SPRUCE_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB)),
    STANDARD(2, 1.2D, Set.of(
            Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE,
            Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
            Blocks.GLASS, Blocks.GLASS_PANE)),
    QUALITY(3, 1.5D, Set.of(
            Blocks.BRICKS, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB,
            Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB,
            Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE,
            Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB)),
    LUXURY(4, 2.0D, Set.of(
            Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK,
            Blocks.NETHERITE_BLOCK, Blocks.POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_STAIRS));

    public final int level;
    public final double durabilityMultiplier;
    public final Set<Block> blocks;

    MaterialTier(int level, double durabilityMultiplier, Set<Block> blocks) {
        this.level = level;
        this.durabilityMultiplier = durabilityMultiplier;
        this.blocks = blocks;
    }

    public static MaterialTier classify(Block block) {
        for (MaterialTier tier : values()) {
            if (tier.blocks.contains(block)) return tier;
        }
        return STANDARD;
    }

    /**
     * Compatibility fallback for the current registered building catalog.
     * Existing building records do not store a block-by-block material snapshot.
     */
    public static MaterialTier forBuildingType(String type) {
        BuildingDefinition.Type parsed = BuildingDefinition.Type.byId(type).orElse(null);
        if (parsed == null) return STANDARD;
        Map<MaterialTier, Integer> counts = new java.util.EnumMap<>(MaterialTier.class);
        for (BuildingDefinition.ResourceCost cost : BuildingDefinition.get(parsed).resources()) {
            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse(cost.itemId()));
            MaterialTier tier = classify(block);
            counts.merge(tier, cost.amount(), Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(STANDARD);
    }
}
