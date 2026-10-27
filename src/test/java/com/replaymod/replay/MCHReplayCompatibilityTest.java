package com.replaymod.replay;

import com.replaymod.replay.compat.ReplayVehicleClock;
import com.replaymod.replay.mixin.MixinMCHFarVehicle_MCHeli;
import com.replaymod.replay.mixin.MixinMCHExportPose_MCHeli;
import com.replaymod.replay.mixin.MixinMCHTargetMarker_MCHeli;
import com.replaymod.replay.compat.RecordedAircraftFrames;
import com.replaymod.replay.compat.ReplayAircraftExport;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.*;
import sun.misc.Unsafe;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class MCHReplayCompatibilityTest {
    @Test public void actualF15ClipRetainsRecordedSkylightThroughoutAll600VideoFrames() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_LIGHTING_RECORDING"); assumeNotNull(file);
        ReplayModReplay previous=ReplayModReplay.instance;
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file);
                java.io.InputStream input=zip.getInputStream(zip.getEntry("recording.tmcpr"))) {
            RecordedAircraftFrames frames=RecordedAircraftFrames.read(input); assertEquals(1,frames.trackCount());
            Field tracks=RecordedAircraftFrames.class.getDeclaredField("tracks"); tracks.setAccessible(true);
            java.util.UUID uuid=(java.util.UUID)((Map)tracks.get(frames)).keySet().iterator().next();
            set(null,ReplayAircraftExport.class,"frames",frames);
            ReplayModReplay module=allocate(ReplayModReplay.class);
            ReplayHandler replay=allocate(ReplayHandler.class); Sender sender=allocate(Sender.class);
            set(replay,ReplayHandler.class,"fullReplaySender",sender);
            set(module,ReplayModReplay.class,"replayHandler",replay); ReplayModReplay.instance=module;
            ExportPose pose=allocate(ExportPose.class);
            set(pose,MixinMCHExportPose_MCHeli.class,"aircraftUuid",uuid);
            set(pose,MixinMCHExportPose_MCHeli.class,"entityClassName","mcheli.plane.MCP_EntityPlane");
            set(pose,MixinMCHExportPose_MCHeli.class,"entityName","f15ex");
            for (int frame=0;frame<600;frame++) {
                sender.time=frame*1000/60;
                assertEquals("Recorded F15 frame "+frame,0x00f00000,pose.replayMod$packedLight());
            }
            ReplayAircraftExport.end(); assertEquals(-1,pose.replayMod$packedLight());
        } finally { ReplayAircraftExport.end(); ReplayModReplay.instance=previous; }
    }
    @Test public void recordedLightingReplacesMissingChunkLightOnlyDuringReplayAndPreservesActualDarkness() throws Exception {
        ReplayModReplay previous=ReplayModReplay.instance;
        try {
            Method light=MixinMCHFarVehicle_MCHeli.class.getDeclaredMethod("replayMod$recordedLight",Object.class);
            light.setAccessible(true);
            ReplayModReplay module=allocate(ReplayModReplay.class);
            set(module,ReplayModReplay.class,"replayHandler",allocate(ReplayHandler.class));
            for (final int recorded:new int[]{0x00f00000,0x00a00030,0,-1}) {
                ReplayAircraftExport.PoseHook pose=new ReplayAircraftExport.PoseHook() {
                    @Override public double[] replayMod$bodyPose() { return new double[6]; }
                    @Override public int replayMod$packedLight() { return recorded; }
                };
                ReplayModReplay.instance=module; assertEquals(recorded,light.invoke(null,pose));
                ReplayModReplay.instance=null; assertEquals(-1,light.invoke(null,pose));
            }
            ReplayModReplay.instance=module; assertEquals(-1,light.invoke(null,new Object()));
        } finally { ReplayModReplay.instance=previous; }
    }
    @Test public void replayViewingSharesExportPosesAcrossPauseSeekAndExportCleanup() throws Exception {
        ReplayModReplay previous=ReplayModReplay.instance;
        ReplayAircraftExport.close();
        try {
            ReplayModReplay module=allocate(ReplayModReplay.class);
            ReplayHandler replay=allocate(ReplayHandler.class); Sender sender=allocate(Sender.class);
            set(replay,ReplayHandler.class,"fullReplaySender",sender);
            set(module,ReplayModReplay.class,"replayHandler",replay); ReplayModReplay.instance=module;
            RecordedAircraftFrames frames=RecordedAircraftFrames.read(new java.io.ByteArrayInputStream(new byte[0]));
            java.util.UUID uuid=new java.util.UUID(1,2);
            Class<?> poseType=RecordedAircraftFrames.Pose.class;
            java.lang.reflect.Constructor<?> constructor=poseType.getDeclaredConstructor(double.class,double.class,double.class,
                float.class,float.class,float.class,String.class,String.class,boolean.class,int.class); constructor.setAccessible(true);
            String type="mcheli.plane.MCP_EntityPlane";
            java.util.NavigableMap<Integer,RecordedAircraftFrames.Pose> track=new java.util.TreeMap<>();
            track.put(1000,(RecordedAircraftFrames.Pose)constructor.newInstance(0d,120d,0d,0f,0f,0f,type,"f16",false,1));
            track.put(1100,(RecordedAircraftFrames.Pose)constructor.newInstance(10d,120d,0d,20f,0f,0f,type,"f16",false,2));
            Field tracks=RecordedAircraftFrames.class.getDeclaredField("tracks"); tracks.setAccessible(true);
            ((Map)tracks.get(frames)).put(uuid,track);
            set(null,ReplayAircraftExport.class,"playbackFrames",frames);
            set(null,ReplayAircraftExport.class,"playbackOwner",replay);
            for (int time:new int[]{1050,1050,1100,1000,1050}) {
                sender.time=time;
                RecordedAircraftFrames.Pose viewing=ReplayAircraftExport.pose(uuid,type,"f16"); assertNotNull(viewing);
                set(null,ReplayAircraftExport.class,"frames",frames);
                assertEquals(viewing.x,ReplayAircraftExport.pose(uuid,type,"f16").x,0);
                assertEquals(viewing.yaw,ReplayAircraftExport.pose(uuid,type,"f16").yaw,0);
                ReplayAircraftExport.end();
                assertEquals(viewing.x,ReplayAircraftExport.pose(uuid,type,"f16").x,0);
            }
            set(module,ReplayModReplay.class,"replayHandler",allocate(ReplayHandler.class));
            assertNull("An index cannot leak into a different replay",ReplayAircraftExport.pose(uuid,type,"f16"));
            set(module,ReplayModReplay.class,"replayHandler",replay);
            ReplayAircraftExport.close(); assertNull(ReplayAircraftExport.pose(uuid,type,"f16"));
        } finally { ReplayAircraftExport.close(); ReplayModReplay.instance=previous; }
    }
    @Test public void gameplayTrianglesAreHiddenOnlyWhileAReplayIsOpen() throws Exception {
        ReplayModReplay previous=ReplayModReplay.instance;
        try {
            Method hide=MixinMCHTargetMarker_MCHeli.class.getDeclaredMethod("replayMod$hideReplayMarkers",org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class);
            hide.setAccessible(true);
            ReplayModReplay.instance=null;
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> live=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<>("markers",true);
            hide.invoke(null,live); assertFalse("Live marker preferences are preserved",live.isCancelled());
            ReplayModReplay module=allocate(ReplayModReplay.class);
            set(module,ReplayModReplay.class,"replayHandler",allocate(ReplayHandler.class)); ReplayModReplay.instance=module;
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> replay=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<>("markers",true);
            hide.invoke(null,replay); assertTrue(replay.isCancelled()); assertEquals(Boolean.FALSE,replay.getReturnValue());
            set(module,ReplayModReplay.class,"replayHandler",null);
            live=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<>("markers",true);
            hide.invoke(null,live); assertFalse("Markers resume on closing replay",live.isCancelled());
        } finally { ReplayModReplay.instance=previous; }
    }
    @Test public void clockPausesAndRewindsWithReplayAndFallsBackToWallTimeOutsideReplay() throws Exception {
        ReplayModReplay previous=ReplayModReplay.instance;
        try {
            ReplayModReplay module=allocate(ReplayModReplay.class);
            ReplayHandler replay=allocate(ReplayHandler.class); Sender sender=allocate(Sender.class);
            set(replay,ReplayHandler.class,"fullReplaySender",sender);
            set(module,ReplayModReplay.class,"replayHandler",replay); ReplayModReplay.instance=module;
            sender.time=45000; assertEquals(45000,ReplayVehicleClock.millis());
            assertEquals(45000000000L,ReplayVehicleClock.nanos());
            assertEquals(ReplayVehicleClock.nanos(),ReplayVehicleClock.nanos());
            assertEquals(45000,ReplayVehicleClock.millis());
            sender.time=16000; assertEquals(16000,ReplayVehicleClock.millis());
            assertEquals(16000000000L,ReplayVehicleClock.nanos());
            ReplayModReplay.instance=null;
            long before=System.currentTimeMillis(),clock=ReplayVehicleClock.millis();
            assertTrue(clock>=before && clock<=System.currentTimeMillis());
            long beforeNanos=System.nanoTime(),nanos=ReplayVehicleClock.nanos();
            assertTrue(nanos>=beforeNanos && nanos<=System.nanoTime());
        } finally { ReplayModReplay.instance=previous; }
    }
    @Test public void seekInvalidatesFarVehiclePoseEvenWhenTheNextSeekHasTheSameTimestamp() throws Exception {
        ReplayModReplay previous=ReplayModReplay.instance;
        try {
            ReplayModReplay module=allocate(ReplayModReplay.class);
            set(module,ReplayModReplay.class,"replayHandler",allocate(ReplayHandler.class)); ReplayModReplay.instance=module;
            FarRenderer renderer=allocate(FarRenderer.class); Map<Integer,Object> poses=new HashMap<>();
            set(renderer,MixinMCHFarVehicle_MCHeli.class,"smoothedPoses",poses);
            set(renderer,MixinMCHFarVehicle_MCHeli.class,"replayMod$poseRevision",Long.MIN_VALUE);
            Method method=MixinMCHFarVehicle_MCHeli.class.getDeclaredMethod("replayMod$discardPreviousSeekPose",org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
            method.setAccessible(true); poses.put(1,new Object()); method.invoke(renderer,new Object[]{null}); assertTrue(poses.isEmpty());
            poses.put(1,new Object()); method.invoke(renderer,new Object[]{null}); assertEquals(1,poses.size());
            ReplayVehicleClock.invalidate(); method.invoke(renderer,new Object[]{null}); assertTrue(poses.isEmpty());
            ReplayModReplay.instance=null; poses.put(1,new Object()); ReplayVehicleClock.invalidate();
            method.invoke(renderer,new Object[]{null}); assertEquals(1,poses.size());
        } finally { ReplayModReplay.instance=previous; }
    }
    @Test public void installedMchrHasTheAircraftFieldsAndClockCallSitesUsedByTheOptionalHooks() throws Exception {
        String root=System.getenv("REPLAYMOD_TEST_MCHR"); assumeNotNull(root);
        ClassNode aircraft=read(Paths.get(root,"mcheli/aircraft/MCH_EntityAircraft.class"));
        assertNotNull(find(read(Paths.get(root,"mcheli/multiplay/MCH_GuiTargetMarker.class")),"isEnableEntityMarker","()Z"));
        Map<String,String> fields=new HashMap<>(); for(FieldNode field:aircraft.fields) fields.put(field.name,field.desc);
        assertEquals("I",fields.get("aircraftPosRotInc"));
        for(String name:new String[]{"aircraftX","aircraftY","aircraftZ","aircraftYaw","aircraftPitch"}) assertEquals(name,"D",fields.get(name));
        assertEquals("F",fields.get("prevRotationRoll"));
        assertNotNull(find(aircraft,"getServerRoll","()F")); assertNotNull(find(aircraft,"setRotRoll","(F)V"));
        assertClock(read(Paths.get(root,"mcheli/render/MCH_RenderFarVehicle.class")),"onRenderWorldLast");
        ClassNode far=read(Paths.get(root,"mcheli/render/MCH_RenderFarVehicle.class"));
        ClassNode terrain=read(Paths.get(root,"mcheli/render/MCH_WGMapOcclusion.class"));
        assertNotNull(find(terrain,"traceSegment","(IDDDDDD)Lmcheli/render/MCH_WGMapOcclusion$Result;"));
        assertNotNull(find(terrain,"terrainGeneration","()J"));
        assertNotNull("Riders use the same unified render pass as the body",find(far,"renderContact",
            "(Lnet/minecraft/client/Minecraft;Lmcheli/render/MCH_RenderFarVehicle$SmoothedPose;Lmcheli/render/MCH_RenderFarVehicle$RenderDefinition;Lmcheli/aircraft/MCH_EntityAircraft;FIZZDD)V"));
        assertTrue(hasCall(far,"renderContact","mcheli/aircraft/MCH_RenderAircraft","renderAircraft"));
        MethodNode contact=find(far,"renderContact",
            "(Lnet/minecraft/client/Minecraft;Lmcheli/render/MCH_RenderFarVehicle$SmoothedPose;Lmcheli/render/MCH_RenderFarVehicle$RenderDefinition;Lmcheli/aircraft/MCH_EntityAircraft;FIZZDD)V");
        // The normal lightmap call follows the thermal full-bright call.
        int lightCalls=0;
        assertNotNull(contact);
        for (AbstractInsnNode insn:contact.instructions.toArray()) if (insn instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)insn;
            if (call.owner.equals("net/minecraft/client/renderer/OpenGlHelper") && call.name.equals("func_77475_a")
                    && call.desc.equals("(IFF)V")) lightCalls++;
        }
        assertTrue("Thermal, normal and lightmap restoration calls must remain available",lightCalls>=3);
        assertTrue("Installed MCHR precise aircraft rotation needs the nano clock hook",hasCall(far,"onRenderWorldLast","java/lang/System","nanoTime"));
        ClassNode pose=read(Paths.get(root,"mcheli/render/MCH_RenderFarVehicle$SmoothedPose.class"));
        for (Field reflected:MixinMCHExportPose_MCHeli.class.getDeclaredFields()) {
            String descriptor=org.spongepowered.asm.lib.Type.getDescriptor(reflected.getType());
            assertTrue("Installed render pose field "+reflected.getName(),pose.fields.stream().anyMatch(f -> f.name.equals(reflected.getName()) && f.desc.equals(descriptor)));
        }
        assertNotNull(find(pose,"updateFromAircraft","(Lmcheli/aircraft/MCH_EntityAircraft;Lmcheli/render/MCH_RenderFarVehicle$RenderDefinition;JJIF)V"));
        assertNotNull(find(pose,"update","(Lmcheli/MCH_EntityInfo;JI)V"));
        ClassNode tracker=read(Paths.get(root,"mcheli/MCH_EntityInfoClientTracker.class"));
        for(String name:new String[]{"updateEntities","shouldSuppressAircraftRender","cleanupExpired","requestMissingAircraftResync"}) assertClock(tracker,name);
        assertClock(read(Paths.get(root,"mcheli/MCH_EntityInfo.class")),"<init>");
    }
    @Test public void renderHookOverridesUnstableNativePoseAndReleasesItAfterExportWithoutChangingEntities() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_JITTER_RECORDING"); assumeNotNull(file);
        ReplayModReplay previous=ReplayModReplay.instance;
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file);
                java.io.InputStream input=zip.getInputStream(zip.getEntry("recording.tmcpr"))) {
            RecordedAircraftFrames frames=RecordedAircraftFrames.read(input);
            set(null,ReplayAircraftExport.class,"frames",frames);
            ReplayModReplay module=allocate(ReplayModReplay.class);
            ReplayHandler replay=allocate(ReplayHandler.class); Sender sender=allocate(Sender.class);
            set(replay,ReplayHandler.class,"fullReplaySender",sender);
            set(module,ReplayModReplay.class,"replayHandler",replay); ReplayModReplay.instance=module;
            ExportPose pose=allocate(ExportPose.class);
            java.util.UUID uuid=java.util.UUID.fromString("e7caf969-ef69-12a3-cfb3-e67c2d7eb8d4");
            set(pose,MixinMCHExportPose_MCHeli.class,"aircraftUuid",uuid);
            set(pose,MixinMCHExportPose_MCHeli.class,"entityClassName","mcheli.plane.MCP_EntityPlane");
            set(pose,MixinMCHExportPose_MCHeli.class,"entityName","f16");
            Method hook=MixinMCHExportPose_MCHeli.class.getDeclaredMethod("replayMod$sampleExportFrame",org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
            hook.setAccessible(true);
            Field yaw=MixinMCHExportPose_MCHeli.class.getDeclaredField("yaw"); yaw.setAccessible(true);
            Field y=MixinMCHExportPose_MCHeli.class.getDeclaredField("y"); y.setAccessible(true);
            for (int frame=0;frame<900;frame++) {
                sender.time=30955+frame*1000/60;
                // Reproduce arbitrary native-tick/warmup attitude, including huge reversals.
                yaw.setFloat(pose,frame%3==0 ? -170 : 90); y.setDouble(pose,60);
                hook.invoke(pose,new Object[]{null});
                RecordedAircraftFrames.Pose expected=frames.at(uuid,"mcheli.plane.MCP_EntityPlane","f16",sender.time);
                assertEquals(expected.yaw,yaw.getFloat(pose),0); assertEquals(expected.y,y.getDouble(pose),0);
            }
            ReplayAircraftExport.end(); yaw.setFloat(pose,123); hook.invoke(pose,new Object[]{null});
            assertEquals("Native rendering resumes immediately after export",123,yaw.getFloat(pose),0);
            set(null,ReplayAircraftExport.class,"frames",frames); ReplayModReplay.instance=null;
            hook.invoke(pose,new Object[]{null}); assertEquals("Live play is never overridden",123,yaw.getFloat(pose),0);
        } finally { ReplayAircraftExport.end(); ReplayModReplay.instance=previous; }
    }
    @Test public void installedMchrAttitudeDoesNotJumpWithSlowExportFramesOrMoveWhileFrozen() throws Exception {
        String root=System.getenv("REPLAYMOD_TEST_MCHR"); assumeNotNull(root);
        ReplayModReplay previous=ReplayModReplay.instance;
        try(java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{Paths.get(root).toUri().toURL()},getClass().getClassLoader())) {
            ReplayModReplay module=allocate(ReplayModReplay.class);
            ReplayHandler replay=allocate(ReplayHandler.class); Sender sender=allocate(Sender.class);
            set(replay,ReplayHandler.class,"fullReplaySender",sender);
            set(module,ReplayModReplay.class,"replayHandler",replay); ReplayModReplay.instance=module;
            Class<?> sampleType=loader.loadClass("mcheli.MCH_EntityInfo");
            Class<?> rotationType=loader.loadClass("mcheli.render.MCH_RenderFarVehicle$SnapshotRotation");
            Method update=rotationType.getDeclaredMethod("update",sampleType,long.class); update.setAccessible(true);
            Field yaw=rotationType.getDeclaredField("yaw"); yaw.setAccessible(true);
            Object smooth=allocate(rotationType),legacy=allocate(rotationType),sample=null;
            int bucket=-1; float last=0,oldLast=0,maxJump=0,oldMaxJump=0;
            Method hook=MixinMCHFarVehicle_MCHeli.class.getDeclaredMethod("replayMod$renderClock"); hook.setAccessible(true);
            FarRenderer renderer=allocate(FarRenderer.class);
            for(int frame=0;frame<=60;frame++) {
                sender.time=1000+frame*1000/60;
                int nextBucket=(sender.time-1000)/100;
                if(nextBucket!=bucket) {
                    bucket=nextBucket; sample=allocate(sampleType);
                    sampleType.getField("rotationYaw").setFloat(sample,bucket*20);
                    sampleType.getField("lastUpdateTime").setLong(sample,1000+bucket*100);
                }
                update.invoke(smooth,sample,(long)hook.invoke(renderer));
                // A frame taking 300ms to export used to finish a 100ms blend.
                update.invoke(legacy,sample,frame*300000000L);
                float angle=yaw.getFloat(smooth),oldAngle=yaw.getFloat(legacy);
                maxJump=Math.max(maxJump,Math.abs(angle-last)); oldMaxJump=Math.max(oldMaxJump,Math.abs(oldAngle-oldLast));
                last=angle; oldLast=oldAngle;
            }
            assertTrue("Legacy wall-clock path reproduces >=20 degree frame jumps",oldMaxJump>=20);
            assertTrue("60fps playback must not inherit export-time attitude jumps: "+maxJump,maxJump<4);
            sender.time+=33; update.invoke(smooth,sample,(long)hook.invoke(renderer));
            float frozen=yaw.getFloat(smooth);
            for(int frame=0;frame<20;frame++) update.invoke(smooth,sample,(long)hook.invoke(renderer));
            assertEquals("Frozen action and repeated capture passes hold their attitude",frozen,yaw.getFloat(smooth),0);
        } finally { ReplayModReplay.instance=previous; }
    }
    private static boolean hasCall(ClassNode type,String method,String owner,String name) {
        for(MethodNode m:type.methods) if(m.name.equals(method)) for(AbstractInsnNode insn:m.instructions.toArray()) if(insn instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)insn; if(call.owner.equals(owner)&&call.name.equals(name)) return true;
        }
        return false;
    }
    private static ClassNode read(Path path) throws IOException { ClassNode node=new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(node,0); return node; }
    private static MethodNode find(ClassNode node,String name,String desc) {
        for(MethodNode method:node.methods) if(method.name.equals(name)&&method.desc.equals(desc)) return method;
        return null;
    }
    private static void assertClock(ClassNode node,String name) {
        int clocks=0;
        for(MethodNode method:node.methods) if(method.name.equals(name)) for(AbstractInsnNode insn:method.instructions.toArray()) {
            if(insn instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)insn;
                if(call.owner.equals("java/lang/System")&&call.name.equals("currentTimeMillis")) clocks++;
            }
        }
        assertTrue(node.name+"."+name+" must expose the expected clock hook",clocks>0);
    }
    private static class FarRenderer extends MixinMCHFarVehicle_MCHeli { }
    private static class ExportPose extends MixinMCHExportPose_MCHeli { }
    private static class Sender extends FullReplaySender {
        int time;
        Sender() throws IOException { super(null,null,false); }
        @Override public int currentTimeStamp() { return time; }
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field=Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
    private static void set(Object object,Class<?> owner,String name,Object value) throws Exception { Field field=owner.getDeclaredField(name); field.setAccessible(true); field.set(object,value); }
}
