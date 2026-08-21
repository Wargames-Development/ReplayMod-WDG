package com.replaymod.core.utils;

import com.replaymod.core.mixin.TimerAccessor;
import net.minecraft.util.Timer;

public class WrappedTimer extends Timer {
    public static final float DEFAULT_MS_PER_TICK = 1000 / 20;

    protected final Timer wrapped;

    public WrappedTimer(Timer wrapped) {
        super(0);
        this.wrapped = wrapped;
        copy(wrapped, this);
    }

    @Override
    public void updateTimer() {
        copy(this, wrapped);
        try {
            wrapped.updateTimer();
        } finally {
            copy(wrapped, this);
        }
    }

    protected void copy(Timer from, Timer to) {
        TimerAccessor fromA = (TimerAccessor) from;
        TimerAccessor toA = (TimerAccessor) to;

        to.elapsedTicks = from.elapsedTicks;
        to.renderPartialTicks = from.renderPartialTicks;
        toA.setLastSyncSysClock(fromA.getLastSyncSysClock());
        to.elapsedPartialTicks = from.elapsedPartialTicks;
        toA.setTicksPerSecond(fromA.getTicksPerSecond());
        toA.setLastHRTime(fromA.getLastHRTime());
        toA.setTimerSpeed(fromA.getTimerSpeed());
        toA.setLastSyncHRClock(fromA.getLastSyncHRClock());
        toA.setCounter(fromA.getCounter());
        toA.setTimeSyncAdjustment(fromA.getTimeSyncAdjustment());
    }
}
