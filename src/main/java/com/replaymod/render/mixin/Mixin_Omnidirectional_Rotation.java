package com.replaymod.render.mixin;

import com.replaymod.render.capturer.CubicOpenGlFrameCapturer;
import com.replaymod.render.hooks.EntityRendererHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.lwjgl.opengl.GL11;

import static com.replaymod.core.versions.MCVer.getMinecraft;

@Mixin(value = net.minecraft.client.renderer.EntityRenderer.class)
public abstract class Mixin_Omnidirectional_Rotation {
    private EntityRendererHandler replayModRender$getOmnidirectionalHandler() {
        return ((EntityRendererHandler.IEntityRenderer) getMinecraft().entityRenderer).replayModRender_getHandler();
    }

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void replayModRender_setupCubicFrameRotation(
            CallbackInfo ci
    ) {
        if (replayModRender$getOmnidirectionalHandler() != null && replayModRender$getOmnidirectionalHandler().data instanceof CubicOpenGlFrameCapturer.Data) {
            CubicOpenGlFrameCapturer.Data data = (CubicOpenGlFrameCapturer.Data) replayModRender$getOmnidirectionalHandler().data;
            float angle = 0;
            float x = 0;
            float y = 0;
            switch (data) {
                case FRONT:
                    angle = 0;
                    y = 1;
                    break;
                case RIGHT:
                    angle = 90;
                    y = 1;
                    break;
                case BACK:
                    angle = 180;
                    y = 1;
                    break;
                case LEFT:
                    angle = -90;
                    y = 1;
                    break;
                case TOP:
                    angle = -90;
                    x = 1;
                    break;
                case BOTTOM:
                    angle = 90;
                    x = 1;
                    break;
            }
            GL11.glRotatef(angle, x, y, 0);
        }
        if (replayModRender$getOmnidirectionalHandler() != null && replayModRender$getOmnidirectionalHandler().omnidirectional) {
            // Minecraft goes back a little so we have to revert that
            GL11.glTranslatef(0.0F, 0.0F, 0.1F);
        }
    }
}
