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
        var doorway = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 2);
        assertTrue(doorway.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 2, -2))
                && p.blockId().equals("minecraft:oak_door")));
        assertTrue(doorway.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 3, -2))
                && p.blockId().equals("minecraft:oak_door")));
        var roof = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 5);
        assertTrue(roof.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 5, 0))
                && p.blockId().equals("minecraft:oak_planks")));
        assertFalse(roof.stream().anyMatch(p -> p.clearsBlock()),
                "Roof layer must not erase the cabin's doorway");
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
        assertEquals(15, walls.stream().filter(p -> p.blockId().equals("minecraft:cobblestone")).count(),
                "The workshop wall leaves a two-block doorway");
        var doorway = BuildingBlueprint.layer(BuildingDefinition.Type.WORKSHOP, 2);
        assertTrue(doorway.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 2, -2))
                && p.blockId().equals("minecraft:oak_door")));
        assertTrue(doorway.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 3, -2))
                && p.blockId().equals("minecraft:oak_door")));
    }

    @Test
    void blueprintGeometryRotatesClockwiseInNinetyDegreeSteps() {
        var northDoor = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 2, 0);
        var eastDoor = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 2, 90);
        var southDoor = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 2, 180);
        var westDoor = BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 2, 270);

        assertTrue(northDoor.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 2, -2))
                && p.blockId().equals("minecraft:oak_door")));
        assertTrue(eastDoor.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(2, 2, 0))
                && p.blockId().equals("minecraft:oak_door")));
        assertTrue(southDoor.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(0, 2, 2))
                && p.blockId().equals("minecraft:oak_door")));
        assertTrue(westDoor.stream().anyMatch(p -> p.relativePosition().equals(new BlockPos(-2, 2, 0))
                && p.blockId().equals("minecraft:oak_door")));
        assertThrows(IllegalArgumentException.class,
                () -> BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 1, 45));
    }

    @Test
    void invalidLayerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BuildingBlueprint.layer(BuildingDefinition.Type.CABIN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> BuildingBlueprint.layer(BuildingDefinition.Type.FARM, 3));
    }
}
