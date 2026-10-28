package ru.civitas.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SettlementSnapshotTest {
    @Test
    void snapshotCodecPreservesEconomyAndResidentState() {
        String residentId = UUID.randomUUID().toString();
        SettlementSnapshot snapshot = new SettlementSnapshot(
                true, "Civitas", "Owner", 2, 7,
                12, 7, 1, 0, 1, 2, 8,
                7, 1, 2, 1, 3,
                4, 1, 1, 2, 45, 6, 27,
                -2, 4, 7, "Синхронизировано",
                List.of("День 4: еда пополнена"), List.of("День 4: дерево 10"),
                List.of("Лесоруб Civitas", "Житель"),
                List.of(residentId, UUID.randomUUID().toString()),
                List.of("lumberjack", ""), List.of("Рубит дерево: 2/5 сек", "Нет задачи"),
                List.of("Дом — 0, 64, 0"), List.of("1. Дом — 50%"),
                List.of("Дубовые брёвна × 12"), List.of("population_5|4"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.encode(buffer);
            SettlementSnapshot decoded = SettlementSnapshot.decode(buffer);
            assertEquals(snapshot, decoded);
            assertEquals(-2, decoded.woodDelta7d());
            assertEquals("lumberjack", decoded.residentRoles().get(0));
            assertEquals("Рубит дерево: 2/5 сек", decoded.residentStatuses().get(0));
        } finally {
            buffer.release();
        }
    }

    @Test
    void residentListsAreAlignedAndBoundedTogether() {
        List<String> names = java.util.stream.IntStream.range(0, 70).mapToObj(i -> "Житель " + i).toList();
        List<String> ids = java.util.stream.IntStream.range(0, 68).mapToObj(i -> UUID.randomUUID().toString()).toList();
        List<String> roles = java.util.stream.IntStream.range(0, 69).mapToObj(i -> i % 2 == 0 ? "lumberjack" : "builder").toList();
        List<String> statuses = java.util.stream.IntStream.range(0, 67).mapToObj(i -> "Статус " + i).toList();
        SettlementSnapshot snapshot = new SettlementSnapshot(
                true, "Civitas", "Owner", 1, 70,
                0, 70, 0, 0, 0, 2, 70,
                70, 1, 1, 2, 66,
                4, 1, 1, 0, 0, 0, 27,
                0, 0, 0, "Ок", List.of(), List.of(),
                names, ids, roles, statuses, List.of(), List.of(), List.of(), List.of());
        assertEquals(64, snapshot.residents().size());
        assertEquals(64, snapshot.residentUuids().size());
        assertEquals(64, snapshot.residentRoles().size());
        assertEquals(64, snapshot.residentStatuses().size());
        assertTrue(snapshot.residentUuids().stream().allMatch(id -> {
            try { UUID.fromString(id); return true; } catch (IllegalArgumentException ex) { return false; }
        }));
    }
}
