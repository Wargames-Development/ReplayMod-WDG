package com.replaymod.core.mixin;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.LoadingScreenRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World resets during editor scrubs must not draw/present a separate dirt screen. */
@Mixin(LoadingScreenRenderer.class)
public abstract class MixinStudioLoadingScreen {
    private boolean editorOwnsLoading() {
        ReplayModReplay mod = ReplayModReplay.instance;
        ReplayHandler replay = mod == null ? null : mod.getReplayHandler();
        return replay != null && replay.keepEditorVisible();
    }
    @Inject(method = {"resetProgressAndMessage", "displayLoadingString", "displaySavingString", "func_73722_d"}, at = @At("HEAD"), cancellable = true)
    private void keepEditor(String text, CallbackInfo ci) { if (editorOwnsLoading()) ci.cancel(); }
    @Inject(method = "setLoadingProgress", at = @At("HEAD"), cancellable = true)
    private void keepEditorProgress(int progress, CallbackInfo ci) { if (editorOwnsLoading()) ci.cancel(); }
}
