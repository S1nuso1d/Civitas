## [0.25.0] - Neighbor Resource Intelligence

### Added
- Diplomacy cards now show neighboring civilization timber, stone and iron reserves alongside population, territory, food, stability and reputation.
- The server includes the new stockpile fields in the authoritative diplomacy snapshot; the client does not infer or simulate resource values.
- Bumped the network protocol to version 6 because the diplomacy packet schema changed.
- The diplomacy row uses compact, width-bounded labels for smaller windows.

### Validation
- Build, tests and JAR packaging must be verified for this exact commit.
- Manual GUI layout and client/server compatibility checks remain required.

## [0.24.0] - Diplomacy Has Economic Consequences

### Added
- Diplomatic stances now affect the neighbor civilization's daily simulation rather than only trade eligibility.
- Alliances add food reserves, improve stability and lower the threshold for territorial expansion.
- Rivalry gradually reduces stability and can consume timber in larger territories.
- War reduces stability and food reserves and consumes timber and, for larger populations, iron.
- Effects are applied only on bounded daily simulation steps; no per-tick work was added.
- Added a unit test comparing allied and wartime outcomes from identical starting profiles.

### Validation
- Build, tests and JAR packaging must be verified for this exact commit.
- In-game balance and multiplayer behavior still require manual testing.

## [0.23.0] - Visit-able Neighbor Settlements

### Added
- Added persistent physical sites for neighboring civilizations.
- Visiting a neighbor creates a small vanilla-block settlement with two cabins, a central bell, work blocks and three persistent villagers.
- Candidate sites are checked for solid, dry ground and clear building volume before construction; existing solid structures are not cleared.
- Added a Visit button to diplomacy GUI and commands `/civitas visit <number>` and `/civitas return`.
- Return coordinates are stored in player persistent data; settlement site coordinates and generation state are stored in world SavedData.
- Added a save/load unit test for neighboring settlement coordinates.
- Bumped network protocol to 5.

### Validation
- Automated build and JAR packaging must be verified for the exact release commit.
- Manual tests are still required for different biomes, repeated visits, return flow and multiplayer.

## [0.22.0] - Diplomacy GUI

### Added
- Added a vanilla-style diplomacy screen reachable from the settlement dashboard.
- Civilization list shows relationship, population, territory, food, stability and trade reputation; list supports pagination.
- Stance changes are sent as typed packets and validated on the authoritative server by civilization UUID and settlement ownership.
- Alliances require trade reputation of at least 60; gifts and successful trade improve reputation, while war blocks caravan selection.
- Bumped network protocol to 4 for clients and servers using the new packet set.

### Validation
- Automated build and tests must pass for the exact release commit.
- Manual in-game and multiplayer checks are still required.

## [0.21.0] - Neighbor Civilizations

### Added
- Existing trade partners now have persistent civilization profiles: population, food, timber, stone, iron, territory and stability.
- Bounded abstract economy advances on in-game day boundaries without spawning remote villagers or loading distant chunks.
- Diplomacy commands: `/civitas diplomacy` and `/civitas diplomacy stance <index> <neutral|allied|rival|war>`.
- War suspends new caravans with the civilization; peaceful stances restore trade eligibility.
- Old 0.20.x saves migrate existing partners to civilization profiles automatically.
- Added unit tests for persistence, trade eligibility, bounded simulation and invalid saved values.

### Limitations
- Diplomacy currently uses commands; a dedicated GUI and physically visitable/generated neighbouring settlements are future work.
- Manual in-game and multiplayer testing is still required.

## [0.20.1] - Worker safety hotfix

### Fixed
- Expanded the protected starter-village footprint so the lumberjack does not select logs from houses or roofs as harvest targets.
- Protect logs close to registered buildings and to nearby construction blocks (planks, stairs, slabs, fences, walls, doors, trapdoors, beds and campfires).
- Ambiguous logs are now preserved instead of being harvested; this intentionally prioritizes player-built structures over uncertain tree detection.

### Validation
- Automated build and unit tests run in GitHub Actions.
- In-game confirmation is still required for starter-house protection and normal tree harvesting.

## [0.20.0] - Trade & Caravans

### Added
- Persistent partner civilizations, reputation, caravan lifecycle and bounded recent trade history.
- Deterministic trade offers based on partner identity, game day and settlement economy trends.
- Server-authoritative trading UI, warehouse exchanges and gifts.
- Caravan arrival, approach, waiting, departure and attack reaction.
- Common configuration for arrival interval, visit duration and reputation rewards.

### Compatibility
- Existing 0.19.0 worlds keep settlement, construction, farmer, food and economy data.
- Trade fields are stored in separate optional NBT keys; existing warehouse inventory is not rewritten.

## [0.19.0] - Agriculture & Population

### Added
- Farmer profession with persistent multi-item crop cargo, safe atomic warehouse delivery, saved crop reservations and cargo drops on death.
- Nutrition registry and common config for daily food consumption, growth interval, reserves, farmer search radius and history length.
- Daily food upkeep, starvation status, free-bed and food-reserve gated population growth.
- Persistent rolling economy history with daily wood/stone/food/population values, food production/consumption and a bounded event feed.
- Overview food/housing indicators and a dedicated Economy tab.
- Unit tests for nutrition, cargo transactions, reservations, economy history and network snapshots.

### Compatibility
- Existing world saves can omit the new NBT fields; history starts empty and old farmer cargo keys are migrated when possible.
- Snapshot wire format now uses protocol version 3.
