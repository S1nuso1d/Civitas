package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.civitas.network.CivitasNetwork;
import java.util.List;

/** Vanilla-styled trade UI. All inventory changes are validated by the server. */
public final class TradeScreen extends Screen {
    private final CivitasNetwork.TradeScreenPacket data;
    private static final int PANEL_W = 390, PANEL_H = 250;

    public TradeScreen(CivitasNetwork.TradeScreenPacket data) {
        super(Component.translatable("civitas.trade.title", data.partnerName()));
        this.data = data;
    }

    public static void open(CivitasNetwork.TradeScreenPacket packet) {
        Minecraft.getInstance().setScreen(new TradeScreen(packet));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        int y = top + 70;
        for (int i = 0; i < data.offers().size(); i++) {
            final int index = i;
            final boolean available = data.offers().get(i).available();
            addRenderableWidget(Button.builder(Component.translatable("civitas.trade.btn.trade"), button -> {
                if (available) ClientNetworkHandler.sendPacket(
                        new CivitasNetwork.TradeActionPacket(data.traderId(), index, false));
            }).bounds(left + PANEL_W - 92, y + i * 27 - 3, 82, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("civitas.trade.btn.gift"),
                button -> ClientNetworkHandler.sendPacket(new CivitasNetwork.TradeActionPacket(data.traderId(), -1, true)))
                .bounds(left + 12, top + PANEL_H - 30, 190, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("civitas.trade.btn.close"), button -> onClose())
                .bounds(left + PANEL_W - 92, top + PANEL_H - 30, 82, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int left = (width - PANEL_W) / 2, top = (height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF101010);
        g.fill(left + 1, top + 1, left + PANEL_W - 1, top + PANEL_H - 1, 0xFFC6C6C6);
        g.fill(left + 3, top + 3, left + PANEL_W - 3, top + PANEL_H - 3, 0xFFE2E2E2);
        g.drawCenteredString(font, Component.translatable("civitas.trade.title", data.partnerName()),
                left + PANEL_W / 2, top + 10, 0xFF303030);
        g.drawString(font, Component.translatable("civitas.trade.reputation", data.reputation()),
                left + 12, top + 30, 0xFF303030, false);
        g.drawString(font, Component.translatable("civitas.trade.trades_left", data.tradesLeft()),
                left + 12, top + 44, 0xFF303030, false);
        g.drawString(font, Component.translatable("civitas.trade.offers"), left + 12, top + 58, 0xFF303030, false);
        for (int i = 0; i < data.offers().size(); i++) {
            var offer = data.offers().get(i);
            int y = top + 72 + i * 27;
            int color = offer.available() ? 0xFF303030 : 0xFF777777;
            g.drawString(font, offer.want(), left + 12, y, color, false);
            g.drawString(font, "→", left + 165, y, color, false);
            g.drawString(font, offer.offer(), left + 185, y, color, false);
        }
        int historyY = Math.min(top + PANEL_H - 52, top + 82 + data.offers().size() * 27);
        if (!data.history().isEmpty()) {
            g.drawString(font, Component.translatable("civitas.trade.history"), left + 12, historyY, 0xFF505050, false);
            List<String> history = data.history();
            for (int i = history.size() - 1, shown = 0; i >= 0 && shown < 2; i--, shown++) {
                String line = history.get(i);
                while (font.width(line) > PANEL_W - 24 && line.length() > 3) line = line.substring(0, line.length() - 2) + "…";
                g.drawString(font, line, left + 12, historyY + 12 + shown * 11, 0xFF555555, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
