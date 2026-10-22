package com.replaymod.replay;

import com.replaymod.replay.compat.RecordedAircraftFrames;
import com.replaymod.replay.compat.RecordedAircraftFrames.Pose;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class RecordedAircraftFramesTest {
    private static final UUID PLANE=new UUID(1,2), REUSED_ID=new UUID(3,4);
    private static final String TYPE="mcheli.plane.MCP_EntityPlane";
    private static Pose at(RecordedAircraftFrames frames,int time) { return frames.at(PLANE,TYPE,"f16",time); }

    @Test public void recordedLightSurvivesPoseInterpolationWithoutBrighteningRealDarkness() throws Exception {
        for (int light:new int[]{0,0x00f00000,0x00a00030,-1}) {
            RecordedAircraftFrames frames=read(sample(1000,PLANE,0,0,0,0,false,100,light),
                sample(1100,PLANE,10,0,0,0,false,102,light));
            for (int time:new int[]{1000,1050,1100}) assertEquals(light,at(frames,time).packedLight);
        }
        RecordedAircraftFrames frames=read(sample(1000,PLANE,0,0,0,0,false,100,0),
            sample(1100,PLANE,10,0,0,0,false,102,0x00f00000));
        assertEquals("Do not invent light between discrete recorded light samples",0,at(frames,1050).packedLight);
        assertEquals(0x00f00000,at(frames,1100).packedLight);
    }

    @Test public void burstDeliveryDoesNotChangeSteadySpeedOrSeparateTheTrackingCameraFromTheBody() throws Exception {
        for (int late:new int[]{1251,1300}) {
            int[] arrivals={1000,1100,late,1300,1400,1500,1600};
            byte[][] packets=new byte[arrivals.length][];
            ByteArrayOutputStream stream=new ByteArrayOutputStream();
            for (int i=0;i<arrivals.length;i++) {
                packets[i]=sample(arrivals[i],PLANE,i*10,0,0,0,false,100+i*2);
                stream.write(packets[i]);
            }
            RecordedAircraftFrames frames=read(packets);
            com.replaymod.studio.RecordedVehicleTrack camera=com.replaymod.studio.RecordedVehicleTrack.read(
                new ByteArrayInputStream(stream.toByteArray()),42);
            for (int time=1000;time<=1600;time++) {
                Pose body=at(frames,time); assertNotNull(body);
                assertEquals("Arrival jitter must not accelerate a steady aircraft",(time-1000)/10.0,body.x,1e-9);
                assertEquals("Camera and body must share corrected times",body.x,camera.at(time).x,1e-9);
            }
        }
    }

    @Test public void framesUseRecordedTimeRegardlessOfTickCountRenderCostFreezeOrSeekOrder() throws Exception {
        RecordedAircraftFrames frames=read(sample(1000,PLANE,0,0,0,0,false), sample(1100,PLANE,10,20,0,0,false));
        Pose middle=at(frames,1050); assertEquals(5,middle.x,0); assertEquals(10,middle.yaw,.0001);
        for (int time:new int[]{1100,1000,1099,1050,1050,1050}) {
            Pose p=at(frames,time); assertEquals((time-1000)/10.0,p.x,.0001);
            assertEquals((time-1000)/5.0,p.yaw,.0001);
        }
        assertEquals(middle.yaw,at(frames,1050).yaw,0);
    }
    @Test public void headingAndRollCrossWrapBoundariesWithoutTakingTheLongWay() throws Exception {
        Pose p=at(read(sample(1000,PLANE,0,179,0,0,false),sample(1100,PLANE,0,-179,0,0,false)),1050);
        assertEquals(180,Math.abs(p.yaw),.0001); assertEquals(0,p.pitch,0);
        p=at(read(sample(1000,PLANE,0,0,0,179,false),sample(1100,PLANE,0,0,0,-179,false)),1050);
        assertEquals(180,Math.abs(p.roll),.0001);
    }
    @Test public void positionTangentsAreContinuousAtUnevenSnapshotIntervalsWithoutOvershooting() throws Exception {
        RecordedAircraftFrames frames=read(sample(1000,PLANE,0,0,0,0,false),sample(1150,PLANE,10,0,0,0,false),
                sample(1200,PLANE,20,0,0,0,false),sample(1300,PLANE,22,0,0,0,false));
        for (int knot:new int[]{1150,1200}) {
            double before=at(frames,knot).x-at(frames,knot-1).x;
            double after=at(frames,knot+1).x-at(frames,knot).x;
            assertEquals("Aircraft velocity must not jump on snapshot arrival",before,after,.015);
        }
        for (int time=1000;time<=1300;time++) {
            double minimum=time<=1150?0:time<=1200?10:20,maximum=time<=1150?10:time<=1200?20:22;
            assertTrue(at(frames,time).x>=minimum && at(frames,time).x<=maximum);
        }
        assertEquals(10,at(frames,1150).x,0); assertEquals(20,at(frames,1200).x,0);
    }
    @Test public void invalidNeighbourDoesNotPullAValidSegmentAcrossATeleportOrDestroyedAircraft() throws Exception {
        RecordedAircraftFrames baseline=read(sample(1100,PLANE,10,0,0,0,false),sample(1200,PLANE,20,0,0,0,false));
        for (boolean destroyed:new boolean[]{false,true}) {
            RecordedAircraftFrames frames=read(sample(1000,PLANE,200,0,0,0,destroyed),
                    sample(1100,PLANE,10,0,0,0,false),sample(1200,PLANE,20,0,0,0,false),
                    sample(1300,PLANE,-200,0,0,0,destroyed));
            for (int time=1100;time<=1200;time++) assertEquals(at(baseline,time).x,at(frames,time).x,0);
        }
    }
    @Test public void actualSixSecondOrbitAndAircraftFollowTheSamePositionCurveAtEveryExportFrame() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_ORBIT_RECORDING"); assumeNotNull(file);
        RecordedAircraftFrames frames; com.replaymod.studio.RecordedVehicleTrack camera;
        try (ZipFile zip=new ZipFile(file); InputStream input=zip.getInputStream(zip.getEntry("recording.tmcpr"));
                InputStream target=zip.getInputStream(zip.getEntry("recording.tmcpr"))) {
            frames=RecordedAircraftFrames.read(input);
            camera=com.replaymod.studio.RecordedVehicleTrack.read(target,2509);
        }
        UUID uuid=UUID.fromString("09128f04-6b58-3afc-cbda-9d7b39ff94fa");
        com.replaymod.replaystudio.pathing.path.Timeline saved=null;
        String path=System.getenv("REPLAYMOD_TEST_ORBIT_TIMELINE");
        if (path!=null) {
            String json=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),StandardCharsets.UTF_8);
            saved=new com.replaymod.replaystudio.pathing.serialize.TimelineSerialization(new com.replaymod.simplepathing.SPTimeline(),null)
                    .deserialize(json).get("");
            // Rebuild this exact orbit at the new default detail. Keep the old
            // timeline as a fixture; existing user camera paths are never edited.
            com.replaymod.studio.AutomatedShot.Settings settings=new com.replaymod.studio.AutomatedShot.Settings();
            settings.replayIn=33273; settings.replayOut=39273;
            settings.angleStart=0; settings.angleChange=360;
            com.replaymod.simplepathing.SPTimeline dense=new com.replaymod.simplepathing.SPTimeline();
            dense.setDefaultInterpolatorType(com.replaymod.simplepathing.InterpolatorType.CATMULL_ROM);
            com.replaymod.studio.AutomatedShot.place(dense,com.replaymod.studio.AutomatedShot.generate(settings,camera,54199));
            saved=dense.getTimeline();
            for(com.replaymod.replaystudio.pathing.path.Path track:saved.getPaths()) track.updateAll();
            String output=System.getenv("REPLAYMOD_TEST_ORBIT_OUTPUT");
            if(output!=null) java.nio.file.Files.write(java.nio.file.Paths.get(output),
                    new com.replaymod.replaystudio.pathing.serialize.TimelineSerialization(new com.replaymod.simplepathing.SPTimeline(),null)
                            .serialize(java.util.Collections.singletonMap("",saved)).getBytes(StandardCharsets.UTF_8));
        }
        double maximumFramingError=0;
        for (int frame=0;frame<360;frame++) {
            int time=33273+frame*1000/60;
            Pose body=frames.at(uuid,TYPE,"f16",time); assertNotNull("Frame "+frame,body);
            com.replaymod.studio.AutomatedShot.Pose target=camera.at(time);
            assertEquals("Frame "+frame,target.x,body.x,1e-9);
            assertEquals("Frame "+frame,target.y,body.y,1e-9);
            assertEquals("Frame "+frame,target.z,body.z,1e-9);
            if(saved!=null) {
                org.apache.commons.lang3.tuple.Triple<Double,Double,Double> position=saved.getPaths().get(1)
                        .getValue(com.replaymod.pathing.properties.CameraProperties.POSITION,frame*1000/60).get();
                org.apache.commons.lang3.tuple.Triple<Float,Float,Float> rotation=saved.getPaths().get(1)
                        .getValue(com.replaymod.pathing.properties.CameraProperties.ROTATION,frame*1000/60).get();
                double angle=Math.toRadians(rotation.getLeft());
                double dx=body.x-(position.getLeft()-Math.sin(angle)*24),
                        dy=body.y-(position.getMiddle()-10),dz=body.z-(position.getRight()+Math.cos(angle)*24);
                maximumFramingError=Math.max(maximumFramingError,Math.sqrt(dx*dx+dy*dy+dz*dz));
            }
        }
        if(saved!=null) {
            System.out.println("Actual serialized orbit: maximum aircraft/framing error="+maximumFramingError+" blocks");
            assertTrue("Saved camera spline must keep the aircraft framed",maximumFramingError<.03);
        }
    }
    @Test public void actualSteadyPlaneKeepsItsSpeedThroughAnIsolatedLatePacket() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_ORBIT_RECORDING"); assumeNotNull(file);
        com.replaymod.studio.RecordedVehicleTrack track;
        try (ZipFile zip=new ZipFile(file); InputStream input=zip.getInputStream(zip.getEntry("recording.tmcpr"))) {
            track=com.replaymod.studio.RecordedVehicleTrack.read(input,2257);
        }
        double minimum=Double.POSITIVE_INFINITY,maximum=0,squared=0; int count=0;
        // This interval contains the 151/49ms delivery burst. Later sustained
        // clock drift is deliberately preserved, just like a server slowdown.
        for (int frame=1;frame<=36;frame++) {
            int before=33350+(frame-1)*1000/60,time=33350+frame*1000/60;
            com.replaymod.studio.AutomatedShot.Pose a=track.at(before),b=track.at(time);
            double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
            double speed=Math.sqrt(dx*dx+dy*dy+dz*dz)*1000/(time-before);
            minimum=Math.min(minimum,speed); maximum=Math.max(maximum,speed);
            squared+=(speed-40.5)*(speed-40.5); count++;
        }
        System.out.println("Actual steady plane: speed="+minimum+".."+maximum+" blocks/s; RMS error="+Math.sqrt(squared/count));
        assertTrue("An isolated late packet must not produce a speed pulse",maximum-minimum<2);
    }
    @Test public void verticalEulerInversionPreservesTheAircraftAttitude() throws Exception {
        RecordedAircraftFrames frames=read(sample(1000,PLANE,0,10,89,20,false),sample(1100,PLANE,0,-170,89,-160,false));
        Pose p=at(frames,1050);
        double[] expected=matrix(10,90,20), actual=matrix(p.yaw,p.pitch,p.roll);
        for (int i=0;i<9;i++) assertEquals(expected[i],actual[i],.00001);
        double[] previous=matrix(10,89,20);
        for (int time=1001;time<=1100;time++) {
            p=at(frames,time); actual=matrix(p.yaw,p.pitch,p.roll);
            assertTrue("Euler inversion must not cause a spin",angle(previous,actual)<.03);
            previous=actual;
        }
    }
    @Test public void gapsDestructionIdentityReuseAndOtherVehicleTypesKeepNativeBehaviour() throws Exception {
        RecordedAircraftFrames frames=read(sample(1000,PLANE,0,0,0,0,false),sample(1100,REUSED_ID,1,0,0,0,false),sample(1200,PLANE,2,0,0,0,false));
        assertNull(at(frames,1150)); assertNull(frames.at(REUSED_ID,TYPE,"f16",1000));
        assertNull(frames.at(PLANE,"mcheli.tank.MCH_EntityTank","f16",1000));
        assertNull(frames.at(PLANE,TYPE,"another plane",1000));
        assertNull(at(read(sample(1000,PLANE,0,0,0,0,false),sample(1100,PLANE,1,0,0,0,true)),1050));
        assertNull(at(read(sample(1000,PLANE,0,0,0,0,false),sample(2000,PLANE,1,0,0,0,false)),1500));
        assertNull(at(read(sample(1000,PLANE,0,0,0,0,false),sample(1100,PLANE,100,0,0,0,false)),1050));
        assertNull(at(read(sample(1000,PLANE,0,Float.NaN,0,0,false)),1000));
        assertNull(at(read(sample(1000,PLANE,0,0,0,0,false)),1200));
    }
    @Test public void truncatedOrUnboundedPayloadsFailBeforeRendering() throws Exception {
        for (byte[] data:new byte[][]{new byte[]{0,0,0},new byte[]{0,0,0,0,127,-1,-1,-1}}) {
            try { read(data); fail(); } catch (IOException expected) { }
        }
        Thread.currentThread().interrupt();
        try { read(sample(1000,PLANE,0,0,0,0,false)); fail(); } catch (InterruptedIOException expected) { }
        finally { Thread.interrupted(); }
    }
    @Test public void usersActualBankingF16ExportHasNoArtificialAttitudeSpikesAcrossAll900Frames() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_JITTER_RECORDING"); assumeNotNull(file);
        RecordedAircraftFrames frames;
        try (ZipFile zip=new ZipFile(file); InputStream input=zip.getInputStream(zip.getEntry("recording.tmcpr"))) { frames=RecordedAircraftFrames.read(input); }
        UUID uuid=UUID.fromString("e7caf969-ef69-12a3-cfb3-e67c2d7eb8d4");
        double[] previous=null; double maximum=0;
        for (int i=0;i<900;i++) {
            int time=30955+i*1000/60;
            Pose p=frames.at(uuid,TYPE,"f16",time); assertNotNull("Recorded export frame "+i,p);
            assertTrue(p.y>100); // Must not show the stale runway pose at export startup.
            double[] current=matrix(p.yaw,p.pitch,p.roll);
            if (previous!=null) maximum=Math.max(maximum,angle(previous,current));
            previous=current;
            // Multi-pass/cube captures and seeks must produce precisely the same frame.
            assertEquals(p.roll,frames.at(uuid,TYPE,"f16",time).roll,0);
        }
        assertTrue("Actual legitimate manoeuvre, maximum frame step="+maximum,maximum<4);
        System.out.println("Actual F16 export: 900 poses; maximum rotation step = "+maximum+" degrees at 60fps");
    }
    private static double angle(double[] a,double[] b) {
        double trace=0; for (int i=0;i<9;i++) trace+=a[i]*b[i];
        return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,(trace-1)/2))));
    }
    private static double[] matrix(float yaw,float pitch,float roll) {
        double y=Math.toRadians(-yaw), p=Math.toRadians(pitch), r=Math.toRadians(roll);
        double cy=Math.cos(y),sy=Math.sin(y),cp=Math.cos(p),sp=Math.sin(p),cr=Math.cos(r),sr=Math.sin(r);
        return new double[]{cy*cr+sy*sp*sr,-cy*sr+sy*sp*cr,sy*cp, cp*sr,cp*cr,-sp,
            -sy*cr+cy*sp*sr,sy*sr+cy*sp*cr,cy*cp};
    }
    private static RecordedAircraftFrames read(byte[]... packets) throws IOException {
        ByteArrayOutputStream stream=new ByteArrayOutputStream(); for (byte[] p:packets) stream.write(p);
        return RecordedAircraftFrames.read(new ByteArrayInputStream(stream.toByteArray()));
    }
    private static byte[] sample(int time,UUID uuid,double x,float yaw,float pitch,float roll,boolean dead) throws IOException {
        return sample(time,uuid,x,yaw,pitch,roll,dead,time);
    }
    private static byte[] sample(int time,UUID uuid,double x,float yaw,float pitch,float roll,boolean dead,long sequence) throws IOException {
        return sample(time,uuid,x,yaw,pitch,roll,dead,sequence,0);
    }
    private static byte[] sample(int time,UUID uuid,double x,float yaw,float pitch,float roll,boolean dead,long sequence,int light) throws IOException {
        ByteArrayOutputStream payload=new ByteArrayOutputStream(); DataOutputStream p=new DataOutputStream(payload);
        p.writeByte(7); p.writeLong(sequence); p.writeInt(1); p.writeInt(42); text(p,"world"); text(p,"f16"); text(p,TYPE);
        p.writeDouble(x); p.writeDouble(120); p.writeDouble(20); p.writeDouble(x); p.writeDouble(120); p.writeDouble(20);
        p.writeFloat(yaw); p.writeFloat(pitch); p.writeByte(0); p.writeLong(0); p.writeFloat(roll); p.writeBoolean(dead); p.writeFloat(0); p.writeFloat(0);
        p.writeInt(0x41474c31); p.writeInt(1); p.writeDouble(20);
        p.writeInt(0x5445414d); p.writeInt(1); text(p,"team");
        p.writeInt(0x4c495431); p.writeInt(1); p.writeInt(light);
        p.writeInt(0x56494431); p.writeInt(1); p.writeLong(uuid.getMostSignificantBits()); p.writeLong(uuid.getLeastSignificantBits());
        ByteArrayOutputStream packet=new ByteArrayOutputStream(); DataOutputStream b=new DataOutputStream(packet);
        b.writeByte(0x3f); text(b,"MCHReforged"); b.writeShort(payload.size()); b.write(payload.toByteArray());
        ByteArrayOutputStream framed=new ByteArrayOutputStream(); DataOutputStream f=new DataOutputStream(framed);
        f.writeInt(time); f.writeInt(packet.size()); f.write(packet.toByteArray()); return framed.toByteArray();
    }
    private static void text(DataOutputStream out,String text) throws IOException {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8); out.writeByte(bytes.length); out.write(bytes);
    }
}
