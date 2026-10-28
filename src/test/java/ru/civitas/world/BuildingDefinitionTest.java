package ru.civitas.world;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingDefinitionTest {

    @Test
    void registryContainsEveryBuildableType() {
        Set<BuildingDefinition.Type> types = BuildingDefinition.all().stream()
                .map(BuildingDefinition::type)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                BuildingDefinition.Type.CABIN,
                BuildingDefinition.Type.FARM,
                BuildingDefinition.Type.WORKSHOP
        ), types);
        assertEquals(types.size(), BuildingDefinition.all().size());
    }

    @Test
    void everyDefinitionHasValidDimensionsAndLayerCount() {
        for (BuildingDefinition definition : BuildingDefinition.all()) {
            assertTrue(definition.width() > 0);
            assertTrue(definition.depth() > 0);
            assertTrue(definition.height() > 0);
            assertTrue(definition.totalLayers() > 0);
            assertFalse(definition.resources().isEmpty());
            assertEquals(definition.type().id(), definition.id());
        }
    }

    @Test
    void registryContainsExpectedMaterialCosts() {
        Map<String, Integer> cabin = BuildingDefinition.get(BuildingDefinition.Type.CABIN).costs();
        assertEquals(32, cabin.get("minecraft:oak_planks"));
        assertEquals(8, cabin.get("minecraft:oak_log"));
        assertEquals(4, cabin.get("minecraft:glass"));

        Map<String, Integer> farm = BuildingDefinition.get(BuildingDefinition.Type.FARM).costs();
        assertEquals(49, farm.get("minecraft:dirt"));
        assertEquals(24, farm.get("minecraft:wheat_seeds"));
        assertEquals(1, farm.get("minecraft:water_bucket"));

        Map<String, Integer> workshop = BuildingDefinition.get(BuildingDefinition.Type.WORKSHOP).costs();
        assertEquals(32, workshop.get("minecraft:oak_planks"));
        assertEquals(25, workshop.get("minecraft:cobblestone"));
        assertEquals(1, workshop.get("minecraft:crafting_table"));
    }

    @Test
    void lookupIsCaseInsensitiveAndRejectsUnknownIds() {
        assertEquals(BuildingDefinition.Type.CABIN,
                BuildingDefinition.find("CABIN").orElseThrow().type());
        assertEquals(BuildingDefinition.Type.FARM,
                BuildingDefinition.find("farm").orElseThrow().type());
        assertTrue(BuildingDefinition.find("castle").isEmpty());
    }

    @Test
    void exposedRegistryAndCostMapsAreImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> BuildingDefinition.all().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> BuildingDefinition.get(BuildingDefinition.Type.CABIN)
                        .costs().put("minecraft:diamond", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> BuildingDefinition.get(BuildingDefinition.Type.CABIN)
                        .resources().clear());
    }
}
