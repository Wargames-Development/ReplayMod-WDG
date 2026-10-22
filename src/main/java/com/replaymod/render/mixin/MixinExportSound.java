package com.replaymod.render.mixin;

import com.replaymod.render.audio.ExportAudio;
import com.replaymod.render.audio.ReplaySoundPlayback;
import java.util.Map;
import java.net.URL;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.minecraft.entity.player.EntityPlayer;
import paulscode.sound.SoundSystem;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class MixinExportSound {
    @Shadow @Final private Map<String,ISound> playingSounds;
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void capture(ISound sound, CallbackInfo ci) {
        com.replaymod.replay.ReplayModReplay mod = com.replaymod.replay.ReplayModReplay.instance;
        if (mod != null && mod.getReplayHandler() != null &&
                (mod.getReplayHandler().isApplyingEditorSeek() || com.replaymod.replay.compat.ReplayEffects.isSeeking())) { ci.cancel(); return; }
        ReplaySoundPlayback.started(sound);
        com.replaymod.replay.compat.ReplayEffects.observeSound(sound);
        if (ExportAudio.capture((SoundManager) (Object) this, sound)) ci.cancel();
    }
    @Inject(method = "stopSound", at = @At("HEAD"))
    private void stop(ISound sound, CallbackInfo ci) { ExportAudio.stop(sound); ReplaySoundPlayback.stop(sound); }
    @Inject(method = "stopAllSounds", at = @At("HEAD"))
    private void stopAll(CallbackInfo ci) { ExportAudio.stopAll(); ReplaySoundPlayback.clear(); }
    @Inject(method = "resumeAllSounds", at = @At("RETURN"))
    private void resumed(CallbackInfo ci) { ReplaySoundPlayback.resumed(); }
    @Redirect(method="resumeAllSounds",at=@At(value="INVOKE",target="Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;play(Ljava/lang/String;)V",remap=false))
    private void resumeWorldSound(@Coerce SoundSystem system,String channel) {
        if (ReplaySoundPlayback.mayResume(playingSounds.get(channel))) system.play(channel);
    }
    @Redirect(method="setListener",at=@At(value="INVOKE",target="Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;setListenerPosition(FFF)V",remap=false))
    private void frame(@Coerce SoundSystem system,float x,float y,float z,EntityPlayer listener,float partialTicks) {
        system.setListenerPosition(x,y,z);
        ReplaySoundPlayback.frame(system,playingSounds,x,y,z,partialTicks);
    }
    @Redirect(method="playSound",at=@At(value="INVOKE",target="Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;newSource(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V",remap=false))
    private void enginePriority(@Coerce SoundSystem system,boolean priority,String channel,URL url,String file,boolean repeat,float x,float y,float z,int attenuation,float distance,ISound sound) {
        system.newSource(ReplaySoundPlayback.priority(priority,sound),channel,url,file,repeat,x,y,z,attenuation,distance);
    }
    @Inject(method = "isSoundPlaying", at = @At("HEAD"), cancellable = true)
    private void isPlaying(ISound sound, CallbackInfoReturnable<Boolean> cir) {
        Boolean playing = ExportAudio.isPlaying(sound);
        if (playing != null) cir.setReturnValue(playing);
    }
    @Inject(method = "playDelayedSound", at = @At("HEAD"), cancellable = true)
    private void delay(ISound sound, int ticks, CallbackInfo ci) { if (ExportAudio.delay((SoundManager) (Object) this, sound, ticks)) ci.cancel(); }
}
