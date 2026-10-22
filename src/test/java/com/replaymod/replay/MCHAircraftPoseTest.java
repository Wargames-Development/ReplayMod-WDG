package com.replaymod.replay;

import com.replaymod.replay.mixin.MixinMCHAircraftPose_MCHeli;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class MCHAircraftPoseTest {
    @Test public void seekUsesTheWholeRecordedHeadingInsteadOfOneOfThreeInterpolationSteps() throws Exception {
        Aircraft plane=allocate();
        plane.rotationYaw=0; plane.rotationPitch=0;
        plane.target(-23.5,98.1,328.5,-90,-16.875,28.5f,3);
        plane.replayMod$settlePose();
        assertEquals(-90,plane.rotationYaw,0); assertEquals(-90,plane.prevRotationYaw,0);
        assertEquals(-16.875,plane.rotationPitch,0); assertEquals(-16.875,plane.prevRotationPitch,0);
        assertEquals(28.5,plane.roll,0); assertEquals(28.5,plane.previousRoll(),0);
        assertEquals(-23.5,plane.posX,0); assertEquals(98.1,plane.posY,0); assertEquals(328.5,plane.posZ,0);
        assertEquals(plane.posX,plane.lastTickPosX,0); assertEquals(plane.posY,plane.prevPosY,0);
        assertEquals(0,plane.remaining());
    }
    @Test public void aSpawnWithoutMovementKeepsItsSpawnHeadingAndPosition() throws Exception {
        Aircraft plane=allocate(); plane.rotationYaw=-90; plane.rotationPitch=12;
        plane.posX=10; plane.posY=100; plane.posZ=20; plane.serverRoll=-33;
        plane.replayMod$settlePose();
        assertEquals(-90,plane.rotationYaw,0); assertEquals(12,plane.rotationPitch,0);
        assertEquals(10,plane.posX,0); assertEquals(100,plane.posY,0); assertEquals(20,plane.posZ,0);
        assertEquals(-33,plane.previousRoll(),0);
    }
    @Test public void settlingIsIdempotentAndDoesNotAddAConstantHeadingCorrection() throws Exception {
        Aircraft plane=allocate(); plane.rotationYaw=179;
        plane.target(1,2,3,-179,25,-75,2);
        plane.replayMod$settlePose(); plane.replayMod$settlePose();
        assertEquals(-179,plane.rotationYaw,0); assertEquals(-179,plane.prevRotationYaw,0);
        assertEquals(25,plane.rotationPitch,0); assertEquals(-75,plane.previousRoll(),0);
    }
    private static Aircraft allocate() throws Exception {
        Field field=Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (Aircraft)((Unsafe)field.get(null)).allocateInstance(Aircraft.class);
    }
    private static class Aircraft extends MixinMCHAircraftPose_MCHeli {
        float serverRoll,roll;
        Aircraft() { super(null); }
        void target(double x,double y,double z,double yaw,double pitch,float roll,int ticks) {
            aircraftX=x; aircraftY=y; aircraftZ=z; aircraftYaw=yaw; aircraftPitch=pitch;
            serverRoll=roll; aircraftPosRotInc=ticks;
        }
        int remaining() { return aircraftPosRotInc; }
        float previousRoll() { return prevRotationRoll; }
        @Override public float getServerRoll() { return serverRoll; }
        @Override public void setRotRoll(float value) { roll=value; }
        @Override public void setPosition(double x,double y,double z) { posX=x; posY=y; posZ=z; }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
    }
}
