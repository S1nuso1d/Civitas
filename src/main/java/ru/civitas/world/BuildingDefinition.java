package ru.civitas.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Single source of truth for settlement building metadata.
 *
 * Costs, names, construction layers and clearance dimensions live here so
 * commands, server-side building logic and the client screen use the same data.
 */
public record BuildingDefinition(
        Type type,
        String id,
        String displayName,
        int width,
        int depth,
        int height,
        int totalLayers,
        List<ResourceCost> resources
) {
    public enum Type {
        CABIN("cabin"),
        FARM("farm"),
        WORKSHOP("workshop");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Type> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            for (Type type : values()) {
                if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    public record ResourceCost(String itemId, int amount, String displayName) {
        public ResourceCost {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
            if (amount < 1) {
                throw new IllegalArgumentException("resource amount must be positive");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("resource displayName must not be blank");
            }
        }
    }

    private static final Map<Type, BuildingDefinition> REGISTRY;

    static {
        EnumMap<Type, BuildingDefinition> definitions = new EnumMap<>(Type.class);

        register(definitions, new BuildingDefinition(
                Type.CABIN, "cabin", "Жилой дом", 7, 7, 5, 5,
                List.of(
                        new ResourceCost("minecraft:oak_planks", 32, "дубовые доски"),
                        new ResourceCost("minecraft:oak_log", 8, "дубовые брёвна"),
                        new ResourceCost("minecraft:glass", 4, "стекло")
                )
        ));
        register(definitions, new BuildingDefinition(
                Type.FARM, "farm", "Ферма", 7, 7, 3, 2,
                List.of(
                        new ResourceCost("minecraft:dirt", 49, "земля"),
                        new ResourceCost("minecraft:wheat_seeds", 24, "семена пшеницы"),
                        new ResourceCost("minecraft:water_bucket", 1, "ведро воды")
                )
        ));
        register(definitions, new BuildingDefinition(
                Type.WORKSHOP, "workshop", "Мастерская", 7, 7, 4, 4,
                List.of(
                        new ResourceCost("minecraft:oak_planks", 32, "дубовые доски"),
                        new ResourceCost("minecraft:cobblestone", 25, "булыжник"),
                        new ResourceCost("minecraft:crafting_table", 1, "верстак")
                )
        ));

        REGISTRY = Collections.unmodifiableMap(definitions);
    }

    public BuildingDefinition {
        if (type == null || id == null || id.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Building identity must not be blank");
        }
        if (width < 1 || depth < 1 || height < 1 || totalLayers < 1) {
            throw new IllegalArgumentException("Building dimensions and layer count must be positive");
        }
        resources = List.copyOf(resources);
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("A buildable structure must have a resource cost");
        }
    }

    private static void register(Map<Type, BuildingDefinition> definitions, BuildingDefinition definition) {
        if (definitions.putIfAbsent(definition.type(), definition) != null) {
            throw new IllegalStateException("Duplicate building definition: " + definition.type());
        }
    }

    public static BuildingDefinition get(Type type) {
        BuildingDefinition definition = REGISTRY.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown building type: " + type);
        }
        return definition;
    }

    public static Optional<BuildingDefinition> find(String id) {
        return Type.byId(id).map(REGISTRY::get);
    }

    public static List<BuildingDefinition> all() {
        return List.copyOf(new ArrayList<>(REGISTRY.values()));
    }

    /** Ordered item ID -> count view used by server-side stock validation. */
    public Map<String, Integer> costs() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ResourceCost resource : resources) {
            result.put(resource.itemId(), resource.amount());
        }
        return Collections.unmodifiableMap(result);
    }

    /** Compact human-readable cost used by both server messages and the GUI. */
    public String describeCosts() {
        return resources.stream()
                .map(resource -> resource.displayName() + " × " + resource.amount())
                .collect(Collectors.joining(", "));
    }

    public int radiusX() {
        return width / 2;
    }

    public int radiusZ() {
        return depth / 2;
    }
}
