package com.replaymod.replay.compat;

/** Keep cloud drift on one continuous source-time clock rather than a separate client tick phase. */
public final class ReplayCloudClock {
    private ReplayCloudClock() { }
    public static double ticks(double nativeTicks) {
        return ReplayVehicleClock.active() ? ReplayVehicleClock.millis()/50.0D : nativeTicks;
    }
}
