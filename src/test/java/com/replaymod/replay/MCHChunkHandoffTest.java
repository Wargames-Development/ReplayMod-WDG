package com.replaymod.replay;

import com.replaymod.replay.compat.ReplayVehiclePose;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class MCHChunkHandoffTest {
    @Test public void vehicleChunkHandoffSettlesTheNetworkTargetWithoutAdvancingFlightOrAnimation() {
        Vehicle vehicle=new Vehicle();
        FullReplaySender.settleChunkUnloadPose(vehicle);
        assertEquals(1,vehicle.settled); assertEquals(0,vehicle.updates);
        assertEquals(32,vehicle.posX,0); assertEquals(vehicle.posX,vehicle.lastTickPosX,0);
    }
    @Test public void vanillaChunkHandoffKeepsItsExistingFourInterpolationUpdates() {
        MovingEntity entity=new MovingEntity();
        FullReplaySender.settleChunkUnloadPose(entity);
        assertEquals(4,entity.updates);
    }
    private static class MovingEntity extends Entity {
        int updates;
        MovingEntity() { super(null); }
        @Override public void onUpdate() { updates++; }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound nbt) { }
        @Override protected void writeEntityToNBT(NBTTagCompound nbt) { }
    }
    private static final class Vehicle extends MovingEntity implements ReplayVehiclePose {
        int settled;
        @Override public void replayMod$settlePose() { settled++; setPosition(32,120,0); lastTickPosX=prevPosX=posX; }
    }
}
