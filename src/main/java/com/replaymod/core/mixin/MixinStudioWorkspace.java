package com.replaymod.core.mixin;

import com.replaymod.studio.StudioPreview;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinStudioWorkspace {
    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(F)V"))
    private void retainFreeCameraMouse(CallbackInfo ci) {
        com.replaymod.replay.ReplayModReplay mod = com.replaymod.replay.ReplayModReplay.instance;
        if (mod != null && mod.getReplayHandler() != null) {
            com.replaymod.replay.ReplayModReplay.keepFreeCameraCaptured((Minecraft) (Object) this,
                    mod.getReplayHandler(), org.lwjgl.opengl.Display.isActive(), org.lwjgl.input.Mouse.isGrabbed());
        }
    }
    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    private void keepEditorDuringSeek(net.minecraft.client.gui.GuiScreen screen, CallbackInfo ci) {
        com.replaymod.replay.ReplayModReplay mod = com.replaymod.replay.ReplayModReplay.instance;
        com.replaymod.replay.ReplayHandler replay = mod == null ? null : mod.getReplayHandler();
        if (replay != null && replay.keepEditorVisible() && (screen == null || screen instanceof net.minecraft.client.gui.GuiDownloadTerrain || screen instanceof com.replaymod.replay.gui.screen.GuiOpeningReplay)) ci.cancel();
    }
    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/FMLCommonHandler;onRenderTickStart(F)V", remap = false))
    private void continueSeekOutsideEditor(CallbackInfo ci) {
        if (((Minecraft) (Object) this).currentScreen instanceof com.replaymod.studio.CinematicEditor) return;
        com.replaymod.replay.ReplayModReplay mod = com.replaymod.replay.ReplayModReplay.instance;
        if (mod != null && mod.getReplayHandler() != null) mod.getReplayHandler().tickEditorSeek();
    }
    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", remap = false, shift = At.Shift.AFTER))
    private void compositeWorkspace(CallbackInfo ci) { StudioPreview.drawWorkspace(); }
}
