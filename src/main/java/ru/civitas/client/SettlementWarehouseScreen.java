package ru.civitas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.civitas.menu.SettlementWarehouseMenu;

public final class SettlementWarehouseScreen extends AbstractContainerScreen<SettlementWarehouseMenu> {
    public SettlementWarehouseScreen(SettlementWarehouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (width - imageWidth) / 2;
        int top = (height - imageHeight) / 2;
        // Vanilla inventory-style bevel and recessed slot areas.
        graphics.fill(left - 1, top - 1, left + imageWidth + 1, top + imageHeight + 1, 0xFF171717);
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFFC6C6C6);
        graphics.fill(left + 2, top + 2, left + imageWidth - 2, top + imageHeight - 2, 0xFF373737);
        graphics.fill(left + 3, top + 3, left + imageWidth - 3, top + imageHeight - 3, 0xFFC6C6C6);
        graphics.fill(left + 7, top + 17, left + imageWidth - 7, top + 71, 0xFF8B8B8B);
        graphics.fill(left + 7, top + 85, left + imageWidth - 7, top + 139, 0xFF8B8B8B);
        graphics.fill(left + 7, top + 143, left + imageWidth - 7, top + 161, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.literal("Склад поселения"), 8, 6, 0xFF404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
