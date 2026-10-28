package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.civitas.network.CivitasNetwork;

/** Server-fed diplomacy dashboard. Every stance change is revalidated on the server. */
public final class DiplomacyScreen extends Screen {
    private static final int PANEL_W = 430;
    private static final int PANEL_H = 330;
    private final CivitasNetwork.DiplomacyScreenPacket data;
    private int page;

    public DiplomacyScreen(CivitasNetwork.DiplomacyScreenPacket data) {
        super(Component.literal("Дипломатия Civitas"));
        this.data = data;
    }

    public static void open(CivitasNetwork.DiplomacyScreenPacket data) {
        Minecraft.getInstance().setScreen(new DiplomacyScreen(data));
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        int pageSize = 5;
        int maxPage = Math.max(0, (data.civilizations().size() - 1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * pageSize;
        for (int row = 0; row < pageSize && start + row < data.civilizations().size(); row++) {
            final var civilization = data.civilizations().get(start + row);
            int y = top + 76 + row * 40;
            addRenderableWidget(Button.builder(
                    Component.literal("Изменить: " + shortStance(civilization.stance())),
                    button -> ClientNetworkHandler.sendPacket(new CivitasNetwork.DiplomacyActionPacket(
                            civilization.id(), nextStance(civilization.stance()))))
                    .bounds(left + PANEL_W - 130, y + 2, 118, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("←"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(left + 12, top + PANEL_H - 31, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), button -> {
            page = Math.min(maxPage, page + 1);
            rebuildWidgets();
        }).bounds(left + 44, top + PANEL_H - 31, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Обновить"), button ->
                CivitasNetwork.requestDiplomacy()).bounds(left + PANEL_W - 208, top + PANEL_H - 31, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), button -> onClose())
                .bounds(left + PANEL_W - 108, top + PANEL_H - 31, 96, 20).build());
    }

    private static String nextStance(String stance) {
        return switch (stance) {
            case "NEUTRAL" -> "allied";
            case "ALLIED" -> "rival";
            case "RIVAL" -> "war";
            default -> "neutral";
        };
    }

    private static String shortStance(String stance) {
        return switch (stance) {
            case "ALLIED" -> "Союз";
            case "RIVAL" -> "Соперник";
            case "WAR" -> "Война";
            default -> "Нейтралитет";
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int left = (width - PANEL_W) / 2, top = (height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF101010);
        g.fill(left + 1, top + 1, left + PANEL_W - 1, top + PANEL_H - 1, 0xFFC6C6C6);
        g.fill(left + 3, top + 3, left + PANEL_W - 3, top + PANEL_H - 3, 0xFFE2E2E2);
        g.drawCenteredString(font, "Дипломатия соседних цивилизаций", left + PANEL_W / 2, top + 12, 0xFF303030);
        g.drawString(font, "Кнопка циклически меняет отношения: нейтралитет → союз → соперничество → война",
                left + 12, top + 31, 0xFF454545, false);
        if (data.civilizations().isEmpty()) {
            g.drawCenteredString(font, "Пока нет известных соседей.", left + PANEL_W / 2, top + 100, 0xFF505050);
            g.drawCenteredString(font, "Они появятся после прибытия торговых караванов.", left + PANEL_W / 2, top + 116, 0xFF505050);
        } else {
            int start = page * 5;
            for (int row = 0; row < 5 && start + row < data.civilizations().size(); row++) {
                var c = data.civilizations().get(start + row);
                int y = top + 70 + row * 40;
                g.fill(left + 8, y, left + PANEL_W - 8, y + 36, 0xFFD1D1D1);
                g.drawString(font, c.name(), left + 14, y + 4, 0xFF202020, false);
                String stats = "Население " + c.population() + " · Земли " + c.territory()
                        + " · Еда " + c.food() + " · Стабильность " + c.stability() + "%"
                        + " · Репутация " + c.reputation();
                if (font.width(stats) > PANEL_W - 160) {
                    while (font.width(stats) > PANEL_W - 160 && stats.length() > 5)
                        stats = stats.substring(0, stats.length() - 2) + "…";
                }
                g.drawString(font, stats, left + 14, y + 19, 0xFF505050, false);
            }
        }
        g.drawString(font, "Отношения сохраняются в мире. Война блокирует торговые караваны.",
                left + 12, top + PANEL_H - 48, 0xFF505050, false);
        if (!data.civilizations().isEmpty()) {
            g.drawString(font, "Страница " + (page + 1) + "/" + Math.max(1, (data.civilizations().size() + 4) / 5),
                    left + 82, top + PANEL_H - 25, 0xFF505050, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
