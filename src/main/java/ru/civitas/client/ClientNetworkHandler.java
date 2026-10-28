package ru.civitas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import ru.civitas.network.CivitasNetwork;
import ru.civitas.network.SettlementSnapshot;

/** Applies server snapshots on the client game thread. */
public final class ClientNetworkHandler {
    private static SettlementSnapshot latestSnapshot =
            SettlementSnapshot.empty("Ожидание данных сервера…");

    private ClientNetworkHandler() {
    }

    public static void handleSnapshot(SettlementSnapshot snapshot) {
        latestSnapshot = snapshot;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SettlementScreen screen) {
            screen.acceptSnapshot(snapshot);
        }
    }

    public static SettlementSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    /** Sends an already-validated typed payload through the active client connection. */
    public static void sendPacket(Object packet) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener != null) {
            CivitasNetwork.CHANNEL.send(packet, listener.getConnection());
        }
    }
}
