# Changelog

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
