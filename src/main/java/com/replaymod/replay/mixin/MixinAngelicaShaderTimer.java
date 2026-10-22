package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayShaderClock;
import com.replaymod.replay.compat.ReplayVehicleClock;
import java.util.OptionalLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.coderbot.iris.uniforms.SystemTimeUniforms$Timer", remap = false)
public abstract class MixinAngelicaShaderTimer {
    @Shadow private float frameTimeCounter;
    @Shadow private float lastFrameTime;
    @Shadow private OptionalLong lastStartTime;
    @Unique private long replayMod$previousMillis = -1;

    @Inject(method = "beginFrame(J)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void replayMod$replayTime(long startTime, CallbackInfo ci) {
        if (!ReplayVehicleClock.active()) {
            if (replayMod$previousMillis >= 0) lastStartTime = OptionalLong.empty();
            replayMod$previousMillis = -1;
            return;
        }
        long millis = ReplayVehicleClock.millis();
        frameTimeCounter = ReplayShaderClock.animationSeconds(millis);
        lastFrameTime = ReplayShaderClock.frameSeconds(ReplayShaderClock.export(), millis, replayMod$previousMillis);
        replayMod$previousMillis = millis;
        lastStartTime = OptionalLong.of(startTime);
        ci.cancel();
    }

    @Inject(method = "reset()V", at = @At("RETURN"), require = 1)
    private void replayMod$reset(CallbackInfo ci) { replayMod$previousMillis = -1; }
}
