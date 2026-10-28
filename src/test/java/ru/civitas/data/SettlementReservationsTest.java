package ru.civitas.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SettlementReservationsTest {
    @Test void aCropCannotBeReservedByTwoWorkers() {
        SettlementReservations reservations=new SettlementReservations();
        BlockPos crop=new BlockPos(4,65,-3);
        UUID first=UUID.randomUUID(), second=UUID.randomUUID();
        assertTrue(reservations.tryReserve(crop,first));
        assertTrue(reservations.isReservedBy(crop,first));
        assertTrue(reservations.isReservedByOther(crop,second));
        assertFalse(reservations.tryReserve(crop,second));
        assertTrue(reservations.release(crop,first));
        assertTrue(reservations.tryReserve(crop,second));
    }
    @Test void reservationsPersistAndCanBeReleasedByWorker() {
        SettlementReservations reservations=new SettlementReservations();
        BlockPos crop=new BlockPos(1,70,2);
        UUID worker=UUID.randomUUID();
        reservations.tryReserve(crop,worker);
        CompoundTag tag=new CompoundTag();
        reservations.save(tag,"plots");
        SettlementReservations loaded=SettlementReservations.load(tag,"plots");
        assertTrue(loaded.isReservedBy(crop,worker));
        assertTrue(loaded.releaseAll(worker));
        assertFalse(loaded.isReserved(crop));
    }
}
