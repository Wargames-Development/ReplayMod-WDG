package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.flansmod.client.TickHandlerClient", remap = false)
public abstract class MixinFMURShakeClock_FMUR {
    @Redirect(method = {"triggerScreenShake(FJ)V", "applyShotScreenShake"}, at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"), require = 0)
    private static long replayClock() { return ReplayEffects.nanoTime(); }
}
