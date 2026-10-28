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
