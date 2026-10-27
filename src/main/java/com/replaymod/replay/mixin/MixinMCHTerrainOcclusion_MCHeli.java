package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayTerrainVisibility;
import com.replaymod.replay.compat.ReplayVehicleClock;
import com.replaymod.replay.compat.TerrainVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native WGMap answers win; only unknown replay rays need the selected terrain-cache fallback. */
@Pseudo
@Mixin(targets="mcheli.render.MCH_WGMapOcclusion",remap=false)
public abstract class MixinMCHTerrainOcclusion_MCHeli {
    @Inject(method="traceSegment",at=@At("RETURN"),cancellable=true)
    private static void replayMod$terrain(int dimension,double x0,double y0,double z0,double x1,double y1,double z1,CallbackInfoReturnable<Object> callback) {
        if(!ReplayVehicleClock.active() || !(callback.getReturnValue() instanceof Enum)) return;
        Enum<?> nativeResult=(Enum<?>)callback.getReturnValue();
        if(nativeResult.name().equals("CLEAR") || nativeResult.name().equals("BLOCKED")) return;
        TerrainVisibility.Result result=ReplayTerrainVisibility.trace(dimension,x0,y0,z0,x1,y1,z1);
        if(result!=TerrainVisibility.Result.UNKNOWN)
            callback.setReturnValue(Enum.valueOf((Class)nativeResult.getDeclaringClass(),result.name()));
    }
    @Inject(method="terrainGeneration",at=@At("RETURN"),cancellable=true)
    private static void replayMod$terrainGeneration(CallbackInfoReturnable<Long> callback) {
        if(ReplayVehicleClock.active()) callback.setReturnValue(callback.getReturnValue()*31+ReplayTerrainVisibility.generation());
    }
}
