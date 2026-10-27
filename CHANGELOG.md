# Changelog

## Alpha 0.3.1 — starter village

- Added a one-time starter village generator near the world spawn.
- Added four houses, beds, work-site blocks, paths, lighting, a bell and four vanilla villagers.
- Added starter resources to the physical warehouse.
- Persisted village coordinates to world data and prevented regeneration on later logins.
- First-time players are moved to the village; respawn in the Overworld or other dimensions returns them to the village.
- Automatically found the initial settlement at the village center when no settlement exists.
- CI and in-game verification are pending for this revision.

## Alpha 0.3.0 — in progress

- Added a 27-slot physical warehouse block entity.
- Added a server-side container menu and client-side screen registration.
- Added Shift-click transfer between warehouse and player inventory.
- Added owner/operator checks before opening the physical warehouse.
- Added a safe first-pass migration from Alpha 0.2 virtual resources into available physical slots.
- Prevented integer overflow when adding resources to the legacy virtual store.
- GitHub Actions Gradle build succeeded on commit `9798485879cce462cab2d7d52beb72bede929411`; artifact `civitas-0.3.0-forge-1.21.1` was uploaded.
- Manual in-game tests for persistence, multiplayer permissions and migration are still pending.

## Alpha 0.2.7

- Added the Settlement Warehouse to Minecraft's Functional Blocks creative tab.
- Updated the build script, documentation, and CI artifact name.
- GitHub Actions build completed successfully for commit `b593e4f8b79a59fd9fdce96160ead41bc69fe47f`.
- The warehouse was a management-panel entry point without a slot-based container.

## Alpha 0.2.6

- Added a placeable Settlement Warehouse block using Minecraft's barrel textures.
- Interacting with the warehouse opened the Civitas management panel.
- Added English and Russian names for the warehouse.

## Alpha 0.2.5

- Added rename, deposit, and withdraw actions to the in-game management panel.
- Added input fields for settlement name, item ID, and amount.
- Added input validation and server-side command checks.

## Alpha 0.2.4

- Added the first settlement management screen, opened by using the Civilization Codex.
- Added UI actions to found a settlement, view status, inspect storage, and view help.

## Alpha 0.2.3

- Fixed Windows build and launch scripts to use relative paths, CRLF line endings, and ASCII console messages.

## Alpha 0.2.2

- Added `pack.mcmeta` for Minecraft 1.21.1.

## Alpha 0.2.1

- Fixed the item maximum-stack-size API call for Minecraft/Forge 1.21.1.

## Alpha 0.2.0

- Moved the project to Forge 1.21.1.
- Added persistent settlement metadata, settlement founding, and virtual storage.
