package ru.civitas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.client.Minecraft;

/**
 * First in-game Civitas management panel. Actions are sent to the server
 * through the normal command protocol, so the player does not need to type commands.
 */
public final class SettlementScreen extends Screen {
    private EditBox settlementName;

    public SettlementScreen() {
        super(Component.literal("Civitas — управление поселением"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 120;
        int top = this.height / 2 - 92;

        settlementName = new EditBox(this.font, left, top + 32, 240, 20,
                Component.literal("Название поселения"));
        settlementName.setMaxLength(40);
        settlementName.setHint(Component.literal("Например, Новая деревня"));
        addRenderableWidget(settlementName);

        addRenderableWidget(Button.builder(Component.literal("Основать поселение"), button -> {
            String name = settlementName.getValue().trim();
            sendCommand("civitas found " + (name.isEmpty() ? "Новая деревня" : name));
            onClose();
        }).bounds(left, top + 60, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Обзор поселения"), button -> runCommand("civitas status"))
                .bounds(left, top + 88, 116, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Склад"), button -> runCommand("civitas storage"))
                .bounds(left + 124, top + 88, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Помощь"), button -> runCommand("civitas help"))
                .bounds(left, top + 116, 116, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), button -> onClose())
                .bounds(left + 124, top + 116, 116, 20).build());
    }

    private void runCommand(String command) {
        sendCommand(command);
        onClose();
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
        int left = this.width / 2 - 135;
        int top = this.height / 2 - 110;
        graphics.fill(left, top, left + 270, top + 190, 0xE820252D);
        graphics.renderOutline(left, top, 270, 190, 0xFFB89A57);
        graphics.drawCenteredString(this.font, Component.literal("CIVITAS"), this.width / 2, top + 10, 0xFFE7C982);
        graphics.drawCenteredString(this.font, Component.literal("Управление цивилизацией"), this.width / 2, top + 22, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Название для нового поселения:"), left + 15, top + 48, 0xFFD7DCE4, false);
        graphics.drawCenteredString(this.font, Component.literal("Данные поселения сохраняются в мире"), this.width / 2, top + 165, 0xFFB8C0CC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}