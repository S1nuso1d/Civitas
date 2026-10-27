package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla-inspired settlement dashboard with real Minecraft item icons. */
public final class SettlementScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 480;
    private static final int INNER_WIDTH = 330;
    private static final int BUTTON_WIDTH = 160;
    private static final int GAP = 10;

    private EditBox settlementName;
    private EditBox itemId;
    private EditBox amount;
    private EditBox orderIndex;
    private String pendingBuild = "";

    public SettlementScreen() {
        super(Component.translatable("screen.civitas.settlement"));
    }

    @Override
    protected void init() {
        iconPositions.clear();
        int panelLeft = (width - PANEL_WIDTH) / 2;
        int left = panelLeft + 15;
        int top = (height - PANEL_HEIGHT) / 2;
        int right = left + BUTTON_WIDTH + GAP;

        settlementName = new EditBox(font, left, top + 40, INNER_WIDTH, 20,
                Component.translatable("screen.civitas.settlement_name"));
        settlementName.setMaxLength(40);
        settlementName.setHint(Component.translatable("screen.civitas.name_hint"));
        addRenderableWidget(settlementName);

        addButton("   Основать поселение", left, top + 67, BUTTON_WIDTH, "civitas found", Items.BELL);
        addButton("   Переименовать", right, top + 67, BUTTON_WIDTH, "civitas rename", Items.NAME_TAG);

        addButton("   Обзор поселения", left, top + 108, BUTTON_WIDTH, "civitas status", Items.WRITABLE_BOOK);
        addButton("   Склад поселения", right, top + 108, BUTTON_WIDTH, "civitas storage", Items.CHEST);

        itemId = new EditBox(font, left, top + 148, 230, 20, Component.translatable("screen.civitas.item_id"));
        itemId.setMaxLength(128);
        itemId.setValue("minecraft:oak_log");
        itemId.setHint(Component.literal("minecraft:oak_log"));
        addRenderableWidget(itemId);

        amount = new EditBox(font, left + 240, top + 148, 90, 20, Component.translatable("screen.civitas.amount"));
        amount.setMaxLength(4);
        amount.setValue("16");
        amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(amount);

        addButton("   Сдать из руки", left, top + 175, BUTTON_WIDTH, "civitas deposit " + safeAmount(), Items.HOPPER);
        addButton("   Забрать со склада", right, top + 175, BUTTON_WIDTH, "civitas withdraw " + safeItemId() + " " + safeAmount(), Items.BARREL);

        addButton("   Жители и задачи", left, top + 216, BUTTON_WIDTH, "civitas workers", Items.VILLAGER_SPAWN_EGG);
        addButton("   Назначить лесоруба", right, top + 216, BUTTON_WIDTH, "civitas worker lumberjack", Items.IRON_AXE);
        addButton("   Назначить строителя", left, top + 242, BUTTON_WIDTH, "civitas worker builder", Items.IRON_PICKAXE);
        addButton("   Пауза лесоруба", right, top + 242, BUTTON_WIDTH, "civitas worker pause", Items.CLOCK);
        addButton("   Продолжить работу", left, top + 268, BUTTON_WIDTH, "civitas worker resume", Items.LIME_DYE);
        addButton("   Снять профессию", right, top + 268, BUTTON_WIDTH, "civitas worker clear", Items.BARRIER);

        addButton("   Список зданий", left, top + 294, BUTTON_WIDTH, "civitas buildings", Items.BRICKS);
        addButton("   Очередь стройки", right, top + 294, BUTTON_WIDTH, "civitas construction", Items.OAK_PLANKS);

        addButton("   Дом", left, top + 337, BUTTON_WIDTH, "civitas build cabin", Items.OAK_DOOR);
        addButton("   Ферма", right, top + 337, BUTTON_WIDTH, "civitas build farm", Items.WHEAT);
        addButton("   Мастерская", left, top + 363, INNER_WIDTH, "civitas build workshop", Items.CRAFTING_TABLE);

        orderIndex = new EditBox(font, left, top + 389, 55, 20, Component.literal("№"));
        orderIndex.setMaxLength(3);
        orderIndex.setValue("1");
        orderIndex.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(orderIndex);
        addButton("   Отменить заказ №", left + 65, top + 389, INNER_WIDTH - 65, "civitas construction cancel selected", Items.BARRIER);
        addRenderableWidget(Button.builder(Component.literal("Закрыть  [Esc]"), button -> onClose())
                .bounds(left, top + 412, INNER_WIDTH, 16).build());
    }

    private void addButton(String label, int x, int y, int width, String command, net.minecraft.world.item.Item icon) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            String actual = command;
            if (actual.equals("civitas construction cancel selected")) {
                actual = "civitas construction cancel " + safeOrderIndex();
            }
            if (actual.startsWith("civitas build ")) {
                if (!actual.equals(pendingBuild)) {
                    pendingBuild = actual;
                    return;
                }
                pendingBuild = "";
            }
            if (actual.equals("civitas found") || actual.equals("civitas rename")) {
                actual = actual + " " + safeName();
            } else if (actual.startsWith("civitas deposit ")) {
                actual = "civitas deposit " + safeAmount();
            } else if (actual.startsWith("civitas withdraw ")) {
                actual = "civitas withdraw " + safeItemId() + " " + safeAmount();
            }
            sendCommand(actual);
        }).bounds(x, y, width, 20).build());
        iconPositions.add(new IconPosition(x + 4, y + 2, new ItemStack(icon)));
    }

    private final java.util.List<IconPosition> iconPositions = new java.util.ArrayList<>();

    private record IconPosition(int x, int y, ItemStack stack) {}

    private String safeName() {
        String value = settlementName.getValue().trim().replace('\n', ' ').replace('\r', ' ');
        return value.isEmpty() ? "Новая деревня" : value;
    }

    private String safeItemId() {
        String value = itemId.getValue().trim();
        return value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? value : "minecraft:oak_log";
    }

    private String safeOrderIndex() {
        try { return Integer.toString(Math.max(1, Math.min(999, Integer.parseInt(orderIndex.getValue())))); }
        catch (NumberFormatException exception) { return "1"; }
    }

    private String safeAmount() {
        try {
            int value = Integer.parseInt(amount.getValue());
            return Integer.toString(Math.max(1, Math.min(2304, value)));
        } catch (NumberFormatException exception) {
            return "1";
        }
    }

    private static void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundChatCommandPacket(command));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // Pixel-like beveled frame inspired by vanilla inventory panels.
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF171717);
        graphics.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFFC6C6C6);
        graphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF373737);
        graphics.fill(left + 5, top + 5, left + PANEL_WIDTH - 5, top + PANEL_HEIGHT - 5, 0xFFC6C6C6);
        graphics.fill(left + 7, top + 7, left + PANEL_WIDTH - 7, top + 30, 0xFF555555);
        graphics.fill(left + 8, top + 8, left + PANEL_WIDTH - 8, top + 29, 0xFF292929);

        graphics.drawCenteredString(font, Component.literal("CIVITAS"), width / 2, top + 11, 0xFFFFD866);
        graphics.drawCenteredString(font, Component.literal("УПРАВЛЕНИЕ ПОСЕЛЕНИЕМ"), width / 2, top + 22, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("Название поселения"), left + 15, top + 31, 0xFF303030, false);
        drawSection(graphics, left + 15, top + 96, "ПОСЕЛЕНИЕ И СКЛАД");
        graphics.drawString(font, Component.literal("ID предмета"), left + 15, top + 136, 0xFF303030, false);
        graphics.drawString(font, Component.literal("Кол-во"), left + 255, top + 136, 0xFF303030, false);
        drawSection(graphics, left + 15, top + 203, "ЖИТЕЛИ И ЗАДАЧИ");
        drawSection(graphics, left + 15, top + 323, "СТРОИТЕЛЬСТВО · РЕСУРСЫ СО СКЛАДА");
        super.render(graphics, mouseX, mouseY, partialTick);
        for (IconPosition icon : iconPositions) graphics.renderItem(icon.stack(), icon.x(), icon.y());
        if (!pendingBuild.isEmpty()) {
            String preview = switch (pendingBuild) {
                case "civitas build cabin" -> "Дом: 32 доски, 8 брёвен, 4 стекла";
                case "civitas build farm" -> "Ферма: 49 земли, 24 семени, ведро воды";
                case "civitas build workshop" -> "Мастерская: 32 доски, 25 булыжника, верстак";
                default -> "Проверь стоимость и наличие материалов на складе";
            };
            graphics.fill(left + 8, top + 433, left + PANEL_WIDTH - 8, top + 464, 0xFF777777);
            graphics.fill(left + 9, top + 434, left + PANEL_WIDTH - 9, top + 463, 0xFFE7D8A8);
            graphics.drawCenteredString(font, Component.literal(preview), width / 2, top + 439, 0xFF303030);
            graphics.drawCenteredString(font, Component.literal("Нажми выбранную постройку ещё раз для заказа"), width / 2, top + 451, 0xFF8A2020);
        } else {
            graphics.drawCenteredString(font, Component.literal("Выбери постройку один раз для предпросмотра стоимости"), width / 2, top + 443, 0xFF303030);
            graphics.drawCenteredString(font, Component.literal("Повторное нажатие спишет ресурсы и поставит её в очередь"), width / 2, top + 455, 0xFF303030);
        }
        graphics.drawCenteredString(font, Component.literal("Ответы и ошибки отображаются в чате"), width / 2, top + 468, 0xFF303030);
    }

    private void drawSection(GuiGraphics graphics, int x, int y, String title) {
        graphics.fill(x, y, x + INNER_WIDTH, y + 1, 0xFF777777);
        graphics.drawString(font, Component.literal(title), x + 2, y - 8, 0xFF303030, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
