package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayVehicleClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "mcheli.MCH_EntityInfo", remap = false)
public abstract class MixinMCHSnapshotClock_MCHeli {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"))
    private long replayMod$clock() { return ReplayVehicleClock.millis(); }
}
