package com.replaymod.replay.events;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;

public abstract class ReplayDispatchKeypressesEvent extends Event {

    @Cancelable
    public static class Pre extends ReplayDispatchKeypressesEvent {}
}
