package com.replaymod.core.events;

import de.johni0702.minecraft.gui.utils.Event;

public interface KeyEventCallback {
    Event<KeyEventCallback> EVENT = Event.create((listeners) ->
            (key, scanCode, action, modifiers) -> {
                for (KeyEventCallback listener : listeners) {
                    if (listener.onKeyEvent(key, scanCode, action, modifiers)) {
                        return true;
                    }
                }
                return false;
            }
    );

    int ACTION_RELEASE = 0;
    int ACTION_PRESS = 1;

    boolean onKeyEvent(int key, int scanCode, int action, int modifiers);
}
