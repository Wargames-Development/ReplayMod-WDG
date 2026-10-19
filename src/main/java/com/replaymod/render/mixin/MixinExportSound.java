package com.replaymod.render.mixin;

import com.replaymod.render.audio.ExportAudio;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class MixinExportSound {
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void capture(ISound sound, CallbackInfo ci) {
        com.replaymod.replay.compat.ReplayEffects.observeSound(sound);
        if (ExportAudio.capture((SoundManager) (Object) this, sound)) ci.cancel();
    }
    @Inject(method = "stopSound", at = @At("HEAD"))
    private void stop(ISound sound, CallbackInfo ci) { ExportAudio.stop(sound); }
    @Inject(method = "stopAllSounds", at = @At("HEAD"))
    private void stopAll(CallbackInfo ci) { ExportAudio.stopAll(); }
}
