package com.replaymod.replay.mixin;

import com.replaymod.core.events.PreRenderHandCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.HandRenderer", remap = false)
public abstract class MixinAngelicaShaderHand {
    // Angelica renders hands outside EntityRenderer.renderHand and uses its
    // own event bus rather than Forge's RenderHandEvent. Preserve ReplayMod's
    // free-camera/omnidirectional suppression and spectated-player hand state.
    @Inject(method = "canRender", at = @At("HEAD"), cancellable = true, require = 1)
    private void replayMod$hideCameraHand(CallbackInfoReturnable<Boolean> ci) {
        if (PreRenderHandCallback.EVENT.invoker().preRenderHand()) ci.setReturnValue(false);
    }
}
