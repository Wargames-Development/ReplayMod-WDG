package com.replaymod.replay.compat;

/** Implemented by optional vehicle mixins to finish their own network interpolation after a seek. */
public interface ReplayVehiclePose {
    void replayMod$settlePose();
}
