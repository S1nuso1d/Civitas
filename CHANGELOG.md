# Changelog

## [0.40.1] - Optional Mod Integration

### Added
- Added `/civitas addons` to detect optional Biomes O' Plenty, Signpost, Xaero's Minimap and Dynamic Lights installs and to check Biomes O' Plenty's GlitchCore/TerraBlender dependencies.
- Added `/civitas map` to print the settlement center coordinates for manual waypoint creation in Xaero's Minimap.
- No compile-time dependency on the add-on mods was introduced; Civitas remains optional-mod safe.

### Validation
- JAR metadata inspection confirms the Biomes O' Plenty, Signpost and Xaero files target Forge/Minecraft 1.21.1. Biomes O' Plenty declares GlitchCore and TerraBlender as required dependencies.
- The supplied Dynamic Lights JAR declares Forge loader compatibility but has not been launched under Forge 52.1.20; runtime compatibility remains unverified.
- This source commit still requires CI build/tests and in-game validation.

## [0.40.0] - Reliability & Polish Pass (first slice)

### Added
- Added a shared navigation watchdog for assigned workers. It only monitors active routes, restarts stalled navigation without teleporting entities, and applies a cooldown after repeated failures.
- Added distance-based AI decision cadence for lumberjacks, farmers and builders while leaving vanilla entity navigation untouched. The cadence is aligned to world time so existing 20-tick scheduling remains compatible.
- Added chunk-aware target waiting for lumberjack and farmer targets; a temporarily unloaded target is retained rather than treated as a missing block.
- Assigned workers now hold a role marker: iron axe for lumberjacks, iron hoe for farmers and iron shovel for builders. The marker is removed when the profession is released.
- Villager name tags are now shown when the player looks directly at a villager or is within four blocks; this is client-side only.
- Added unit tests for AI LOD distance thresholds.

### Safety notes
- Existing saved settlement data and network protocol are unchanged.
- Automatic backup/migration and GUI polish are not part of this first slice; they will be handled separately after the actual save lifecycle is verified.

### Validation
- Source committed. Build, automated tests and in-game behavior have not yet been confirmed for this version.

## [0.39.0] - Living Society (first slice)

### Added
- Added persistent personal orders for individual villagers: follow the issuing player, stay, go home, guard a position, and reset to profession behavior.
- Added `/civitas order <resident> <follow|stay|home|guard|reset> [position]` with suggestions for valid order names.
- Personal orders take precedence over existing lumberjack, farmer and builder routines while preserving their existing work state.
- Navigation updates are throttled; nearby bed scans are bounded and use a retry cooldown.

### Scope note
- This release is the first slice of the Living Society pass. Villager health/illness, social/family relationships, and interior comfort scoring are not implemented in this version.

### Validation
- Source committed; local Gradle build, automated tests, and in-game checks remain required.

## [0.38.0] - Senses & Schedules

### Added
- Added shared worker lifecycle behavior: night rest for assigned lumberjacks, farmers and builders, preserving persisted targets and cargo.
- Added a non-stacking +35% base movement-speed modifier for assigned workers standing on supported road blocks.
- Added throttled work sounds, role-specific work particles, and occasional nearby worker chatter.
- Workers open adjacent unpowered doors while navigating and close tracked doors after passage when the doorway is clear.
- Rain reduces farmer harvest progress by 25%; lumberjacks receive a small cumulative chopping bonus in dry weather.

### Validation
- Source committed; local Gradle build, automated tests and in-game checks remain required.



## [0.37.2] - Worker Search Backoff

### Changed
- Cache unsuccessful lumberjack tree searches for five seconds (100 game ticks) instead of rescanning the full search volume every work interval. Successful searches clear the retry delay immediately.

### Validation
- Source committed; run build, tests and in-game checks locally.


## [0.37.1] - Quality Pass

### Added
- Lumberjacks replant supported species after harvesting a tree when the trunk base is empty and sits above dirt, grass block or podzol; the behavior is configurable and enabled by default.
- Lumberjacks deliver their partial load after 200 ticks since the last harvest, as well as at their existing cargo limit.
- Procedural resident names are generated for population-growth arrivals.
- Economy history can estimate days until depletion for food, wood and stone from the seven-day net trend; the Economy tab shows a food-stock forecast and a low-reserve warning.

### Validation
- Source committed; run build, tests and in-game checks locally.


## [0.37.0] - Achievement progression gates

