package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ru.civitas.network.CivitasNetwork;
import ru.civitas.world.BuildingDefinition;

/**
 * Vanilla-style blueprint picker. The server remains authoritative: this screen
 * only sends typed requests and never places blocks directly on the client.
 */
public final class BlueprintCatalogScreen extends Screen {
    private BuildingDefinition.Type selected = BuildingDefinition.Type.CABIN;
    private int rotation;
    private Button cabinButton;
    private Button farmButton;
    private Button workshopButton;
    private Button rotateButton;
    private Button previewButton;
    private Button orderButton;
    private String status = "Наведи прицел на блок земли для выбора площадки.";

    public BlueprintCatalogScreen() {
        super(Component.literal("Чертежи Civitas"));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(12, (height - 270) / 2);
        int buttonWidth = (panelWidth - 24) / 3;

        cabinButton = addRenderableWidget(Button.builder(Component.literal("Дом"), b -> select(BuildingDefinition.Type.CABIN))
                .bounds(left + 8, top + 34, buttonWidth, 20).build());
        farmButton = addRenderableWidget(Button.builder(Component.literal("Ферма"), b -> select(BuildingDefinition.Type.FARM))
                .bounds(left + 12 + buttonWidth, top + 34, buttonWidth, 20).build());
        workshopButton = addRenderableWidget(Button.builder(Component.literal("Мастерская"), b -> select(BuildingDefinition.Type.WORKSHOP))
                .bounds(left + 16 + buttonWidth * 2, top + 34, buttonWidth, 20).build());

        rotateButton = addRenderableWidget(Button.builder(Component.literal("Поворот: 0°"), b -> {
            rotation = (rotation + 90) % 360;
            rotateButton.setMessage(Component.literal("Поворот: " + rotation + "°"));
        }).bounds(left + 8, top + 174, panelWidth - 16, 20).build());

        previewButton = addRenderableWidget(Button.builder(Component.literal("Проверить площадку"), b -> sendPlacement(true))
                .bounds(left + 8, top + 202, panelWidth - 16, 20).build());
        orderButton = addRenderableWidget(Button.builder(Component.literal("Заказать строительство"), b -> sendPlacement(false))
                .bounds(left + 8, top + 228, panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left + 8, top + 254, panelWidth - 16, 20).build());
        updateSelectionButtons();
    }

    private void select(BuildingDefinition.Type type) {
        selected = type;
        updateSelectionButtons();
    }

    private void updateSelectionButtons() {
        if (cabinButton == null) return;
        cabinButton.active = selected != BuildingDefinition.Type.CABIN;
        farmButton.active = selected != BuildingDefinition.Type.FARM;
        workshopButton.active = selected != BuildingDefinition.Type.WORKSHOP;
    }

    private BlockPos targetPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        return null;
    }

    private void sendPlacement(boolean preview) {
        BlockPos target = targetPosition();
        if (target == null) {
            status = "Сначала наведи прицел на блок площадки.";
            return;
        }
        String payload = selected.id() + "|" + target.getX() + "|" + target.getY() + "|" + target.getZ();
        CivitasNetwork.Action action = preview
                ? CivitasNetwork.Action.PREVIEW_BUILD : CivitasNetwork.Action.BUILD_AT;
        ClientNetworkHandler.sendPacket(new CivitasNetwork.ActionPacket(action, payload, rotation));
        status = preview
                ? "Запрос проверки отправлен серверу."
                : "Заказ отправлен серверу. Ответ и ошибки появятся в чате.";
        if (!preview) onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelWidth = Math.min(360, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(12, (height - 270) / 2);
        graphics.fill(left, top, left + panelWidth, top + 282, 0xE820242B);
        graphics.fill(left, top, left + panelWidth, top + 2, 0xFF8DAD83);
        graphics.drawCenteredString(font, "Чертежи Civitas", width / 2, top + 10, 0xFFFFFFFF);

        BuildingDefinition definition = BuildingDefinition.get(selected);
        graphics.drawString(font, definition.displayName(), left + 10, top + 64, 0xFFFFD27A, false);
        graphics.drawString(font, "Размер: " + definition.width() + " × " + definition.depth()
                + " × " + definition.height() + " блоков", left + 10, top + 80, 0xFFE6E6E6, false);
        graphics.drawString(font, "Этапы строительства: " + definition.totalLayers(), left + 10, top + 94,
                0xFFE6E6E6, false);
        graphics.drawString(font, "Материалы:", left + 10, top + 112, 0xFFB7D9FF, false);

        int lineY = top + 126;
        for (BuildingDefinition.ResourceCost cost : definition.resources()) {
            if (lineY > top + 169) break;
            graphics.drawString(font, "• " + cost.amount() + " × " + cost.displayName(), left + 14, lineY,
                    0xFFE6E6E6, false);
            lineY += 12;
        }

        BlockPos target = targetPosition();
        String targetText = target == null ? "Площадка: не выбрана"
                : "Площадка: " + target.getX() + ", " + target.getY() + ", " + target.getZ();
        graphics.drawString(font, targetText, left + 10, top + 154, 0xFFB7D9FF, false);
        graphics.drawString(font, status, left + 10, top + 270, 0xFFD6D6D6, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
