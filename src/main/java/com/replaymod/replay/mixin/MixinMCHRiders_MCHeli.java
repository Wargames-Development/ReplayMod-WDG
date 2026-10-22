package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayRiders;
import com.replaymod.replay.compat.ReplayVehicleClock;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The unified body pass owns its riders as well as the aircraft model. */
@Pseudo
@Mixin(targets="mcheli.aircraft.MCH_RenderAircraft", remap=false)
public abstract class MixinMCHRiders_MCHeli {
    @Inject(method="renderRiddenEntity",at=@At("HEAD"),cancellable=true)
    private void replayMod$deferRiders(@Coerce Entity aircraft,float partial,float yaw,float pitch,float roll,float width,float height,CallbackInfo ci) {
        if (ReplayVehicleClock.active() && !ReplayRiders.drawing() && ReplayRiders.unified(aircraft)) ci.cancel();
    }
}
