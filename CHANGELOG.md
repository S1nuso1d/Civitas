# Changelog

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
- Добавлен корневой `src/main/resources/pack.mcmeta` с `pack_format: 34` для Minecraft 1.21–1.21.1. Это устраняет отсутствие метаданных, из-за которого Forge показывал предупреждение о наборе ресурсов.
- Повышена версия проекта до 0.2.2; обновлены пути JAR и инструкции.


## Alpha 0.2.1
- Исправлена ошибка компиляции `Item#getMaxStackSize()` для Minecraft/Forge 1.21.1: используется `Item#getDefaultMaxStackSize()`.
- Версия проекта и инструкции обновлены до 0.2.1.
- Исправлена кодировка скриптов запуска Windows для более читаемого вывода русских сообщений.

## Alpha 0.2.0
- Перенос с NeoForge на Forge 1.21.1.
- Добавлены сохраняемое состояние одного поселения, команда создания и виртуальный склад.
