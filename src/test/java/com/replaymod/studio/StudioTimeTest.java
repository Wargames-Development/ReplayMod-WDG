package com.replaymod.studio;

import org.junit.Test;
import static org.junit.Assert.*;

public class StudioTimeTest {
    @Test public void supportsSecondsMinutesHoursAndMilliseconds() {
        assertEquals(12500, StudioTime.parse("12.5"));
        assertEquals(72500, StudioTime.parse("1:12.500"));
        assertEquals(3723500, StudioTime.parse("1:02:03.5"));
        assertEquals(StudioTime.parse("123:45.678"), StudioTime.parse(StudioTime.format(7425678)));
    }
    @Test public void rejectsInvalidTimesWithoutSilentlyClampingThem() {
        for (String input : new String[]{"", "-1", "NaN", "Infinity", "1:60", "1.5:20", "1::2", "999999999999"}) {
            try { StudioTime.parse(input); fail(input); } catch (IllegalArgumentException expected) { }
        }
    }
}
