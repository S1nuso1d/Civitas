package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

/**
 * In-game management panel. Buttons submit actions to the server so players
 * can manage the settlement without manually typing Civitas commands.
 */
public final class SettlementScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 370;

    private EditBox settlementName;
    private EditBox itemId;
    private EditBox amount;

    public SettlementScreen() {
        super(Component.literal("Civitas — управление поселением"));
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2 + 15;
        int top = (this.height - PANEL_HEIGHT) / 2 + 12;
        int buttonWidth = 150;
        int gap = 10;

        settlementName = new EditBox(this.font, left, top + 32, 310, 20,
                Component.literal("Название поселения"));
        settlementName.setMaxLength(40);
        settlementName.setHint(Component.literal("Например, Новая деревня"));
        addRenderableWidget(settlementName);

        addRenderableWidget(Button.builder(Component.literal("Основать поселение"), button ->
                sendCommand("civitas found " + safeName())).bounds(left, top + 59, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Переименовать"), button ->
                sendCommand("civitas rename " + safeName())).bounds(left + buttonWidth + gap, top + 59, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Обзор поселения"), button ->
                sendCommand("civitas status")).bounds(left, top + 86, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Содержимое склада"), button ->
                sendCommand("civitas storage")).bounds(left + buttonWidth + gap, top + 86, buttonWidth, 20).build());

        itemId = new EditBox(this.font, left, top + 122, 220, 20, Component.literal("ID предмета"));
        itemId.setMaxLength(128);
        itemId.setValue("minecraft:oak_log");
        itemId.setHint(Component.literal("minecraft:oak_log"));
        addRenderableWidget(itemId);

        amount = new EditBox(this.font, left + 230, top + 122, 80, 20, Component.literal("Количество"));
        amount.setMaxLength(4);
        amount.setValue("16");
        amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(amount);

        addRenderableWidget(Button.builder(Component.literal("Сдать предметы из руки"), button ->
                sendCommand("civitas deposit " + safeAmount())).bounds(left, top + 151, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Забрать со склада"), button ->
                sendCommand("civitas withdraw " + safeItemId() + " " + safeAmount()))
                .bounds(left + buttonWidth + gap, top + 151, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Жители и задачи"), button ->
                sendCommand("civitas workers")).bounds(left, top + 205, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назначить лесоруба"), button ->
                sendCommand("civitas worker lumberjack")).bounds(left + buttonWidth + gap, top + 205, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Приостановить"), button ->
                sendCommand("civitas worker pause")).bounds(left, top + 232, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Продолжить"), button ->
                sendCommand("civitas worker resume")).bounds(left + buttonWidth + gap, top + 232, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Снять профессию"), button ->
                sendCommand("civitas worker clear")).bounds(left, top + 259, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Список зданий"), button ->
                sendCommand("civitas buildings")).bounds(left, top + 178, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), button ->
                onClose()).bounds(left + buttonWidth + gap, top + 178, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Построить дом"), button ->
                sendCommand("civitas build cabin")).bounds(left, top + 300, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Построить ферму"), button ->
                sendCommand("civitas build farm")).bounds(left + buttonWidth + gap, top + 300, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Построить мастерскую"), button ->
                sendCommand("civitas build workshop")).bounds(left, top + 327, 310, 20).build());
    }

    private String safeName() {
        String value = settlementName.getValue().trim().replace('\n', ' ').replace('\r', ' ');
        return value.isEmpty() ? "Новая деревня" : value;
    }

    private String safeItemId() {
        String value = itemId.getValue().trim();
        return value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? value : "minecraft:oak_log";
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
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF020252D);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFB89A57);
        graphics.drawCenteredString(this.font, Component.literal("CIVITAS"), this.width / 2, top + 10, 0xFFE7C982);
        graphics.drawCenteredString(this.font, Component.literal("Управление поселением"), this.width / 2, top + 23, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Название поселения"), left + 15, top + 36, 0xFFD7DCE4, false);
        graphics.drawString(this.font, Component.literal("Предмет и количество для склада"), left + 15, top + 110, 0xFFD7DCE4, false);
        graphics.drawCenteredString(this.font, Component.literal("Строительство за ресурсы склада"), this.width / 2, top + 286, 0xFFE7C982);
        graphics.drawCenteredString(this.font, Component.literal("Ответы сервера появятся в чате"), this.width / 2, top + 354, 0xFFB8C0CC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
