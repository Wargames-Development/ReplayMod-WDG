package com.replaymod.replay.mixin;

import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    // Angelica redirects Minecraft's call to updateCameraAndRender to skip
    // iconified windows. Enter at the callee instead of competing for that call.
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"), cancellable = true)
    private void renderStudioViewer(float partial, CallbackInfo ci) {
        if (com.replaymod.studio.StudioPreview.isCapturing()) return;
        com.replaymod.studio.CinematicEditor.restorePreviewWorkspace();
        if (mc.currentScreen instanceof com.replaymod.studio.CinematicEditor) {
            ((com.replaymod.studio.CinematicEditor) mc.currentScreen).renderViewer((EntityRenderer) (Object) this, partial);
            ci.cancel();
        }
    }
    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z", remap = false))
    private boolean viewerScreenEvents(cpw.mods.fml.common.eventhandler.EventBus bus, cpw.mods.fml.common.eventhandler.Event event) {
        if (com.replaymod.studio.StudioPreview.isCapturing() && event instanceof net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent) return event instanceof net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre;
        return bus.post(event);
    }
    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(FZII)V"))
    private void cleanViewer(net.minecraft.client.gui.GuiIngame hud, float partial, boolean screen, int x, int y) {
        if (com.replaymod.studio.StudioPreview.isCapturing()) mc.entityRenderer.setupOverlayRendering();
        else hud.renderGameOverlay(partial, screen, x, y);
    }
    @Shadow private float prevCamRoll;
    @Shadow private float camRoll;
    @Shadow private Minecraft mc;

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void applyRoll(float partialTicks, CallbackInfo ci) {
        EntityLivingBase view = mc.renderViewEntity;
        if (view instanceof CameraEntity) {
            prevCamRoll = camRoll = ((CameraEntity) view).roll;
        }
    }
}
