package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayRenderViewContext;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the lifetime of RenderManager entity rendering so optional mod
 * compatibility hooks can safely substitute visual viewer identity without
 * affecting normal replay ticking, physics, or input/control code.
 */
@Mixin(RenderManager.class)
public abstract class MixinRenderManagerReplayViewContext {
    @Inject(method = "doRenderEntity", at = @At("HEAD"))
    private void replayModCompat$enterEntityRender(Entity entity, double x, double y, double z,
                                                    float yaw, float partialTicks, boolean hideDebugBox,
                                                    CallbackInfoReturnable<Boolean> cir) {
        ReplayRenderViewContext.enterEntityRender();
    }

    @Inject(method = "doRenderEntity", at = @At("RETURN"))
    private void replayModCompat$exitEntityRender(Entity entity, double x, double y, double z,
                                                   float yaw, float partialTicks, boolean hideDebugBox,
                                                   CallbackInfoReturnable<Boolean> cir) {
        ReplayRenderViewContext.exitEntityRender();
    }
}
