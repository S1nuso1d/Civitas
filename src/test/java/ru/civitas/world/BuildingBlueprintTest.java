package ru.civitas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildingBlueprintTest {
    @Test
    void everyBuildingHasAValidBlueprintForEveryLayer() {
        for (BuildingDefinition definition : BuildingDefinition.all()) {
            for (int layer = 1; layer <= definition.totalLayers(); layer++) {
                List<BuildingBlueprint.Placement> placements =
                        BuildingBlueprint.layer(definition.type(), layer);
                assertFalse(placements.isEmpty(), definition.id() + " layer " + layer);
                Set<BlockPos> positions = new HashSet<>();
                for (BuildingBlueprint.Placement placement : placements) {
                    assertNotNull(placement.relativePosition());
                    assertNotNull(placement.state());
                    assertTrue(positions.add(placement.relativePosition()),
                            "Duplicate coordinate in " + definition.id() + " layer " + layer);
                }
                assertThrows(UnsupportedOperationException.class,
                        () -> placements.add(placements.get(0)));
            }
        }
    }

    @Test
    void cabinBlueprintKeepsItsFloorWallsWindowsAndRoof() {
        var floor = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 1);
        assertEquals(25, floor.size());
        assertTrue(floor.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 1, 0))
                && p.state().is(Blocks.CRAFTING_TABLE)));
        var walls = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 3);
        assertEquals(2, walls.stream().filter(p -> p.state().is(Blocks.GLASS)).count());
        var roof = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 5);
        assertTrue(roof.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 5, 0))
                && p.state().is(Blocks.OAK_PLANKS)));
        assertTrue(roof.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 1, -2))
                && p.state().isAir()));
    }

    @Test
    void farmBlueprintContainsFenceWaterAndTwentyFourCrops() {
        var base = BuildingBlueprint.layer(BuildingDefinition.Type.FARM, 1);
        assertTrue(base.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 1, 0))
                && p.state().is(Blocks.WATER)));
        assertTrue(base.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(3, 1, 0))
                && p.state().is(Blocks.OAK_FENCE)));
        var crops = BuildingBlueprint.layer(BuildingDefinition.Type.FARM, 2);
        assertEquals(24, crops.stream().filter(p -> p.state().is(Blocks.WHEAT)).count());
    }

    @Test
    void workshopBlueprintContainsWorkstationsAndCobblestoneWalls() {
        var base = BuildingBlueprint.layer(BuildingDefinition.Type.WORKSHOP, 1);
        assertTrue(base.stream().anyMatch(p -> p.state().is(Blocks.CRAFTING_TABLE)));
        assertTrue(base.stream().anyMatch(p -> p.state().is(Blocks.FURNACE)));
        assertTrue(base.stream().anyMatch(p -> p.state().is(Blocks.ANVIL)));
        var walls = BuildingBlueprint.layer(BuildingDefinition.Type.WORKSHOP, 3);
        assertEquals(16, walls.stream().filter(p -> p.state().is(Blocks.COBBLESTONE)).count());
    }

    @Test
    void invalidLayerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> BuildingBlueprint.layer(BuildingDefinition.Type.FARM, 3));
    }
}