### Added
- Achievement unlocks now respect prerequisite milestones, so later milestones cannot unlock before their foundational achievements.
- Periodic background checks evaluate achievement progress silently without sending a status snapshot every 10 seconds.
- Manual dashboard refreshes still send snapshots normally; save keys and network protocol remain unchanged.

### Validation
- Source committed; run build, tests and in-game achievement checks locally.


## [0.36.0] - Achievement progress display

### Added
- Locked achievement rows show a percentage computed from the current authoritative settlement snapshot.
- Compound milestones use their limiting component so progress is not overstated when one requirement is still far behind.
- Existing unlock history and protocol version 8 are unchanged.

### Validation
- Source committed; run build, tests and a client UI check locally.


## [0.35.0] - Background achievement progress

### Added
- Server periodically evaluates settlement milestones every 200 overworld ticks while the owner is online, regardless of dashboard visibility.
- Reuses the existing server-authoritative snapshot and persistent unlock logic; protocol version remains 8.

### Notes
- This periodic pass also sends a refreshed snapshot to the owner, so dashboard data stays current. Build and in-game behavior require local verification.


## [0.34.0] - Achievement history

### Added
- Achievement rows now display the saved in-game day of first unlock.
- Server sends stable achievement IDs and their persisted first-unlock day in the existing snapshot field; no protocol bump is needed.
- Historical unlock dates are read from world saved data and remain stable across later state changes.

### Validation
- Source changes committed; build, tests and in-game rendering require local verification.


## [0.33.0] - Persistent achievements

### Added
- Added stable IDs for all 21 achievement milestones.
- Persisted first-unlock world day in `CivitasSavedData`; unlocks survive reloads and later loss of a condition.
- Achievement conditions are evaluated server-side when the dashboard snapshot is generated; the client renders the saved unlock list.
- Bumped the Civitas network protocol to version 8 for the expanded snapshot.

### Validation
- Source changes committed; local build, tests and in-game save/reload behavior still require verification.


## [0.32.1] - Achievement accuracy pass

### Fixed
- Renamed misleading achievement labels: resource stock trends are now described as resource balance, not trade; settlement-level milestones no longer claim to measure territorial ownership.
- Resource-balance achievement uses a `long` sum to avoid integer overflow when combining seven-day deltas.
- Corrected the number and wording of achievement development areas in the UI.

### Validation
- Source change committed; local build, tests and in-game display still require verification.

## [0.32.0] - Living Settlement

### Interface and progression
- Added a Buildings tab with page navigation, registered building indices, cabin levels and upgrade material guidance.
- Added a scrollable achievement tree with 21 milestones across founding, population, crafts, construction, economy, trade, territory, workforce and settlement development.
- Achievement states are derived from the authoritative settlement snapshot; individual unlock persistence, gated prerequisites and reward grants are not included yet.

### Houses and construction
- Building records now persist upgrade level and rotation; old world data defaults to level 1 and rotation 0.
- Added owner-only `/civitas buildings upgrade <index>` for registered Civitas cabins.
- Level 2 adds a porch, supported fence railings, stairs and lantern. Level 3 adds a brick chimney through the roof.
- Upgrade placement is checked against loaded blocks, fluids, settlement influence and claimed chunks before resources are withdrawn.
- Upgrade costs use free warehouse stock only; construction reservations are protected. The upgrade has rollback handling for placed blocks and materials.
- Construction workers face the worksite and swing during the work cycle; lumberjacks visibly chop, and farmers perform a timed harvest before consuming a seed.
- Added blueprint tests for house-upgrade footprint bounds, unique positions and invalid parameters.

### Validation and limitations
- Alpha 0.32.0 has not been confirmed by a local Gradle build, automated test run or in-game test.
- Achievement unlocks are derived rather than individually persisted. The starter-village houses are not yet registered as upgradeable Civitas building records.
- Resident schedules, social behavior, rest and happiness simulation remain future work.
- Fire/fluid spread, mob griefing and third-party world changes are not comprehensively protected.

## [0.31.0] - Construction Integrity Checks

- Expanded blueprint tests across all building types and supported rotations.
- Added regression tests for legacy construction cancellation refunds and rotation normalization.
- Build and tests must be run locally before treating the release as verified.

## [0.30.0] - Territory Protection Hardening

- Claimed overworld chunks block non-owner player block edits and block interactions; warning messages are rate-limited.
- Explosions no longer remove blocks in claimed overworld chunks.
- Construction validates the complete rotated footprint before starting and rechecks territory before every layer.
