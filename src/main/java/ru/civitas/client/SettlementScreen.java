package ru.civitas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.civitas.network.CivitasNetwork;
import ru.civitas.network.SettlementSnapshot;
import ru.civitas.world.BuildingDefinition;

import java.util.ArrayList;
import java.util.List;

/** Responsive, vanilla-inspired settlement dashboard backed by server snapshots. */
public final class SettlementScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 520;
    private static final int MAX_PANEL_HEIGHT = 420;
    private static final int PAD = 12;

    private enum Tab {
        OVERVIEW("Обзор"),
        RESIDENTS("Жители"),
        CONSTRUCTION("Стройка"),
        STORAGE("Склад");

        private final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private record IconPosition(int x, int y, ItemStack stack) {}
    private record RenderLine(int x, int y, String text, int color) {}

    private final List<IconPosition> icons = new ArrayList<>();
    private final List<RenderLine> renderLines = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentBottom;

    private Tab activeTab = Tab.OVERVIEW;
    private SettlementSnapshot snapshot;
    private EditBox settlementName;
    private EditBox itemId;
    private EditBox amount;
    private EditBox orderIndex;

    private String settlementNameValue = "Новая деревня";
    private String itemIdValue = "minecraft:oak_log";
    private String amountValue = "16";
    private String orderIndexValue = "1";
    private String pendingBuild = "";

    private int buildOffset;
    private int listOffset;
    private int residentControlPage;
    private boolean showingQueue;
    private int ticksSinceRefresh;

    public SettlementScreen() {
        super(Component.translatable("screen.civitas.settlement"));
        snapshot = ClientNetworkHandler.latestSnapshot();
    }

    @Override
    protected void init() {
        icons.clear();

        // The panel follows the current viewport rather than assuming 360×480.
        panelWidth = Math.max(100, Math.min(MAX_PANEL_WIDTH, width - 8));
        panelHeight = Math.max(150, Math.min(MAX_PANEL_HEIGHT, height - 8));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentX = panelX + PAD;
        contentY = panelY + 64;
        contentWidth = panelWidth - PAD * 2;
        contentBottom = panelY + panelHeight - 42;

        int gap = 3;
        int tabWidth = (contentWidth - gap * 3) / 4;
        int x = contentX;
        int tabY = panelY + 35;
        for (Tab tab : Tab.values()) {
            final Tab selected = tab;
            String tabLabel = tabWidth < 52 ? switch (tab) {
                case OVERVIEW -> "Обз.";
                case RESIDENTS -> "Жит.";
                case CONSTRUCTION -> "Стр.";
                case STORAGE -> "Скл.";
            } : tab.title;
            if (tab == activeTab) tabLabel = "» " + tabLabel;
            addRenderableWidget(Button.builder(Component.literal(tabLabel), button -> switchTab(selected))
                    .bounds(x, tabY, tabWidth, 20).build());
            x += tabWidth + gap;
        }

        switch (activeTab) {
            case OVERVIEW -> initOverview();
            case RESIDENTS -> initResidents();
            case CONSTRUCTION -> initConstruction();
            case STORAGE -> initStorage();
        }

        int footerButtonY = panelY + panelHeight - 30;
        int leftButtonWidth = (contentWidth - 4) / 2;
        addButton("Обновить", contentX, footerButtonY, leftButtonWidth, 20,
                CivitasNetwork::requestSnapshot, Items.COMPASS);
        addButton("Закрыть", contentX + leftButtonWidth + 4, footerButtonY,
                contentWidth - leftButtonWidth - 4, 20, this::onClose, Items.BARRIER);

        CivitasNetwork.requestSnapshot();
    }

    private void initOverview() {
        int inputY = contentY + 13;
        int renameWidth = Math.min(98, Math.max(70, contentWidth / 3));
        int inputWidth = Math.max(70, contentWidth - renameWidth - 4);
        settlementName = new EditBox(font, contentX, inputY, inputWidth, 20,
                Component.literal("Название поселения"));
        settlementName.setMaxLength(40);
        settlementName.setValue(settlementNameValue);
        settlementName.setHint(Component.literal("Название поселения"));
        addRenderableWidget(settlementName);

        String label = snapshot != null && snapshot.founded() ? "Переименовать" : "Основать";
        addButton(label, contentX + inputWidth + 4, inputY, renameWidth, 20, () -> {
            rememberInputs();
            CivitasNetwork.sendAction(
                    snapshot != null && snapshot.founded()
                            ? CivitasNetwork.Action.RENAME : CivitasNetwork.Action.FOUND,
                    settlementNameValue, 0);
        }, Items.NAME_TAG);
    }

    private void initResidents() {
        int half = (contentWidth - 4) / 2;
        addButton("Назначение", contentX, contentY, half, 20,
                () -> { residentControlPage = 0; listOffset = 0; rebuildWidgets(); }, null);
        addButton("Работа", contentX + half + 4, contentY, contentWidth - half - 4, 20,
                () -> { residentControlPage = 1; listOffset = 0; rebuildWidgets(); }, null);

        int actionY = contentY + 23;
        if (residentControlPage == 0) {
            addButton("Лесоруб", contentX, actionY, half, 20,
                    () -> sendAction(CivitasNetwork.Action.ASSIGN_LUMBERJACK, "", 0), Items.IRON_AXE);
            addButton("Строитель", contentX + half + 4, actionY, contentWidth - half - 4, 20,
                    () -> sendAction(CivitasNetwork.Action.ASSIGN_BUILDER, "", 0), Items.IRON_PICKAXE);
        } else {
            int third = (contentWidth - 8) / 3;
            addButton("Пауза", contentX, actionY, third, 20,
                    () -> sendAction(CivitasNetwork.Action.PAUSE_LUMBERJACK, "", 0), Items.CLOCK);
            addButton("Старт", contentX + third + 4, actionY, third, 20,
                    () -> sendAction(CivitasNetwork.Action.RESUME_LUMBERJACK, "", 0), Items.LIME_DYE);
            addButton("Снять", contentX + (third + 4) * 2, actionY,
                    contentWidth - (third + 4) * 2, 20,
                    () -> sendAction(CivitasNetwork.Action.CLEAR_LUMBERJACK, "", 0), Items.BARRIER);
        }

        int navY = contentY + 44;
        addListNavigation(snapshot == null ? 0 : snapshot.residents().size(), navY);
    }

    private void initConstruction() {
        int navY = contentY;
        if (showingQueue) {
            addButton("← Здания", contentX, navY, Math.min(82, contentWidth / 2), 18,
                    () -> { showingQueue = false; buildOffset = 0; listOffset = 0; rebuildWidgets(); }, null);

            List<String> orders = snapshot == null ? List.of() : snapshot.construction();
            int cancelY = contentBottom - 22;
            int rows = Math.max(1, (cancelY - (navY + 24) - 2) / 13);
            int maxOffset = Math.max(0, orders.size() - rows);
            listOffset = Math.min(listOffset, maxOffset);
            int arrowsX = contentX + contentWidth - 54;
            addButton("←", arrowsX, navY, 24, 18,
                    () -> { listOffset = Math.max(0, listOffset - rows); rebuildWidgets(); }, null);
            addButton("→", arrowsX + 28, navY, 24, 18,
                    () -> { listOffset = Math.min(maxOffset, listOffset + rows); rebuildWidgets(); }, null);

            orderIndex = new EditBox(font, contentX, cancelY, 44, 20, Component.literal("№"));
            orderIndex.setMaxLength(3);
            orderIndex.setValue(orderIndexValue);
            orderIndex.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            addRenderableWidget(orderIndex);
            addButton("Отменить заказ №", contentX + 49, cancelY,
                    Math.max(70, contentWidth - 49), 20, () -> {
                        rememberInputs();
                        sendAction(CivitasNetwork.Action.CANCEL_ORDER, "",
                                safeInteger(orderIndexValue, 1, 999, 1));
                    }, Items.BARRIER);
        } else {
            List<BuildingDefinition> definitions = BuildingDefinition.all();
            int rows = Math.max(1, Math.min(3, visibleRows(navY + 22, 28)));
            buildOffset = Math.min(buildOffset, Math.max(0, definitions.size() - 1));

            addButton("←", contentX, navY, 26, 18,
                    () -> { buildOffset = Math.max(0, buildOffset - rows); rebuildWidgets(); }, null);
            addButton("→", contentX + 30, navY, 26, 18,
                    () -> { buildOffset = Math.min(Math.max(0, definitions.size() - rows), buildOffset + rows); rebuildWidgets(); }, null);
            addButton("Очередь", contentX + contentWidth - 70, navY, 70, 18,
                    () -> { showingQueue = true; listOffset = 0; rebuildWidgets(); }, Items.OAK_PLANKS);

            int buttonWidth = Math.min(104, Math.max(78, contentWidth / 2));
            for (int i = buildOffset; i < Math.min(definitions.size(), buildOffset + rows); i++) {
                BuildingDefinition definition = definitions.get(i);
                int rowY = navY + 22 + (i - buildOffset) * 28;
                String command = "civitas build " + definition.id();
                addButton(definition.displayName(), contentX, rowY, buttonWidth, 22, () -> {
                    if (!command.equals(pendingBuild)) {
                        pendingBuild = command;
                        return;
                    }
                    pendingBuild = "";
                    sendAction(CivitasNetwork.Action.BUILD, definition.id(), 0);
                }, buildingIcon(definition.type()));
            }
        }
    }

    private void initStorage() {
        int amountWidth = Math.min(72, Math.max(40, contentWidth / 4));
        int itemWidth = Math.max(40, contentWidth - amountWidth - 6);
        itemId = new EditBox(font, contentX, contentY + 13, itemWidth, 20, Component.literal("ID предмета"));
        itemId.setMaxLength(128);
        itemId.setValue(itemIdValue);
        itemId.setHint(Component.literal("minecraft:oak_log"));
        addRenderableWidget(itemId);

        amount = new EditBox(font, contentX + itemWidth + 6, contentY + 13, amountWidth, 20,
                Component.literal("Количество"));
        amount.setMaxLength(4);
        amount.setValue(amountValue);
        amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(amount);

        int half = (contentWidth - 4) / 2;
        addButton("Сдать", contentX, contentY + 37, half, 20, () -> {
            rememberInputs();
            sendAction(CivitasNetwork.Action.DEPOSIT, "", safeInteger(amountValue, 1, 2304, 1));
        }, Items.HOPPER);
        addButton("Забрать", contentX + half + 4, contentY + 37,
                contentWidth - half - 4, 20, () -> {
                    rememberInputs();
                    sendAction(CivitasNetwork.Action.WITHDRAW, safeItemId(),
                            safeInteger(amountValue, 1, 2304, 1));
                }, Items.BARREL);

        addListNavigation(snapshot == null ? 0 : snapshot.storage().size(), contentY + 40);
    }

    private void addListNavigation(int itemCount, int y) {
        int rowStart = y + 17;
        int rows = visibleRows(rowStart, 12);
        int maxOffset = Math.max(0, itemCount - rows);
        int arrowsX = contentX + contentWidth - 54;
        addButton("←", arrowsX, y, 24, 16,
                () -> { listOffset = Math.max(0, listOffset - rows); rebuildWidgets(); }, null);
        addButton("→", arrowsX + 28, y, 24, 16,
                () -> { listOffset = Math.min(maxOffset, listOffset + rows); rebuildWidgets(); }, null);
    }

    private Item buildingIcon(BuildingDefinition.Type type) {
        return switch (type) {
            case CABIN -> Items.OAK_DOOR;
            case FARM -> Items.WHEAT;
            case WORKSHOP -> Items.CRAFTING_TABLE;
        };
    }

    private void addButton(String label, int x, int y, int buttonWidth, int buttonHeight,
                           Runnable action, Item icon) {
        String buttonLabel = icon == null ? label : "   " + label;
        addRenderableWidget(Button.builder(Component.literal(buttonLabel), button -> action.run())
                .bounds(x, y, buttonWidth, buttonHeight).build());
        if (icon != null) {
            icons.add(new IconPosition(x + 3, y + Math.max(1, (buttonHeight - 16) / 2),
                    new ItemStack(icon)));
        }
    }

    private void switchTab(Tab tab) {
        rememberInputs();
        activeTab = tab;
        listOffset = 0;
        pendingBuild = "";
        rebuildWidgets();
    }

    private void sendAction(CivitasNetwork.Action action, String value, int number) {
        rememberInputs();
        CivitasNetwork.sendAction(action, value, number);
    }

    private void rememberInputs() {
        if (settlementName != null) settlementNameValue = cleanText(settlementName.getValue(), 40);
        if (itemId != null) itemIdValue = itemId.getValue().trim();
        if (amount != null) amountValue = amount.getValue();
        if (orderIndex != null) orderIndexValue = orderIndex.getValue();
    }

    private static String cleanText(String value, int maxLength) {
        String cleaned = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength).trim() : cleaned;
    }

    private String safeItemId() {
        String value = itemIdValue == null ? "" : itemIdValue.trim();
        return value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? value : "minecraft:oak_log";
    }

    private static int safeInteger(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int visibleRows(int startY, int rowHeight) {
        return Math.max(1, (contentBottom - startY) / rowHeight);
    }

    private void drawLine(int y, String text, int color, int maxWidth) {
        drawLineAt(contentX, y, text, color, maxWidth);
    }

    private void drawLineAt(int x, int y, String text, int color, int maxWidth) {
        String fitted = text == null ? "" : text;
        while (font.width(fitted) > maxWidth && fitted.length() > 1) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        if (!fitted.equals(text) && fitted.length() > 1) {
            fitted = fitted.substring(0, fitted.length() - 1) + "…";
        }
        if (y <= contentBottom) renderLines.add(new RenderLine(x, y, fitted, color));
    }

    public void acceptSnapshot(SettlementSnapshot nextSnapshot) {
        snapshot = nextSnapshot;
        if (settlementName == null || !settlementName.isFocused()) {
            settlementNameValue = nextSnapshot.settlementName();
            if (settlementName != null) settlementName.setValue(settlementNameValue);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticksSinceRefresh >= 40) {
            ticksSinceRefresh = 0;
            CivitasNetwork.requestSnapshot();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderLines.clear();

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF171717);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelY + panelHeight - 2, 0xFFC6C6C6);
        graphics.fill(panelX + 4, panelY + 4, panelX + panelWidth - 4, panelY + panelHeight - 4, 0xFF373737);
        graphics.fill(panelX + 5, panelY + 5, panelX + panelWidth - 5, panelY + panelHeight - 5, 0xFFC6C6C6);
        graphics.fill(panelX + 7, panelY + 7, panelX + panelWidth - 7, panelY + 30, 0xFF555555);
        graphics.fill(panelX + 8, panelY + 8, panelX + panelWidth - 8, panelY + 29, 0xFF292929);
        graphics.drawCenteredString(font, Component.literal("CIVITAS"), panelX + panelWidth / 2, panelY + 9, 0xFFFFD866);

        String title = snapshot != null && snapshot.founded()
                ? snapshot.settlementName() : "УПРАВЛЕНИЕ ПОСЕЛЕНИЕМ";
        graphics.drawCenteredString(font, Component.literal(title),
                panelX + panelWidth / 2, panelY + 20, 0xFFFFFFFF);

        switch (activeTab) {
            case OVERVIEW -> renderOverview();
            case RESIDENTS -> renderResidents();
            case CONSTRUCTION -> renderConstruction();
            case STORAGE -> renderStorage();
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        for (IconPosition icon : icons) graphics.renderItem(icon.stack(), icon.x(), icon.y());
        for (RenderLine line : renderLines) {
            graphics.drawString(font, Component.literal(line.text()), line.x(), line.y(), line.color(), false);
        }
        drawFooter(graphics, snapshot == null ? "Запрашиваю состояние сервера…" : snapshot.statusMessage());
    }

    private void renderOverview() {
        if (snapshot == null) {
            drawLine(contentY + 40, "Запрашиваю состояние поселения…", 0xFF303030, contentWidth);
            return;
        }
        List<String> lines = List.of(
                "Уровень: " + snapshot.level() + " | Население: " + snapshot.population(),
                "Жители: " + snapshot.residentCount() + " | Лесорубы: " + snapshot.lumberjacks()
                        + " | Строители: " + snapshot.builders() + " | Без роли: " + snapshot.idleResidents(),
                "Здания: дома " + snapshot.cabinCount() + " | фермы " + snapshot.farmCount()
                        + " | мастерские " + snapshot.workshopCount(),
                "Строительных заказов: " + snapshot.orderCount(),
                "Склад: " + snapshot.warehouseItemCount() + " предметов; занято "
                        + snapshot.warehouseOccupiedSlots() + "/" + snapshot.warehouseSlots() + " слотов"
        );
        int startY = contentY + 40;
        int rows = visibleRows(startY, 14);
        for (int i = 0; i < Math.min(rows, lines.size()); i++) {
            drawLine(startY + i * 14, lines.get(i), 0xFF303030, contentWidth);
        }
    }

    private void renderResidents() {
        if (snapshot == null) {
            drawLine(contentY + 65, "Запрашиваю жителей…", 0xFF303030, contentWidth);
            return;
        }
        drawLine(contentY + 45, "Жителей рядом: " + snapshot.residentCount(),
                0xFF303030, Math.max(50, contentWidth - 58));
        List<String> lines = snapshot.residents();
        int startY = contentY + 61;
        int rows = visibleRows(startY, 12);
        listOffset = Math.min(listOffset, Math.max(0, lines.size() - rows));
        for (int i = 0; i < rows && listOffset + i < lines.size(); i++) {
            drawLine(startY + i * 12, lines.get(listOffset + i), 0xFF303030, contentWidth);
        }
        if (lines.isEmpty()) drawLine(startY, "Жители поблизости не найдены.", 0xFF777777, contentWidth);
    }

    private void renderConstruction() {
        if (showingQueue) {
            if (snapshot == null) return;
            List<String> orders = snapshot.construction();
            int startY = contentY + 24;
            int cancelY = contentBottom - 22;
            int rows = Math.max(1, (cancelY - startY - 2) / 13);
            listOffset = Math.min(listOffset, Math.max(0, orders.size() - rows));
            for (int i = 0; i < rows && listOffset + i < orders.size(); i++) {
                drawLine(startY + i * 13, orders.get(listOffset + i), 0xFF303030, contentWidth);
            }
            if (orders.isEmpty()) drawLine(startY, "Очередь строительства пуста.", 0xFF777777, contentWidth);
            return;
        }

        List<BuildingDefinition> definitions = BuildingDefinition.all();
        int rows = Math.max(1, Math.min(3, visibleRows(contentY + 22, 28)));
        int buttonWidth = Math.min(104, Math.max(78, contentWidth / 2));
        for (int i = buildOffset; i < Math.min(definitions.size(), buildOffset + rows); i++) {
            BuildingDefinition definition = definitions.get(i);
            int y = contentY + 28 + (i - buildOffset) * 28;
            String cost = definition.describeCosts();
            if (("civitas build " + definition.id()).equals(pendingBuild)) cost = "Выбрано: " + cost;
            drawLineAt(contentX + buttonWidth + 8, y, cost, 0xFF303030,
                    Math.max(24, contentWidth - buttonWidth - 8));
        }
        if (!pendingBuild.isEmpty()) {
            String selectedId = pendingBuild.substring("civitas build ".length());
            String preview = BuildingDefinition.find(selectedId)
                    .map(definition -> "Подтверди: " + definition.displayName() + " — " + definition.describeCosts())
                    .orElse("Повтори нажатие для подтверждения");
            drawLine(contentBottom - 12, preview, 0xFF7A2828, contentWidth);
        } else {
            drawLine(contentBottom - 12, "Выбери здание и нажми ещё раз для подтверждения.",
                    0xFF303030, contentWidth);
        }
    }

    private void renderStorage() {
        if (snapshot == null) return;
        drawLine(contentY, "ID предмета", 0xFF303030, contentWidth);
        drawLine(contentY + 45, "Склад: " + snapshot.warehouseOccupiedSlots()
                + "/" + snapshot.warehouseSlots() + " слотов", 0xFF303030,
                Math.max(40, contentWidth - 58));
        List<String> lines = snapshot.storage();
        int startY = contentY + 62;
        int rows = visibleRows(startY, 12);
        listOffset = Math.min(listOffset, Math.max(0, lines.size() - rows));
        for (int i = 0; i < rows && listOffset + i < lines.size(); i++) {
            drawLine(startY + i * 12, lines.get(listOffset + i), 0xFF303030, contentWidth);
        }
        if (lines.isEmpty()) drawLine(startY, "В складе нет предметов.", 0xFF777777, contentWidth);
    }

    private void drawFooter(GuiGraphics graphics, String text) {
        String fitted = text == null ? "" : text;
        int maxWidth = panelWidth - 18;
        while (font.width(fitted) > maxWidth && fitted.length() > 1) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        if (fitted.length() < (text == null ? 0 : text.length()) && fitted.length() > 1) {
            fitted = fitted.substring(0, fitted.length() - 1) + "…";
        }
        graphics.drawCenteredString(font, Component.literal(fitted),
                panelX + panelWidth / 2, panelY + panelHeight - 9, 0xFF303030);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        rememberInputs();
        super.onClose();
    }
}
