package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.RecordedAircraftFrames;
import com.replaymod.replay.compat.ReplayAircraftExport;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use one recorded-time pose for loaded/distant aircraft in replay viewing and export. */
@Pseudo
@Mixin(targets="mcheli.render.MCH_RenderFarVehicle$SmoothedPose", remap=false)
public abstract class MixinMCHExportPose_MCHeli implements ReplayAircraftExport.PoseHook {
    @Shadow private UUID aircraftUuid;
    @Shadow private String entityClassName, entityName;
    @Shadow private double x, y, z;
    @Shadow private float yaw, pitch, roll;
    @Override public double[] replayMod$bodyPose() { return new double[]{x,y,z,yaw,pitch,roll}; }
    @Override public int replayMod$packedLight() {
        RecordedAircraftFrames.Pose p=ReplayAircraftExport.pose(aircraftUuid,entityClassName,entityName);
        return p==null ? -1 : p.packedLight;
    }

    @Inject(method={"updateFromAircraft", "update"}, at=@At("RETURN"), require=0)
    private void replayMod$sampleExportFrame(CallbackInfo ci) {
        RecordedAircraftFrames.Pose p=ReplayAircraftExport.pose(aircraftUuid,entityClassName,entityName);
        if (p!=null) { x=p.x; y=p.y; z=p.z; yaw=p.yaw; pitch=p.pitch; roll=p.roll; }
    }
}
