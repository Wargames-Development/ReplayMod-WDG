package com.replaymod.pathing.player;

import com.replaymod.core.utils.WrappedTimer;
import de.johni0702.minecraft.gui.utils.Event;
import net.minecraft.util.Timer;

/**
 * Wrapper around the current timer that prevents the timer from advancing by itself.
 */
public class ReplayTimer extends WrappedTimer {
    private final Timer state = new Timer(0);

    public ReplayTimer(Timer wrapped) {
        super(wrapped);
    }

    @Override
    public void updateTimer() {
        copy(this, state); // Save our current state
        try {
            wrapped.updateTimer(); // Update current state
        } finally {
            copy(state, this); // Restore our old state
            UpdatedCallback.EVENT.invoker().onUpdate();
        }
    }

    public Timer getWrapped() {
        return wrapped;
    }

    public interface UpdatedCallback {
        Event<UpdatedCallback> EVENT = Event.create((listeners) ->
                () -> {
                    for (UpdatedCallback listener : listeners) {
                        listener.onUpdate();
                    }
                }
        );
        void onUpdate();
    }
}
