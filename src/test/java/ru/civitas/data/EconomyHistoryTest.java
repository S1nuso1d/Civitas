package ru.civitas.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EconomyHistoryTest {
    @Test void deltaUsesDailySnapshots() {
        EconomyHistory history = new EconomyHistory(30);
        for (long day=1; day<=10; day++) history.recordDaily(day,(int)(day*10),(int)(day*5),(int)(day*2),5);
        assertEquals(70, history.deltaWood(7));
        assertEquals(35, history.deltaStone(7));
        assertEquals(14, history.deltaFood(7));
    }
    @Test void depletionForecastUsesWeeklyNetChange() {
        EconomyHistory history = new EconomyHistory(30);
        for (long day = 1; day <= 8; day++) {
            history.recordDaily(day, 100 - (int) day * 10, 80 - (int) day * 5,
                    100 - (int) day * 7, 5);
        }
        assertEquals(10, history.daysUntilDepletion("wood", 100));
        assertEquals(20, history.daysUntilDepletion("stone", 100));
        assertEquals(14, history.daysUntilDepletion("food", 100));
        EconomyHistory growing = new EconomyHistory(7);
        growing.recordDaily(1, 10, 10, 10, 1);
        growing.recordDaily(8, 20, 20, 20, 1);
        assertEquals(-1, growing.daysUntilDepletion("wood", 100));
        assertEquals(-1, history.daysUntilDepletion("unknown", 100));
        assertEquals(0, history.daysUntilDepletion("food", 0));
    }

    @Test void historyAndEventsHaveBoundedCapacityAndPersist() {
        EconomyHistory history = new EconomyHistory(7);
        for (long day=1;day<=100;day++) history.recordDaily(day,0,0,0,5);
        for (int i=0;i<100;i++) history.recordEvent(100,"event "+i);
        assertEquals(7, history.snapshot().size());
        assertEquals(32, history.recentEvents().size());
        assertTrue(history.setCapacity(10));
        assertEquals(10, history.capacity());
        CompoundTag tag=new CompoundTag();
        history.save(tag,"economy");
        EconomyHistory loaded=EconomyHistory.load(tag,"economy",7);
        assertEquals(7,loaded.snapshot().size());
        assertEquals(32,loaded.recentEvents().size());
        assertEquals("День 100: event 99",loaded.recentEvents().get(31));

        EconomyHistory flow = new EconomyHistory(7);
        flow.recordDaily(3, 10, 4, 8, 5);
        flow.recordFoodProduction(3, 6);
        flow.recordFoodConsumption(3, 5);
        CompoundTag flowTag = new CompoundTag();
        flow.save(flowTag, "economy");
        EconomyHistory flowLoaded = EconomyHistory.load(flowTag, "economy", 7);
        assertEquals(6, flowLoaded.snapshot().get(0).foodProduced);
        assertEquals(5, flowLoaded.snapshot().get(0).foodConsumed);
    }
}
