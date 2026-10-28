package ru.civitas.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildingBlueprintTest {
    @Test
    void everyBuildingHasAValidImmutableBlueprintForEveryLayer() {
        for (BuildingDefinition definition : BuildingDefinition.all()) {
            for (int layer = 1; layer <= definition.totalLayers(); layer++) {
                List<BuildingBlueprint.Placement> placements =
                        BuildingBlueprint.layer(definition.type(), layer);
                assertFalse(placements.isEmpty(), definition.id() + " layer " + layer);
                Set<BlockPos> positions = new HashSet<>();
                for (BuildingBlueprint.Placement placement : placements) {
                    assertNotNull(placement.relativePosition());
                    assertFalse(placement.blockId().isBlank());
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
                && p.blockId().equals("minecraft:crafting_table")));
        var walls = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 3);
        assertEquals(2, walls.stream().filter(p -> p.blockId().equals("minecraft:glass")).count());
        var roof = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 5);
        assertTrue(roof.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 5, 0))
                && p.blockId().equals("minecraft:oak_planks")));
        assertTrue(roof.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 1, -2))
                && p.clearsBlock()));
    }

    @Test
    void farmBlueprintContainsFenceWaterAndTwentyFourCrops() {
        var base = BuildingBlueprint.layer(BuildingDefinition.Type.FARM, 1);
        assertTrue(base.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 1, 0))
                && p.blockId().equals("minecraft:water")));
        assertTrue(base.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(3, 1, 0))
                && p.blockId().equals("minecraft:oak_fence")));
        var crops = BuildingBlueprint.layer(BuildingDefinition.Type.FARM, 2);
        assertEquals(24, crops.stream().filter(p -> p.blockId().equals("minecraft:wheat")).count());
        assertTrue(crops.stream().allMatch(p -> p.cropAge() == 0));
    }

    @Test
    void workshopBlueprintContainsWorkstationsAndCobblestoneWalls() {
        var base = BuildingBlueprint.layer(BuildingDefinition.Type.WORKSHOP, 1);
        assertTrue(base.stream().anyMatch(p -> p.blockId().equals("minecraft:crafting_table")));
        assertTrue(base.stream().anyMatch(p -> p.blockId().equals("minecraft:furnace")));
        assertTrue(base.stream().anyMatch(p -> p.blockId().equals("minecraft:anvil")));
        var walls = BuildingBlueprint.layer(BuildingDefinition.Type.WORKSHOP, 3);
        assertEquals(16, walls.stream().filter(p -> p.blockId().equals("minecraft:cobblestone")).count());
    }

    @Test
    void invalidLayerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> BuildingBlueprint.layer(BuildingDefinition.Type.FARM, 3));
    }
}
