# Changelog

## [0.42.0] - Living Systems Pass (first implementation slice)

### Added
- Added a client-only real-time settlement HUD toggled with F4, backed by the existing authoritative settlement snapshot.
- HUD shows population, worker roles, food reserve estimate, seven-day resource trends, construction queue, building count, warehouse occupancy and the current settlement status.
- Added a villager inspection panel while looking at a nearby villager: name, current custom status, movement/idle state, health and Minecraft profession.
- This slice deliberately does not claim needs-based worker autonomy, persistent traditions, new networking fields, or a complete AI reason stack. Those require dedicated follow-up changes and tests.

### Validation
- Source committed; GitHub Actions build/tests and in-game rendering must be checked before calling this release verified.

## [0.40.3] - In-game Blueprint Planner

### Added
- Added a Blueprint Planner item that opens a vanilla-style in-game catalog for cabin, farm, and workshop blueprints.
- The catalog shows dimensions, construction stages, and material costs; allows 90-degree rotation; and sends preview/order requests for the block currently targeted by the player.
- Placement is still server-authoritative and uses existing validation, territory, and warehouse-resource checks. The current preview command still uses the existing server preview behavior; a translucent block-by-block ghost renderer is not included in this slice.
- Added English and Russian item names and a paper-based item model.

### Validation
- GitHub Actions build/tests must complete before this change can be called build-verified. In-game interaction has not yet been tested.

## [0.40.2] - Blueprint Catalog

### Added
- Added `/civitas blueprints` to list supported building types, dimensions, construction stages, and costs.
- Documents the preview-then-confirm workflow and preserves existing site/resource validation.

### Validation
- Source change committed; CI compilation/tests and in-game validation are still required.

## [0.40.1] - Optional Mod Integration

### Added
- Added `/civitas addons` to detect optional Biomes O' Plenty, Signpost, Xaero's Minimap and Dynamic Lights installs and to check Biomes O' Plenty's GlitchCore/TerraBlender dependencies.
- Added `/civitas map` to print the settlement center coordinates for manual waypoint creation in Xaero's Minimap.
- No compile-time dependency on the add-on mods was introduced; Civitas remains optional-mod safe.

### Validation
- Add-on metadata inspection was performed, but full runtime compatibility remains unverified.

## [0.40.0] - Reliability & Polish Pass
- Added navigation watchdog, distance-based AI decision cadence, worker equipment support, and optional client-side worker name tags.
