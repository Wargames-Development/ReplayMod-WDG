package com.replaymod.render.audio;

import java.nio.file.*;
import org.junit.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class VehicleSoundTest {
    @Test public void fastFollowCameraDoesNotOutrunTheCompressedEngineSourceBetweenTicks() {
        // A 400-block/s camera moves twenty blocks between 20-Hz source updates.
        // With the stale source, the engine drops out although the plane stays 24 blocks ahead.
        double stale=VehicleSound.position(24,100,0,0,100,0,160,false)[0];
        assertTrue(Math.abs(stale-20)>16);
        for(int frame=0;frame<120;frame++) {
            double camera=frame*400.0/60;
            double[] source=VehicleSound.position(camera+24,100,0,camera,100,0,160,false);
            assertEquals(2.4,source[0]-camera,0.000001);
            assertEquals(0.85,1-Math.abs(source[0]-camera)/16,0.000001);
        }
    }
    @Test public void rangeAndRiderBehaviorMatchMchrAndCameraCutsDoNotLeaveStaleCoordinates() {
        assertArrayEquals(new double[]{8,12,16},VehicleSound.position(80,120,160,0,0,0,160,false),0);
        assertArrayEquals(new double[]{80,120,160},VehicleSound.position(80,120,160,0,0,0,160,true),0);
        double[] far=VehicleSound.position(80,120,160,1000,0,0,160,false);
        assertTrue(Math.abs(far[0]-1000)>16);
        assertArrayEquals(new double[]{80,120,160},VehicleSound.position(80,120,160,80,120,160,160,false),0);
    }
    @Test public void installedMchrExposesTheExactEngineBindingContract() throws Exception {
        String root=System.getenv("REPLAYMOD_TEST_MCHR"); assumeNotNull(root);
        ClassNode updater=read(root,"mcheli/aircraft/MCH_SoundUpdater");
        assertField(updater,"theAircraft","Lmcheli/aircraft/MCH_EntityAircraft;");
        assertMethod(updater,"update","()V");
        assertField(read(root,"mcheli/wrapper/W_SoundUpdater"),"es","Lmcheli/wrapper/W_Sound;");
        ClassNode aircraft=read(root,"mcheli/aircraft/MCH_EntityAircraft");
        assertMethod(aircraft,"getAcInfo","()Lmcheli/aircraft/MCH_AircraftInfo;");
        assertMethod(aircraft,"getTypeName","()Ljava/lang/String;");
        // Minecraft Entity is obfuscated in the distributable mod; match the method and result.
        assertTrue(aircraft.methods.stream().anyMatch(m->m.name.equals("getSeatIdByEntity") && m.desc.endsWith(")I")));
        assertTrue(read(root,"mcheli/aircraft/MCH_AircraftInfo").fields.stream().anyMatch(f->f.name.equals("soundRange")));
    }
    private static ClassNode read(String root,String name) throws Exception { ClassNode node=new ClassNode(); new ClassReader(Files.readAllBytes(Paths.get(root,name+".class"))).accept(node,ClassReader.SKIP_FRAMES); return node; }
    private static void assertField(ClassNode node,String name,String desc) { assertTrue(name,node.fields.stream().anyMatch(f->f.name.equals(name)&&f.desc.equals(desc))); }
    private static void assertMethod(ClassNode node,String name,String desc) { assertTrue(name,node.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc))); }
}
