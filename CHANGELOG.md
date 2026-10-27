# Changelog

## Alpha 0.2.7

- Added the Settlement Warehouse to Minecraft's Functional Blocks creative tab so it can be obtained without `/give`.
- Updated the build script and CI artifact name to `0.2.7`.

## Alpha 0.2.6

- Added a placeable Settlement Warehouse block using Minecraft's barrel textures.
- Interacting with the warehouse opens the Civitas management panel.
- Added English and Russian names for the warehouse.
- Updated build output and CI artifact version to 0.2.6.

## Alpha 0.2.5

## 0.2.5 — Expanded settlement management UI
- Added rename, deposit, and withdraw actions to the in-game management panel.
- Added input fields for a settlement name, resource item ID, and amount.
- Added client-side validation for item IDs and quantity bounds; server-side command checks remain authoritative.
- Added a GitHub Actions workflow to compile the Forge mod on pushes and pull requests.
- Updated README and installation instructions to identify the 0.2.5 artifact.

## 0.2.4 — In-game management panel
- Added the first Civitas settlement management screen, opened by using the Civilization Codex.
- Added UI actions to found a settlement, view settlement status, open storage information, and view help without manually typing commands.
- Kept the existing server-side command validation and persistent settlement data.
- This is an initial UI shell; settlement values are still returned through chat and a fully synchronized live dashboard is planned next.

# Журнал изменений Civitas

## Alpha 0.2.3
- Исправлены `build.bat` и `run-client.bat`: запуск PowerShell выполняется из корня проекта через относительный путь.
- Все `.bat`-скрипты записаны с Windows CRLF-переносами строк.
- Сообщения Windows-скриптов заменены на ASCII, чтобы избежать испорченной кодировки в `cmd.exe`.
- Обновлены версия проекта, имя JAR и инструкция сборки.

## Alpha 0.2.2
- Добавлен корневой `src/main/resources/pack.mcmeta` с `pack_format: 34` для Minecraft 1.21–1.21.1.
- Повышена версия проекта до 0.2.2; обновлены пути JAR и инструкции.

## Alpha 0.2.1
- Исправлена ошибка компиляции `Item#getMaxStackSize()` для Minecraft/Forge 1.21.1.
- Версия проекта и инструкции обновлены до 0.2.1.

## Alpha 0.2.0
- Перенос с NeoForge на Forge 1.21.1.
- Добавлены сохраняемое состояние одного поселения, команда создания и виртуальный склад.
