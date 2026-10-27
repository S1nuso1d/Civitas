## Alpha 0.6.0 — строительная система\n\n- Added three placeable settlement structures: cabin, farm and workshop.\n- Building costs are charged from the same physical warehouse used by the inventory UI and storage commands.\n- Build sites are checked before resources are removed; occupied terrain and insufficient materials abort the build.\n- Added building buttons to the settlement management screen.\n- GitHub Actions compilation and manual in-game checks are pending.\n\n# Changelog

## Alpha 0.7.0 — сохранённая очередь строительства

- Building commands now charge the physical warehouse once and enqueue construction instead of placing the entire structure immediately.
- Cabin, farm and workshop are placed layer by layer by a server-side construction tick handler.
- Queue entries and progress are saved in world data and survive reloads.
- Added `/civitas construction` and a settlement-panel button to inspect active orders and layer progress.
- CI and in-game verification are pending.



## Alpha 0.6.1 — постоянный реестр зданий

- Added a world-persistent building registry with type and coordinates stored in Civitas SavedData.
- Added `/civitas buildings` and a settlement-panel button to inspect registered buildings.
- Prevented new registered structures from being placed too close to an already registered building.
- GitHub Actions compilation and manual in-game checks are pending for this revision.


## Alpha 0.5.0 — управление задачами работников

- Added persistent pause/resume controls for the lumberjack. Pausing stops navigation but preserves the role and carried resources.
- Added safe role removal: a lumberjack cannot be unassigned while carrying logs.
- Expanded the worker list with task-state and blocking-issue information.
- Added diagnostics for a missing warehouse, no nearby trees, unsafe work positions and a full warehouse.
- Added pause, resume and role-removal controls to the settlement management screen.
- GitHub Actions compilation and manual in-game checks are pending for this revision.

## Alpha 0.4.2 — worker management UI

- Added `/civitas workers` to list villagers near the starter settlement and display their Civitas roles.
- Added `/civitas worker lumberjack` to assign the nearest villager as lumberjack and clear duplicate lumberjack roles nearby.
- Added worker-management buttons to the in-game settlement management screen.
- CI build and manual gameplay tests for this revision are pending.

## Alpha 0.4.1 — unified warehouse and worker recovery

- Unified `/civitas storage`, `/civitas deposit`, and `/civitas withdraw` with the 27-slot physical warehouse used by the GUI.
- Added capacity checks before depositing and availability checks before withdrawing to prevent partial or lost transfers.
- Legacy virtual resources are migrated into the physical inventory when warehouse commands are used.
- Improved lumberjack recovery: if no safe work position is available, a worker carrying logs attempts to return to the warehouse.
- GitHub Actions build succeeded on commit `6dbf3a444a688b75cc83ce872d0b57de4c226edd`: https://github.com/S1nuso1d/Civitas/actions/runs/40601663898

## Alpha 0.4.0 — starter village and first worker

- Added a persistent starter village and set the world's spawn point to its square.
- Added player respawn redirection to the village.
- Added starter resources to the physical warehouse.
- Added a persistent lumberjack role on a vanilla villager.
- Added a first worker loop: search nearby tree logs, navigate, chop with a short delay, carry up to eight logs and deposit them into the physical warehouse.
- Added migration for villages created by earlier Civitas versions.
- GitHub Actions Gradle build succeeded on commit `2b75e0a47592e016f4933d62ee160695895b467e`; artifact `civitas-0.4.0-forge-1.21.1` was uploaded: https://github.com/S1nuso1d/Civitas/actions/runs/40600446312
- Manual in-game verification of pathfinding, chopping, delivery and respawn is still pending.

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


## Alpha 0.8.0
- Added builder profession via `/civitas worker builder` and the settlement GUI.
- A real villager walks to the next queued site and advances one layer every three seconds while nearby.
- Construction pauses if no builder is assigned instead of placing blocks automatically.
- Site selection skips registered buildings and reserved queued sites.


## Alpha 0.9.0 — interface overhaul
- Rebuilt the settlement dashboard around a compact vanilla-inspired pixel UI.
- Added real Minecraft item icons to action buttons and grouped actions into settlement/storage, residents/tasks, and construction sections.
- Reworked the warehouse screen to match the vanilla inventory bevel and recessed slot look.
- Updated the CI artifact name to `civitas-0.9.0-forge-1.21.1`.


## Alpha 0.9.1 — безопасная отмена строительства
- Added a dashboard action and `/civitas construction cancel` to cancel the first queued order before construction begins.
- Refunds the full original material cost to the player inventory, dropping overflow nearby.
- Started orders are protected from cancellation to avoid leaving partial structures and ambiguous refunds.


## Alpha 0.9.2 — предпросмотр и подтверждение заказа
- Added a two-step building action: first click previews required resources, second click confirms the order.
- Choosing another building replaces the pending selection; no resources are spent on the first click.
- The server remains responsible for validating stock and charging materials.


## Alpha 0.9.3 — отмена выбранного заказа
- Added indexed cancellation: `/civitas construction cancel <number>`.
- Added a queue index field to the dashboard, so players can target a specific not-yet-started order.
- Full refunds are retained; partially constructed orders cannot be cancelled.
