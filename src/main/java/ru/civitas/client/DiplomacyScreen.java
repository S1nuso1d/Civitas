package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.civitas.network.CivitasNetwork;

/** Vanilla-styled diplomacy dashboard; all changes are validated by the server. */
public final class DiplomacyScreen extends Screen {
    private static final int PANEL_W = 470;
    private static final int PANEL_H = 370;
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
        String[] stances = {"neutral", "allied", "rival", "war"};
        String[] labels = {"Нейтр.", "Союз", "Сопер.", "Война"};
        int buttonGap = 4;
        int buttonWidth = 88;
        for (int row = 0; row < pageSize && start + row < data.civilizations().size(); row++) {
            final var civilization = data.civilizations().get(start + row);
            int y = top + 64 + row * 48;
            addRenderableWidget(Button.builder(Component.literal("Посетить"), button ->
                    ClientNetworkHandler.sendPacket(new CivitasNetwork.DiplomacyVisitPacket(civilization.id())))
                    .bounds(left + PANEL_W - 102, y + 1, 88, 18).build());
            for (int j = 0; j < stances.length; j++) {
                final String stance = stances[j];
                addRenderableWidget(Button.builder(Component.literal(labels[j]), button ->
                        ClientNetworkHandler.sendPacket(new CivitasNetwork.DiplomacyActionPacket(
                                civilization.id(), stance)))
                        .bounds(left + 12 + j * (buttonWidth + buttonGap), y + 23, buttonWidth, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("←"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(left + 12, top + PANEL_H - 30, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), button -> {
            page = Math.min(maxPage, page + 1);
            rebuildWidgets();
        }).bounds(left + 44, top + PANEL_H - 30, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Обновить"), button ->
                CivitasNetwork.requestDiplomacy()).bounds(left + PANEL_W - 208, top + PANEL_H - 30, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), button -> onClose())
                .bounds(left + PANEL_W - 108, top + PANEL_H - 30, 96, 20).build());
    }

    private static String stanceLabel(String stance) {
        return switch (stance) {
            case "ALLIED" -> "Союз";
            case "RIVAL" -> "Соперничество";
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
        g.drawCenteredString(font, "Дипломатия соседних цивилизаций", left + PANEL_W / 2, top + 10, 0xFF303030);
        g.drawString(font, "Выбери отношение напрямую. Союз требует репутацию не ниже 60.",
                left + 12, top + 29, 0xFF454545, false);
        if (data.civilizations().isEmpty()) {
            g.drawCenteredString(font, "Пока нет известных соседей.", left + PANEL_W / 2, top + 100, 0xFF505050);
            g.drawCenteredString(font, "Они появятся после прибытия торговых караванов.", left + PANEL_W / 2, top + 116, 0xFF505050);
        } else {
            int start = page * 5;
            for (int row = 0; row < 5 && start + row < data.civilizations().size(); row++) {
                var c = data.civilizations().get(start + row);
                int y = top + 60 + row * 48;
                g.fill(left + 8, y, left + PANEL_W - 8, y + 44, 0xFFD1D1D1);
                g.drawString(font, c.name() + " · " + stanceLabel(c.stance()),
                        left + 14, y + 3, 0xFF202020, false);
                String stats = "Население " + c.population() + " · Земли " + c.territory()
                        + " · Еда " + c.food() + " · Стабильность " + c.stability()
                        + "% · Репутация " + c.reputation();
                while (font.width(stats) > PANEL_W - 28 && stats.length() > 5)
                    stats = stats.substring(0, stats.length() - 2) + "…";
                g.drawString(font, stats, left + 14, y + 14, 0xFF505050, false);
            }
        }
        g.drawString(font, "Война блокирует торговлю; союз открывается через торговлю и подарки.",
                left + 12, top + PANEL_H - 48, 0xFF505050, false);
        if (!data.civilizations().isEmpty()) {
            g.drawString(font, "Страница " + (page + 1) + "/" + Math.max(1, (data.civilizations().size() + 4) / 5),
                    left + 82, top + PANEL_H - 24, 0xFF505050, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
