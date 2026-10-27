# Changelog

## Alpha 0.2.7

- Added the Settlement Warehouse to Minecraft's Functional Blocks creative tab.
- Updated the build script, documentation, and CI artifact name.
- GitHub Actions build completed successfully for commit `b593e4f8b79a59fd9fdce96160ead41bc69fe47f`; the artifact is available from the Actions run.
- The warehouse is still a management-panel entry point, not a slot-based container.

## Alpha 0.2.6

- Added a placeable Settlement Warehouse block using Minecraft's barrel textures.
- Interacting with the warehouse opens the Civitas management panel.
- Added English and Russian names for the warehouse.

## Alpha 0.2.5

- Added rename, deposit, and withdraw actions to the in-game management panel.
- Added input fields for a settlement name, resource item ID, and amount.
- Added client-side validation for item IDs and quantity bounds; server-side command checks remain authoritative.
- Added GitHub Actions compilation on pushes and pull requests.

## Alpha 0.2.4

- Added the first Civitas settlement management screen, opened by using the Civilization Codex.
- Added UI actions to found a settlement, view status, inspect storage, and view help.

## Alpha 0.2.3

- Fixed Windows build and launch scripts to use relative paths, CRLF line endings, and ASCII console messages.

## Alpha 0.2.2

- Added `pack.mcmeta` with `pack_format: 34` for Minecraft 1.21–1.21.1.

## Alpha 0.2.1

- Fixed the item maximum-stack-size API call for Minecraft/Forge 1.21.1.

## Alpha 0.2.0

- Moved the project to Forge 1.21.1.
- Added persistent settlement metadata, settlement founding, and virtual storage.
