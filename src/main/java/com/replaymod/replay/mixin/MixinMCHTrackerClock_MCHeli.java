package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayVehicleClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "mcheli.MCH_EntityInfoClientTracker", remap = false)
public abstract class MixinMCHTrackerClock_MCHeli {
    @Redirect(method = {"updateEntities", "shouldSuppressAircraftRender", "cleanupExpired", "requestMissingAircraftResync"},
            at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), require = 0)
    private static long replayMod$clock() { return ReplayVehicleClock.millis(); }
}
