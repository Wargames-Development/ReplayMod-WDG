package com.replaymod.replay.mixin;

import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.replay.compat.ReplayShaderClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.coderbot.iris.uniforms.SystemTimeUniforms$FrameCounter", remap = false)
public abstract class MixinAngelicaShaderFrameCounter {
    @Shadow private int count;

    @Inject(method = "beginFrame()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void replayMod$outputFrame(CallbackInfo ci) {
        RenderInfo export = ReplayShaderClock.export();
        if (export != null) {
            count = ReplayShaderClock.frameCounter(export);
            ci.cancel();
        }
    }
}
