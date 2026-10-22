package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayVehiclePose;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** MCHR stores network targets outside Entity's vanilla interpolation fields. */
@Pseudo
@Mixin(targets = "mcheli.aircraft.MCH_EntityAircraft", remap = false)
public abstract class MixinMCHAircraftPose_MCHeli extends Entity implements ReplayVehiclePose {
    @Shadow protected int aircraftPosRotInc;
    @Shadow protected double aircraftX, aircraftY, aircraftZ, aircraftYaw, aircraftPitch;
    @Shadow public float prevRotationRoll;
    @Shadow public abstract float getServerRoll();
    @Shadow public abstract void setRotRoll(float roll);

    protected MixinMCHAircraftPose_MCHeli(World world) { super(world); }

    @Override public void replayMod$settlePose() {
        // Do not use zero-initialized targets on an aircraft which has only spawned.
        if (aircraftPosRotInc > 0) {
            setPosition(aircraftX, aircraftY, aircraftZ);
            rotationYaw = (float) aircraftYaw;
            rotationPitch = (float) aircraftPitch;
            aircraftPosRotInc = 0;
        }
        float roll = getServerRoll();
        setRotRoll(roll);
        prevRotationRoll = roll;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        lastTickPosX = prevPosX = posX;
        lastTickPosY = prevPosY = posY;
        lastTickPosZ = prevPosZ = posZ;
    }
}
