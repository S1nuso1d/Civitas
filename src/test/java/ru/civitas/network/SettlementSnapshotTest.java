package ru.civitas.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementSnapshotTest {
    @Test
    void snapshotCodecPreservesResidentIdentityRoleAndStatus() {
        String residentId = UUID.randomUUID().toString();
        SettlementSnapshot snapshot = new SettlementSnapshot(
                true, "Civitas", "Owner", 2, 7,
                12, 7, 0, 2,
                7, 1, 2, 4, 4, 1, 1, 2, 45, 6, 27,
                "Синхронизировано",
                List.of("Лесоруб Civitas", "Житель"),
                List.of(residentId, UUID.randomUUID().toString()),
                List.of("lumberjack", ""),
                List.of("Рубит дерево: 2/5 сек", "Нет задачи"),
                List.of("Дом — 0, 64, 0"),
                List.of("1. Дом — 50%"),
                List.of("Дубовые брёвна × 12")
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.encode(buffer);
            SettlementSnapshot decoded = SettlementSnapshot.decode(buffer);
            assertEquals(snapshot, decoded);
            assertEquals(residentId, decoded.residentUuids().get(0));
            assertEquals("lumberjack", decoded.residentRoles().get(0));
            assertEquals("Рубит дерево: 2/5 сек", decoded.residentStatuses().get(0));
        } finally {
            buffer.release();
        }
    }

    @Test
    void residentListsAreAlignedAndBoundedTogether() {
        List<String> names = java.util.stream.IntStream.range(0, 70)
                .mapToObj(i -> "Житель " + i).toList();
        List<String> ids = java.util.stream.IntStream.range(0, 68)
                .mapToObj(i -> UUID.randomUUID().toString()).toList();
        List<String> roles = java.util.stream.IntStream.range(0, 69)
                .mapToObj(i -> i % 2 == 0 ? "lumberjack" : "builder").toList();
        List<String> statuses = java.util.stream.IntStream.range(0, 67)
                .mapToObj(i -> "Статус " + i).toList();

        SettlementSnapshot snapshot = new SettlementSnapshot(
                true, "Civitas", "Owner", 1, 70,
                0, 70, 0, 0,
                70, 1, 1, 68, 4, 1, 1, 0, 0, 0, 27,
                "Ок", names, ids, roles, statuses, List.of(), List.of(), List.of()
        );

        assertEquals(64, snapshot.residents().size());
        assertEquals(64, snapshot.residentUuids().size());
        assertEquals(64, snapshot.residentRoles().size());
        assertEquals(64, snapshot.residentStatuses().size());
        assertTrue(snapshot.residentUuids().stream().allMatch(id -> {
            try {
                UUID.fromString(id);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }));
    }
}
