package com.replaymod.render.mixin;

import com.replaymod.render.RenderSettings;
import com.replaymod.render.hooks.EntityRendererHandler;
import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.replaymod.core.versions.MCVer.*;

import net.minecraft.client.renderer.EntityRenderer;

@Mixin(value = EntityRenderer.class)
public abstract class Mixin_StabilizeCamera {
    private EntityRendererHandler replayModRender$getStabilizeHandler() {
        return ((EntityRendererHandler.IEntityRenderer) getMinecraft().entityRenderer).replayModRender_getHandler();
    }

    private float orgYaw;
    private float orgPitch;
    private float orgPrevYaw;
    private float orgPrevPitch;
    private float orgRoll;

    // Only relevant on 1.13+ (previously MC always used the non-head yaw) and only for LivingEntity view entities.
    private float orgHeadYaw;
    private float orgPrevHeadYaw;

    @Inject(method = "setupCameraTransform", at = @At("HEAD"))
    private void replayModRender_beforeSetupCameraTransform(float partialTicks, int renderPass, CallbackInfo ci) {
        if (replayModRender$getStabilizeHandler() != null) {
            Entity entity = getMinecraft().renderViewEntity;
            orgYaw = entity.rotationYaw;
            orgPitch = entity.rotationPitch;
            orgPrevYaw = entity.prevRotationYaw;
            orgPrevPitch = entity.prevRotationPitch;
            orgRoll = entity instanceof CameraEntity ? ((CameraEntity) entity).roll : 0;
            if (entity instanceof EntityLivingBase) {
                orgHeadYaw = ((EntityLivingBase) entity).rotationYawHead;
                orgPrevHeadYaw = ((EntityLivingBase) entity).prevRotationYawHead;
            }
        }
    }

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void replayModRender_resetRotationIfNeeded(float partialTicks, CallbackInfo ci) {
        if (replayModRender$getStabilizeHandler() != null) {
            Entity entity = getMinecraft().renderViewEntity;
            RenderSettings settings = replayModRender$getStabilizeHandler().getSettings();
            if (settings.isStabilizeYaw()) {
                entity.prevRotationYaw = entity.rotationYaw = 0;
                if (entity instanceof EntityLivingBase) {
                    ((EntityLivingBase) entity).prevRotationYawHead = ((EntityLivingBase) entity).rotationYawHead = 0;
                }
            }
            if (settings.isStabilizePitch()) {
                entity.prevRotationPitch = entity.rotationPitch = 0;
            }
            if (settings.isStabilizeRoll() && entity instanceof CameraEntity) {
                ((CameraEntity) entity).roll = 0;
            }
        }
    }

    @Inject(method = "setupCameraTransform", at = @At("RETURN"))
    private void replayModRender_afterSetupCameraTransform(float partialTicks, int renderPass, CallbackInfo ci) {
        if (replayModRender$getStabilizeHandler() != null) {
            Entity entity = getMinecraft().renderViewEntity;
            entity.rotationYaw = orgYaw;
            entity.rotationPitch = orgPitch;
            entity.prevRotationYaw = orgPrevYaw;
            entity.prevRotationPitch = orgPrevPitch;
            if (entity instanceof CameraEntity) {
                ((CameraEntity) entity).roll = orgRoll;
            }
            if (entity instanceof EntityLivingBase) {
                ((EntityLivingBase) entity).rotationYawHead = orgHeadYaw;
                ((EntityLivingBase) entity).prevRotationYawHead = orgPrevHeadYaw;
            }
        }
    }
}
