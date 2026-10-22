package com.replaymod.replay;

import com.replaymod.replay.compat.RecordedSnapshotTimeline;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordedSnapshotTimelineTest {
    @Test public void lateAndBatchedArrivalsKeepTheirServerTickSpacing() throws Exception {
        for (int late:new int[]{1251,1300}) {
            RecordedSnapshotTimeline clock=new RecordedSnapshotTimeline();
            int[] arrivals={1000,1100,late,1300,1400,1500,1600};
            for (int i=0;i<arrivals.length;i++) clock.add(arrivals[i],100+i*2);
            assertArrayEquals(new int[]{0,1000,1100,1200,1300,1400,1500,1600},clock.times());
        }
    }
    @Test public void sustainedSlowTicksPausesAndClockRestartsKeepTheirTiming() throws Exception {
        RecordedSnapshotTimeline clock=new RecordedSnapshotTimeline();
        int[] arrivals={1000,1150,1300,1450,1600,2200,2300,2400,2500,2600};
        long[] sequences={100,102,104,106,108,110,112,1,3,5};
        for (int i=0;i<arrivals.length;i++) clock.add(arrivals[i],sequences[i]);
        int[] expected=new int[arrivals.length+1]; System.arraycopy(arrivals,0,expected,1,arrivals.length);
        assertArrayEquals(expected,clock.times());
    }
    @Test public void invalidOrLegacySequencesAndLargeCorrectionsRetainArrivalTiming() throws Exception {
        for (long step:new long[]{0,100,-1}) {
            RecordedSnapshotTimeline clock=new RecordedSnapshotTimeline();
            int[] arrivals={1000,1100,1251,1300,1400};
            for (int i=0;i<arrivals.length;i++) clock.add(arrivals[i],1000+i*step);
            assertArrayEquals(new int[]{0,1000,1100,1251,1300,1400},clock.times());
        }
        RecordedSnapshotTimeline clock=new RecordedSnapshotTimeline();
        for (int i=0;i<5;i++) clock.add(i==2?1401:1000+i*100,100+i*2);
        assertEquals(1401,clock.times()[3]);
    }
}
