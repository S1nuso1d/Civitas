package ru.civitas.client;

import net.minecraft.client.Minecraft;

/** Client-only entry points called through DistExecutor from common code. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openSettlementScreen() {
        Minecraft.getInstance().setScreen(new SettlementScreen());
    }
}