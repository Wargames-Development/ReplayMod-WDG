package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayVehicleClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gameplay identification triangles do not belong in replay camera views or exports. */
@Pseudo
@Mixin(targets="mcheli.multiplay.MCH_GuiTargetMarker", remap=false)
public abstract class MixinMCHTargetMarker_MCHeli {
    @Inject(method="isEnableEntityMarker", at=@At("HEAD"), cancellable=true)
    private static void replayMod$hideReplayMarkers(CallbackInfoReturnable<Boolean> ci) {
        if (ReplayVehicleClock.active()) ci.setReturnValue(false);
    }
}
