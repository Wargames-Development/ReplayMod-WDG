package com.replaymod.studio;

import com.replaymod.pathing.properties.CameraProperties;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.InterpolatorType;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Test;
import static org.junit.Assert.*;

public class MountedCameraTest {
    private static AutomatedShot.Settings settings() {
        AutomatedShot.Settings s=new AutomatedShot.Settings(); s.kind=AutomatedShot.Kind.MOUNTED;
        s.replayOut=6000; s.distanceStart=s.distanceEnd=8; s.heightStart=s.heightEnd=1;
        s.angleStart=s.angleChange=0; return s;
    }
    @Test public void wingOffsetBanksAndPitchesWithTheAircraft() {
        AutomatedShot.Settings s=settings();
        AutomatedShot.Frame f=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(10,80,20,90,30,90),6000).frames.get(0);
        // Local (8,1,0) becomes (-1,8,0) after bank, then (-1,4sqrt(3),4) after pitch.
        assertEquals(6,f.x,1e-7); assertEquals(80+4*Math.sqrt(3),f.y,1e-7); assertEquals(19,f.z,1e-7);
        assertEquals(90,f.yaw,1e-5); assertEquals(30,f.pitch,1e-5); assertEquals(90,f.roll,1e-5);
    }
    @Test public void viewOffsetsAreComposedInTheAircraftFrame() {
        AutomatedShot.Settings s=settings(); s.viewYaw=90;
        AutomatedShot.Frame f=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(0,0,0,0,0,90),6000).frames.get(0);
        // A sideways view from a 90-degree bank looks down, not along world X.
        assertEquals(90,f.pitch,1e-5);
        assertVector(new double[]{0,-1,0},MountedCamera.rotate(f.yaw,f.pitch,f.roll,0,0,1));
        s.viewYaw=137; s.viewPitch=-23; s.rollStart=s.rollEnd=17;
        f=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(0,0,0,47,31,-62),6000).frames.get(0);
        for(double[] axis:new double[][]{{0,0,1},{0,1,0}}) {
            double[] local=MountedCamera.rotate(137,-23,17,axis[0],axis[1],axis[2]);
            assertVector(MountedCamera.rotate(47,31,-62,local[0],local[1],local[2]),MountedCamera.rotate(f.yaw,f.pitch,f.roll,axis[0],axis[1],axis[2]));
        }
    }
    @Test public void everyViewAxisCanBeDisabledWithoutChangingTheAttachmentPosition() {
        AutomatedShot.Settings s=settings(); AutomatedShot.Target target=t -> new AutomatedShot.Pose(0,80,0,40,25,60);
        AutomatedShot.Frame original=AutomatedShot.generate(s,target,6000).frames.get(0);
        s.followYaw=false;
        AutomatedShot.Frame yaw=AutomatedShot.generate(s,target,6000).frames.get(0); assertEquals(0,yaw.yaw,1e-5);
        s.followYaw=true; s.followPitch=false;
        AutomatedShot.Frame pitch=AutomatedShot.generate(s,target,6000).frames.get(0); assertEquals(0,pitch.pitch,1e-5);
        s.followPitch=true; s.followRoll=false;
        AutomatedShot.Frame roll=AutomatedShot.generate(s,target,6000).frames.get(0); assertEquals(0,roll.roll,1e-5);
        for(AutomatedShot.Frame f:new AutomatedShot.Frame[]{yaw,pitch,roll}) {
            assertEquals(original.x,f.x,0); assertEquals(original.y,f.y,0); assertEquals(original.z,f.z,0);
        }
    }
    @Test public void fullLoopsAndBarrelRollsHaveContinuousEulerAnglesAndCorrectOrientation() {
        for(boolean loop:new boolean[]{true,false}) {
            AutomatedShot.Settings s=settings();
            AutomatedShot.Plan plan=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(0,80,0,37,loop?t*.06f:0,loop?0:t*.06f),6000);
            AutomatedShot.Frame previous=null;
            for(AutomatedShot.Frame f:plan.frames) {
                if(previous!=null) {
                    assertTrue(Math.abs(f.yaw-previous.yaw)<3); assertTrue(Math.abs(f.pitch-previous.pitch)<3); assertTrue(Math.abs(f.roll-previous.roll)<3);
                }
                for(double[] axis:new double[][]{{0,0,1},{0,1,0}})
                    assertVector(MountedCamera.rotate(f.target.yaw,f.target.pitch,f.target.roll,axis[0],axis[1],axis[2]),MountedCamera.rotate(f.yaw,f.pitch,f.roll,axis[0],axis[1],axis[2]));
                previous=f;
            }
        }
    }
    @Test public void localRefinementsDragInBothViewsEvenWhenBankedAndWorldAnchored() {
        for(AutomatedShot.Space space:AutomatedShot.Space.values()) for(ShotDiagram.View view:ShotDiagram.View.values()) {
            AutomatedShot.Settings s=settings(); s.space=space;
            AutomatedShot.Target target=t -> new AutomatedShot.Pose(t/100.0,80,20,t/100f,30,70);
            AutomatedShot.Plan before=AutomatedShot.generate(s,target,6000);
            ShotDiagram d=new ShotDiagram(before,view,0,0,600,400);
            AutomatedShot.Frame handle=before.handles.get(2);
            s.curve=d.drag(s.curve,2,handle,space,20,-13);
            AutomatedShot.Frame moved=AutomatedShot.generate(s,target,6000).handles.get(2);
            assertEquals(20,d.x(moved)-d.x(handle),1e-7); assertEquals(-13,d.y(moved)-d.y(handle),1e-7);
        }
    }
    @Test public void zeroAndNegativeOffsetsAndFrozenActionCanBeSavedAndReplayed() throws Exception {
        AutomatedShot.Settings s=settings(); s.distanceStart=0; s.distanceEnd=-8; s.replayOut=0;
        AutomatedShot.Plan plan=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(0,80,0,20,30,45),6000);
        SPTimeline timeline=new SPTimeline(); timeline.setDefaultInterpolatorType(InterpolatorType.CATMULL_ROM);
        AutomatedShot.place(timeline,plan);
        com.replaymod.replaystudio.pathing.serialize.TimelineSerialization serializer=new com.replaymod.replaystudio.pathing.serialize.TimelineSerialization(new SPTimeline(),null);
        SPTimeline loaded=new SPTimeline(serializer.deserialize(serializer.serialize(java.util.Collections.singletonMap("Wing",timeline.getTimeline()))).get("Wing"));
        loaded.getPositionPath().updateAll();
        for(AutomatedShot.Frame f:plan.handles) {
            Triple<Float,Float,Float> rotation=loaded.getPositionPath().getValue(CameraProperties.ROTATION,f.time).get();
            assertVector(MountedCamera.rotate(f.yaw,f.pitch,f.roll,0,1,0),MountedCamera.rotate(rotation.getLeft(),rotation.getMiddle(),rotation.getRight(),0,1,0));
            Triple<Double,Double,Double> p=loaded.getPositionPath().getValue(CameraProperties.POSITION,f.time).get();
            assertEquals(f.x,p.getLeft(),1e-6); assertEquals(f.y,p.getMiddle(),1e-6); assertEquals(f.z,p.getRight(),1e-6);
        }
        assertEquals(0,plan.speed(),0);
    }
    private static void assertVector(double[] expected,double[] actual) { assertArrayEquals(expected,actual,1e-5); }
}
