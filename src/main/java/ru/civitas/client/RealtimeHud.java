package ru.civitas.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.civitas.network.SettlementSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-only diagnostics overlay. F4 toggles the settlement HUD; targeting a villager
 * shows a compact inspection panel. No packet or save-format changes are needed.
 */
@Mod.EventBusSubscriber(modid = "civitas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RealtimeHud {
    private static boolean enabled;
    private static long lastToggleNanos;

    private RealtimeHud() { }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getKey() != GLFW.GLFW_KEY_F4 || event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        long now = System.nanoTime();
        if (now - lastToggleNanos < 250_000_000L) return;
        lastToggleNanos = now;
        enabled = !enabled;
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("Civitas HUD: " + (enabled ? "включён" : "выключен")), true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        GuiGraphics graphics = event.getGuiGraphics();

        if (enabled) renderSettlement(graphics, ClientNetworkHandler.latestSnapshot());
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Villager villager
                && mc.player.distanceToSqr(villager) <= 64.0D) {
            renderWorkerInspection(graphics, villager);
        }
    }

    private static void renderSettlement(GuiGraphics graphics, SettlementSnapshot s) {
        List<String> lines = new ArrayList<>();
        lines.add("CIVITAS / REALTIME");
        lines.add("Поселение: " + (s.founded() ? s.settlementName() : "нет данных"));
        lines.add("Население: " + s.population() + "  Рабочие: " + (s.lumberjacks() + s.builders() + s.farmers()));
        lines.add("Профессии: лесорубы " + s.lumberjacks() + " | строители " + s.builders() + " | фермеры " + s.farmers());
        lines.add("Еда: " + s.foodStock() + " ед.  Запас: " + s.foodDaysLeft() + " дн.");
        lines.add("Изменение за 7 дн.: дерево " + signed(s.woodDelta7d()) + ", камень " + signed(s.stoneDelta7d()));
        lines.add("Пища за 7 дн.: " + signed(s.foodDelta7d()) + "  Очередь строек: " + s.orderCount());
        lines.add("Здания: " + (s.cabinCount() + s.farmCount() + s.workshopCount())
                + "  Склад: " + s.warehouseOccupiedSlots() + "/" + s.warehouseSlots());
        lines.add("Состояние: " + (s.statusMessage().isBlank() ? "норма" : s.statusMessage()));
        drawPanel(graphics, 8, 8, lines, 0xCC101820, 0xFFE4EDF5);
    }

    private static void renderWorkerInspection(GuiGraphics graphics, Villager villager) {
        List<String> lines = new ArrayList<>();
        lines.add("CIVITAS / ЖИТЕЛЬ");
        lines.add(villager.getName().getString());
        String custom = villager.getCustomName() == null ? "" : villager.getCustomName().getString();
        if (!custom.isBlank() && !custom.equals(villager.getName().getString())) {
            lines.add("Статус: " + custom);
        } else {
            lines.add("Статус: " + (villager.getNavigation().isDone() ? "стоит / ожидает" : "перемещается"));
        }
        lines.add("Здоровье: " + Math.round(villager.getHealth()) + "/" + Math.round(villager.getMaxHealth()));
        lines.add("Профессия Minecraft: " + Component.translatable(villager.getVillagerData().getProfession().value().name()).getString());
        drawPanel(graphics, 8, 122, lines, 0xCC202016, 0xFFFFE8B0);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, List<String> lines, int background, int textColor) {
        Minecraft mc = Minecraft.getInstance();
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.font.width(line));
        width = Math.min(width, 320);
        int lineHeight = mc.font.lineHeight + 3;
        graphics.fill(x, y, x + width + 12, y + lines.size() * lineHeight + 8, background);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (mc.font.width(line) > width) line = mc.font.plainSubstrByWidth(line, Math.max(0, width - 6)) + "…";
            graphics.drawString(mc.font, line, x + 6, y + 4 + i * lineHeight, i == 0 ? 0xFFFFFFFF : textColor, false);
        }
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
