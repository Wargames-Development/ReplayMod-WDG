package com.replaymod.replay.mixin;

import com.replaymod.replay.ReplayModReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disables MCHR's live input and local vehicle-camera callbacks during playback. */
@Pseudo
@Mixin(targets = "mcheli.MCH_ClientCommonTickHandler", remap = false)
public abstract class MixinMCHClientCommonTickHandler_MCHeli {
    @Inject(method = {
            "onTick", "onTickPost", "onRenderTickPre", "onRenderTickPost",
            "onPlayerTickPre", "onPlayerTickPost", "onRenderGameOverlayEvent"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void replayModCompat$disableLiveControls(CallbackInfo ci) {
        // Entity updates, renderers and incoming mod packets are separate from
        // these callbacks and must continue to animate the recorded vehicles.
        if (ReplayModReplay.instance != null && ReplayModReplay.instance.getReplayHandler() != null) {
            ci.cancel();
        }
    }
}
