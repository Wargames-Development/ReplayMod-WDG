package com.replaymod.replay.mixin;

import com.replaymod.extras.advancedscreenshots.AdvancedScreenshots;
import com.replaymod.replay.ReplayModReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public abstract class MixinKeyboardListener {
    @Inject(
            method = "dispatchKeypresses",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/ScreenShotHelper;saveScreenshot(Ljava/io/File;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;"
            ),
            cancellable = true
    )
    private void takeScreenshot(CallbackInfo ci) {
        if (ReplayModReplay.instance.getReplayHandler() != null) {
            AdvancedScreenshots.take();
            ci.cancel();
        }
    }
}
