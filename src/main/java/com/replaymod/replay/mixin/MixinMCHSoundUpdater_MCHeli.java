package com.replaymod.replay.mixin;

import com.replaymod.render.audio.VehicleSound;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Connects the engine voice to its aircraft; no vehicle controls or physics are changed. */
@Pseudo
@Mixin(targets="mcheli.aircraft.MCH_SoundUpdater",remap=false)
public abstract class MixinMCHSoundUpdater_MCHeli {
    @Shadow private int soundDelay;
    @Unique private long replayMod$soundRevision=Long.MIN_VALUE;
    @Inject(method="update",at=@At("HEAD"),remap=false)
    private void replayMod$restartAfterSeek(CallbackInfo ci) {
        if (com.replaymod.replay.compat.ReplayVehicleClock.active() && replayMod$soundRevision!=com.replaymod.replay.compat.ReplayVehicleClock.revision()) {
            replayMod$soundRevision=com.replaymod.replay.compat.ReplayVehicleClock.revision(); soundDelay=0;
        }
    }
    @Inject(method="update",at=@At("RETURN"),remap=false)
    private void replayMod$attachEngine(CallbackInfo ci) { VehicleSound.bind(this); }
}
