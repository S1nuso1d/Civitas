package ru.civitas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.civitas.menu.SettlementWarehouseMenu;

public final class SettlementWarehouseScreen extends AbstractContainerScreen<SettlementWarehouseMenu> {
    public SettlementWarehouseScreen(SettlementWarehouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (width - imageWidth) / 2;
        int top = (height - imageHeight) / 2;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF020252D);
        graphics.renderOutline(left, top, imageWidth, imageHeight, 0xFFB89A57);
        graphics.fill(left + 7, top + 17, left + imageWidth - 7, top + 71, 0xFF343B46);
        graphics.fill(left + 7, top + 85, left + imageWidth - 7, top + 139, 0xFF343B46);
        graphics.fill(left + 7, top + 143, left + imageWidth - 7, top + 161, 0xFF343B46);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.literal("Склад поселения"), 8, 6, 0xFFE7C982, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
