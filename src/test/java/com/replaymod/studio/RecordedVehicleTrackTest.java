package com.replaymod.studio;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class RecordedVehicleTrackTest {
    @Test public void readsPitchAndRollAndInterpolatesAcrossAngleWrap() throws Exception {
        RecordedVehicleTrack track=read(snapshot(1000,42,0,170,20,179,false),snapshot(1100,42,10,-170,40,-179,false));
        assertEquals(20,track.at(1000).pitch,0); assertEquals(179,track.at(1000).roll,0);
        assertEquals(30,track.at(1050).pitch,0); assertEquals(180,track.at(1050).roll,0);
        assertEquals(40,track.at(1100).pitch,0); assertEquals(-179,track.at(1100).roll,0);
        RecordedVehicleTrack invalid=read(snapshot(1000,42,0,0,false),snapshot(1100,42,1,0,Float.NaN,0,false));
        rejected(() -> invalid.at(1100));
    }
    @Test public void changingSnapshotVelocityAndHeadingHaveContinuousTangentsWithoutOvershoot() throws Exception {
        RecordedVehicleTrack track=read(snapshot(1000,42,0,170,false),snapshot(1100,42,10,-175,false),snapshot(1200,42,11,-170,false));
        double before=track.at(1100).x-track.at(1099).x,after=track.at(1101).x-track.at(1100).x;
        assertEquals(before,after,.003);
        assertTrue("Old linear snapshots jump from .1 to .01 blocks/ms",Math.abs(before-after)<.01);
        for (int time=1100;time<=1200;time++) assertTrue(track.at(time).x>=10 && track.at(time).x<=11);
        float yawBefore=AutomatedShot.wrap(track.at(1100).yaw-track.at(1099).yaw),yawAfter=AutomatedShot.wrap(track.at(1101).yaw-track.at(1100).yaw);
        assertEquals(yawBefore,yawAfter,.003);
    }
    @Test public void samplesRecordedVehicleMotionAndWrapsHeadingAcrossNorth() throws Exception {
        RecordedVehicleTrack track=read(snapshot(1000,42,0,179,false),snapshot(1100,42,10,-179,false));
        assertEquals(2,track.size()); AutomatedShot.Pose middle=track.at(1050);
        assertEquals(5,middle.x,0); assertEquals(180,middle.yaw,0);
        assertEquals(0,track.at(1000).x,0); assertEquals(10,track.at(1100).x,0);
    }
    @Test public void absentAndDestroyedVehiclesCannotSilentlyProduceAFrozenFollowShot() throws Exception {
        RecordedVehicleTrack missing=read(snapshot(1000,42,0,0,false),snapshot(1100,99,1,0,false),snapshot(1200,42,2,0,false));
        rejected(() -> missing.at(1150)); rejected(() -> missing.validateRange(1000,1200));
        RecordedVehicleTrack dead=read(snapshot(1000,42,0,0,false),snapshot(1100,42,1,0,true));
        rejected(() -> dead.at(1100));
    }
    @Test public void aGapBetweenSparseGeneratedSamplesIsStillRejected() throws Exception {
        RecordedVehicleTrack track=read(snapshot(1000,42,0,0,false),snapshot(1100,42,1,0,false),snapshot(3000,42,3,0,false),snapshot(3100,42,4,0,false));
        rejected(() -> track.validateRange(1000,3100)); rejected(() -> track.at(2000));
    }
    @Test public void skipsNonPayloadPacketsAndRejectsTruncatedFraming() throws Exception {
        byte[] ignored=frame(0,new byte[]{0x21,1,2,3,4});
        RecordedVehicleTrack track=read(ignored,snapshot(1000,42,0,0,false)); assertEquals(1,track.size());
        try { read(new byte[]{1,2,3}); fail(); } catch(EOFException expected) { }
    }
    @Test public void missingSelectedVehicleReturnsAnActionableFailure() throws Exception {
        try { read(snapshot(1000,77,0,0,false)); fail(); } catch(IOException error) { assertTrue(error.getMessage().contains("42")); }
    }
    @Test public void cancellationStopsTheBackgroundScan() throws Exception {
        Thread.currentThread().interrupt();
        try { read(snapshot(1000,42,0,0,false)); fail(); } catch(InterruptedIOException expected) { }
        finally { Thread.interrupted(); }
    }
    @Test public void realPlaneTestF16CanGenerateAnOrbitFollowAndFlyByFromRecordedSnapshots() throws Exception {
        String path=System.getenv("REPLAYMOD_TEST_PLANE_RECORDING"); assumeNotNull(path);
        RecordedVehicleTrack track;
        try(ZipFile zip=new ZipFile(path); InputStream input=zip.getInputStream(zip.getEntry("recording.tmcpr"))) { track=RecordedVehicleTrack.read(input,179535); }
        assertTrue(track.size()>300); assertEquals(-90,track.at(16021).yaw,.01);
        for(AutomatedShot.Kind kind:AutomatedShot.Kind.values()) {
            AutomatedShot.Settings settings=new AutomatedShot.Settings(); settings.kind=kind; settings.replayIn=24000; settings.replayOut=30000;
            if(kind==AutomatedShot.Kind.FOLLOW) settings.angleChange=0;
            if(kind==AutomatedShot.Kind.FLY_BY) { settings.angleStart=-50; settings.angleChange=50; }
            AutomatedShot.Plan plan=AutomatedShot.generate(settings,track,53000);
            assertEquals(361,plan.frames.size());
            for(AutomatedShot.Frame frame:plan.frames) assertEquals(track.at(frame.replay).x,frame.target.x,0);
        }
    }
    private static void rejected(Runnable action) { try { action.run(); fail(); } catch(IllegalArgumentException expected) { } }
    private static RecordedVehicleTrack read(byte[]... frames) throws IOException { ByteArrayOutputStream stream=new ByteArrayOutputStream(); for(byte[] frame:frames) stream.write(frame); return RecordedVehicleTrack.read(new ByteArrayInputStream(stream.toByteArray()),42); }
    private static byte[] snapshot(int time,int entity,double x,float yaw,boolean destroyed) throws IOException {
        return snapshot(time,entity,x,yaw,0,0,destroyed);
    }
    private static byte[] snapshot(int time,int entity,double x,float yaw,float pitch,float roll,boolean destroyed) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream data=new DataOutputStream(bytes);
        data.writeByte(7); data.writeLong(time); data.writeInt(1); data.writeInt(entity);
        text(data,"world"); text(data,"f16"); text(data,"mcheli.plane.MCP_EntityPlane");
        data.writeDouble(x); data.writeDouble(80); data.writeDouble(20);
        data.writeDouble(x); data.writeDouble(80); data.writeDouble(20);
        data.writeFloat(yaw); data.writeFloat(pitch); data.writeByte(0); data.writeLong(0);
        data.writeFloat(roll); data.writeBoolean(destroyed); data.writeFloat(0); data.writeFloat(0);
        ByteArrayOutputStream packetBytes=new ByteArrayOutputStream(); DataOutputStream packet=new DataOutputStream(packetBytes);
        packet.writeByte(0x3F); text(packet,"MCHReforged"); packet.writeShort(bytes.size()); packet.write(bytes.toByteArray());
        return frame(time,packetBytes.toByteArray());
    }
    private static byte[] frame(int time,byte[] packet) throws IOException { ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream data=new DataOutputStream(bytes); data.writeInt(time); data.writeInt(packet.length); data.write(packet); return bytes.toByteArray(); }
    private static void text(DataOutputStream stream,String text) throws IOException { byte[] bytes=text.getBytes(StandardCharsets.UTF_8); int n=bytes.length; do { int value=n&127; n>>>=7; stream.writeByte(n==0?value:value|128); } while(n!=0); stream.write(bytes); }
}
