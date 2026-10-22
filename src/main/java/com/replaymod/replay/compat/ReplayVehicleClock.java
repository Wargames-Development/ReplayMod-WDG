package com.replaymod.replay.compat;

import com.replaymod.replay.ReplayModReplay;

/** Keeps distant aircraft presentation and snapshot expiry on the same clock as playback. */
public final class ReplayVehicleClock {
    private static long revision;
    private ReplayVehicleClock() { }
    public static boolean active() { return ReplayModReplay.instance != null && ReplayModReplay.instance.getReplayHandler() != null; }
    public static long millis() { return active() ? ReplayModReplay.instance.getReplayHandler().getReplaySender().currentTimeStamp() : System.currentTimeMillis(); }
    /** Same playback time base as snapshot ages, independent of frame rendering cost. */
    public static long nanos() { return active() ? millis() * 1000000L : System.nanoTime(); }
    public static long revision() { return revision; }
    public static void invalidate() { ++revision; }
}
