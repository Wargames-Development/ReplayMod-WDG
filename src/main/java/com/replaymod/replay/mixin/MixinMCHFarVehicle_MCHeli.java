package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayVehicleClock;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mcheli.render.MCH_RenderFarVehicle", remap = false)
public abstract class MixinMCHFarVehicle_MCHeli {
    @Shadow @Final private Map<Integer, ?> smoothedPoses;
    @Unique private long replayMod$poseRevision = Long.MIN_VALUE;

    @Redirect(method = "onRenderWorldLast", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"))
    private long replayMod$clock() { return ReplayVehicleClock.millis(); }

    // MCHR's precise loaded-aircraft attitude smoothing uses a separate nano
    // clock. Wall time makes a slow export frame consume its entire blend.
    @Redirect(method = "onRenderWorldLast", at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"), require = 0)
    private long replayMod$renderClock() { return ReplayVehicleClock.nanos(); }

    // The normal (non-thermal) lightmap call is between the thermal call and
    // finally's restoration call. A loaded entity can query an unloaded chunk
    // even while its recorded body is visible in fully lit sky.
    @Redirect(method="renderContact", at=@At(value="INVOKE",
            target="Lnet/minecraft/client/renderer/OpenGlHelper;setLightmapTextureCoords(IFF)V", ordinal=1, remap=true))
    private void replayMod$recordedLightmap(int unit, float block, float sky,
            net.minecraft.client.Minecraft mc,
            @org.spongepowered.asm.mixin.injection.Coerce Object pose,
            @org.spongepowered.asm.mixin.injection.Coerce Object definition,
            @org.spongepowered.asm.mixin.injection.Coerce net.minecraft.entity.Entity aircraft,
            float partialTicks, int brightness, boolean gpuDepth, boolean precisionPass,
            double depthNear, double depthFar) {
        int light=replayMod$recordedLight(pose);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(unit,
            light<0 ? block : light & 0xffff, light<0 ? sky : (light >>> 16) & 0xffff);
    }

    private static int replayMod$recordedLight(Object pose) {
        return ReplayVehicleClock.active() && pose instanceof com.replaymod.replay.compat.ReplayAircraftExport.PoseHook
            ? ((com.replaymod.replay.compat.ReplayAircraftExport.PoseHook)pose).replayMod$packedLight() : -1;
    }

    @Inject(method = "onRenderWorldLast", at = @At("HEAD"))
    private void replayMod$discardPreviousSeekPose(CallbackInfo ci) {
        if (ReplayVehicleClock.active() && replayMod$poseRevision != ReplayVehicleClock.revision()) {
            smoothedPoses.clear();
            replayMod$poseRevision = ReplayVehicleClock.revision();
        }
    }

    @Inject(method = "renderContact", at = @At(value = "INVOKE", target = "Lmcheli/aircraft/MCH_RenderAircraft;renderAircraft(Lmcheli/aircraft/MCH_EntityAircraft;DDDFFFF)V"), require = 0)
    private void replayMod$renderAttachedRiders(net.minecraft.client.Minecraft mc,
            @org.spongepowered.asm.mixin.injection.Coerce Object pose,
            @org.spongepowered.asm.mixin.injection.Coerce Object definition,
            @org.spongepowered.asm.mixin.injection.Coerce net.minecraft.entity.Entity aircraft,
            float partialTicks, int brightness, boolean gpuDepth, boolean precisionPass,
            double depthNear, double depthFar, CallbackInfo ci) {
        if (ReplayVehicleClock.active() && aircraft != null && pose instanceof com.replaymod.replay.compat.ReplayAircraftExport.PoseHook)
            com.replaymod.replay.compat.ReplayRiders.render(aircraft, partialTicks, ((com.replaymod.replay.compat.ReplayAircraftExport.PoseHook)pose).replayMod$bodyPose());
    }
}
