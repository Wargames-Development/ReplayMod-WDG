package com.replaymod.studio;

import com.replaymod.pathing.properties.CameraProperties;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.simplepathing.InterpolatorType;
import com.replaymod.simplepathing.SPTimeline;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutomatedShotTest {
    private static AutomatedShot.Settings settings(AutomatedShot.Kind kind) {
        AutomatedShot.Settings s=new AutomatedShot.Settings(); s.kind=kind; s.replayIn=10000; s.replayOut=16000;
        s.samplesPerSecond=20; return s; // Retain coverage of older, coarser generated paths.
    }
    @Test public void defaultCameraDetailSamplesShortTrailerShotsAtSixtyHz() {
        AutomatedShot.Settings s=new AutomatedShot.Settings(); s.replayIn=10000; s.replayOut=16000;
        AutomatedShot.Plan plan=AutomatedShot.generate(s,FIXED,60000);
        assertEquals(361,plan.frames.size());
        for(int i=1;i<plan.frames.size();i++) assertTrue(plan.frames.get(i).time-plan.frames.get(i-1).time<=17);
    }
    private static final AutomatedShot.Target FIXED=t -> new AutomatedShot.Pose(10,80,20,0);
    @Test public void worldPathsStayAnchoredWhileVehiclePathsTranslateAndTurnForEveryShotKind() {
        AutomatedShot.Target moving=t -> new AutomatedShot.Pose((t-10000)/100.0,80+(t-10000)/200.0,20,90+(t-10000)/100f);
        AutomatedShot.Target frozen=t -> moving.at(10000);
        for(AutomatedShot.Kind kind:AutomatedShot.Kind.values()) {
            AutomatedShot.Settings s=settings(kind); s.curve=s.curve.move(2,3,4,-5);
            s.space=AutomatedShot.Space.WORLD;
            AutomatedShot.Plan world=AutomatedShot.generate(s,moving,60000),stationary=AutomatedShot.generate(s,frozen,60000);
            for(int i=0;i<world.frames.size();i++) {
                AutomatedShot.Frame a=world.frames.get(i),b=stationary.frames.get(i);
                assertEquals(b.x,a.x,0); assertEquals(b.y,a.y,0); assertEquals(b.z,a.z,0); assertLooksAtTarget(a,2);
            }
            s.space=AutomatedShot.Space.VEHICLE;
            AutomatedShot.Plan vehicle=AutomatedShot.generate(s,moving,60000);
            // Undo the target's translation and heading: the local camera path is identical.
            AutomatedShot.Target zeroHeading=t -> new AutomatedShot.Pose(0,0,0,0);
            AutomatedShot.Plan local=AutomatedShot.generate(s,zeroHeading,60000);
            for(int i=0;i<vehicle.frames.size();i++) {
                AutomatedShot.Frame a=vehicle.frames.get(i),b=local.frames.get(i);
                double yaw=Math.toRadians(a.target.yaw),dx=a.x-a.target.x,dz=a.z-a.target.z;
                assertEquals(b.x,Math.cos(yaw)*dx+Math.sin(yaw)*dz,1e-8);
                assertEquals(b.z,-Math.sin(yaw)*dx+Math.cos(yaw)*dz,1e-8);
                assertEquals(b.y,a.y-a.target.y,1e-8); assertLooksAtTarget(a,2);
            }
        }
    }
    @Test public void refinedPointsAreExactSavedKeysAndRetainReplayTimingAfterReopening() throws Exception {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.space=AutomatedShot.Space.WORLD;
        s.curve=s.curve.move(2,9,7,-5).addAfter(1).move(2,-6,12,3);
        AutomatedShot.Plan plan=AutomatedShot.generate(s,FIXED,60000);
        SPTimeline timeline=timeline(); AutomatedShot.place(timeline,plan);
        com.replaymod.replaystudio.pathing.serialize.TimelineSerialization serializer=new com.replaymod.replaystudio.pathing.serialize.TimelineSerialization(new SPTimeline(),null);
        SPTimeline loaded=new SPTimeline(serializer.deserialize(serializer.serialize(java.util.Collections.singletonMap("Refined",timeline.getTimeline()))).get("Refined"));
        for(AutomatedShot.Frame handle:plan.handles) {
            assertSame(handle,plan.frames.stream().filter(f -> f.time==handle.time).findFirst().get());
            org.apache.commons.lang3.tuple.Triple<Double,Double,Double> position=loaded.getPositionPath().getKeyframe(handle.time).getValue(CameraProperties.POSITION).get();
            assertEquals(handle.x,position.getLeft(),0); assertEquals(handle.y,position.getMiddle(),0); assertEquals(handle.z,position.getRight(),0);
            assertEquals(Integer.valueOf(handle.replay),loaded.getTimePath().getKeyframe(handle.time).getValue(TimestampProperty.PROPERTY).get());
            assertLooksAtTarget(handle,2);
        }
        loaded.getTimePath().updateAll(); assertEquals(Integer.valueOf(12250),loaded.getTimePath().getValue(TimestampProperty.PROPERTY,2250).get());
        assertEquals(1,new ShotTiming(loaded).spans().size());
    }
    @Test public void maximumPointCountKeepsExactHandlesWithinSixHundredSamples() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.duration=300000; s.samplesPerSecond=60;
        while(s.curve.points.size()<ShotCurve.MAX_POINTS) {
            int i=0; while(!s.curve.canAddAfter(i)) i++;
            s.curve=s.curve.addAfter(i);
        }
        AutomatedShot.Plan plan=AutomatedShot.generate(s,FIXED,60000);
        assertEquals(600,plan.frames.size()); assertEquals(16,plan.handles.size());
        for(int i=0;i<plan.handles.size();i++) assertEquals(Math.round(s.curve.points.get(i).t*s.duration),plan.handles.get(i).time);
    }
    @Test public void reopeningOlderGeneratedPathsSmoothsTheirCameraWithoutChangingTheirTiming() {
        SPTimeline original=timeline(); AutomatedShot.place(original,AutomatedShot.generate(settings(AutomatedShot.Kind.FOLLOW),FIXED,60000));
        for (com.replaymod.replaystudio.pathing.path.PathSegment segment:new java.util.ArrayList<>(original.getPositionPath().getSegments()))
            original.setInterpolator(segment.getStartKeyframe().getTime(),InterpolatorType.LINEAR.newInstance());
        SPTimeline reopened=new SPTimeline(original.getTimeline());
        for (com.replaymod.replaystudio.pathing.path.PathSegment segment:reopened.getPositionPath().getSegments())
            assertTrue(segment.getInterpolator() instanceof com.replaymod.replaystudio.pathing.interpolation.CatmullRomSplineInterpolator);
        reopened.getTimePath().updateAll(); assertEquals(Integer.valueOf(13000),reopened.getTimePath().getValue(TimestampProperty.PROPERTY,3000).get());
    }
    @Test public void generatedFollowCameraHasContinuousVelocityBetweenSamplesAtATightTurn() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.FOLLOW); s.angleStart=180; s.angleChange=0;
        AutomatedShot.Plan plan=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(t/50.0,80,0,(float)(80*Math.sin(t/140.0))),60000);
        SPTimeline timeline=timeline(); AutomatedShot.place(timeline,plan); timeline.getPositionPath().updateAll();
        long time=plan.frames.get(60).time;
        org.apache.commons.lang3.tuple.Triple<Double,Double,Double> a=timeline.getPositionPath().getValue(CameraProperties.POSITION,time-1).get(),
            b=timeline.getPositionPath().getValue(CameraProperties.POSITION,time).get(),c=timeline.getPositionPath().getValue(CameraProperties.POSITION,time+1).get();
        assertEquals(b.getLeft()-a.getLeft(),c.getLeft()-b.getLeft(),.002);
        assertEquals(b.getRight()-a.getRight(),c.getRight()-b.getRight(),.002);
        timeline.getTimePath().updateAll(); assertEquals(Integer.valueOf(13000),timeline.getTimePath().getValue(TimestampProperty.PROPERTY,time).get());
    }
    @Test public void orbitPreservesRadiusAndFocusAndIncludesExactTimeEndpoints() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.angleStart=0; s.angleChange=360;
        AutomatedShot.Plan p=AutomatedShot.generate(s,FIXED,60000);
        assertEquals(121,p.frames.size()); assertEquals(0,p.start()); assertEquals(6000,p.end()); assertEquals(1,p.speed(),0);
        assertEquals(10000,p.frames.get(0).replay); assertEquals(16000,p.frames.get(120).replay);
        for(AutomatedShot.Frame f:p.frames) {
            assertEquals(24,Math.hypot(f.x-10,f.z-20),1e-6); assertEquals(90,f.y,1e-6); assertLooksAtTarget(f,2);
        }
        AutomatedShot.Frame a=p.frames.get(0),b=p.frames.get(120);
        assertEquals(a.x,b.x,1e-6); assertEquals(a.z,b.z,1e-6); assertEquals(360,Math.abs(b.yaw-a.yaw),.001);
    }
    @Test public void followUsesRecordedPositionAndHeadingRatherThanAFrozenCurrentEntity() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.FOLLOW); s.angleStart=180; s.angleChange=0;
        AutomatedShot.Plan p=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(t/100.0,80,0,t==16000?90:0),60000);
        AutomatedShot.Frame first=p.frames.get(0),last=p.frames.get(p.frames.size()-1);
        assertEquals(100,first.x,1e-6); assertEquals(-24,first.z,1e-6);
        assertEquals(184,last.x,1e-6); assertEquals(0,last.z,1e-6); assertLooksAtTarget(last,2);
    }
    @Test public void flyByTravelsAlongTheVehicleWithAConstantSideOffset() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.FLY_BY); s.distanceStart=s.distanceEnd=12; s.angleStart=-50; s.angleChange=50;
        AutomatedShot.Plan p=AutomatedShot.generate(s,FIXED,60000);
        assertEquals(22,p.frames.get(0).x,1e-6); assertEquals(-30,p.frames.get(0).z,1e-6);
        assertEquals(22,p.frames.get(120).x,1e-6); assertEquals(70,p.frames.get(120).z,1e-6);
        for(AutomatedShot.Frame f:p.frames) assertLooksAtTarget(f,2);
    }
    @Test public void cameraEasingDoesNotRetimeRecordedAction() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.replayOut=13000; s.ease=AutomatedShot.Ease.IN;
        AutomatedShot.Plan p=AutomatedShot.generate(s,FIXED,60000);
        assertEquals(11500,p.frames.get(60).replay); assertEquals(.5,p.speed(),0);
        assertEquals(.25,AutomatedShot.ease(.5,AutomatedShot.Ease.IN),0);
        assertEquals(.75,AutomatedShot.ease(.5,AutomatedShot.Ease.OUT),0);
    }
    @Test public void frozenActionStillMovesTheCameraAndHonoursRollAndHeightEndpoints() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.replayOut=s.replayIn; s.heightEnd=30; s.rollStart=-12; s.rollEnd=18;
        AutomatedShot.Plan p=AutomatedShot.generate(s,FIXED,60000);
        assertEquals(0,p.speed(),0); assertEquals(110,p.frames.get(120).y,0);
        assertEquals(-12,p.frames.get(0).roll,0); assertEquals(18,p.frames.get(120).roll,0);
        assertTrue(Math.abs(p.frames.get(0).x-p.frames.get(60).x)>1);
    }
    @Test public void yawAcrossNorthNeverAddsAFullTurnBetweenSamples() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.angleStart=-20; s.angleChange=60;
        AutomatedShot.Plan p=AutomatedShot.generate(s,FIXED,60000);
        for(int i=1;i<p.frames.size();i++) assertTrue(Math.abs(p.frames.get(i).yaw-p.frames.get(i-1).yaw)<5);
    }
    @Test public void invalidTimesDistancesAndCameraFocusCrossingAreRejectedBeforePlacement() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.replayOut=70000; rejected(() -> AutomatedShot.generate(s,FIXED,60000));
        s.replayOut=16000; s.distanceStart=Double.NaN; rejected(() -> AutomatedShot.generate(s,FIXED,60000));
        s.distanceStart=0; rejected(() -> AutomatedShot.generate(s,FIXED,60000));
        s.distanceStart=24; s.duration=99; rejected(() -> AutomatedShot.generate(s,FIXED,60000));
        s.duration=6000; s.kind=AutomatedShot.Kind.FLY_BY; s.distanceStart=s.distanceEnd=0; s.heightStart=s.heightEnd=2; s.angleStart=-50; s.angleChange=50;
        rejected(() -> AutomatedShot.generate(s,FIXED,60000));
    }
    @Test public void generationIsBoundedForLongShots() {
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.duration=300000;
        assertEquals(600,AutomatedShot.generate(s,FIXED,60000).frames.size());
    }
    @Test public void generatedSamplesRemainOneTimingRangeAndSurviveSavingAndRetiming() throws Exception {
        SPTimeline timeline=timeline(); AutomatedShot.Plan plan=AutomatedShot.generate(settings(AutomatedShot.Kind.ORBIT),FIXED,60000);
        AutomatedShot.place(timeline,plan);
        assertEquals(1,new ShotTiming(timeline).spans().size()); assertEquals(2,ShotGroups.visibleTimes(timeline,false).size());
        assertEquals(121,ShotGroups.visibleTimes(timeline,true).size());
        com.replaymod.replaystudio.pathing.serialize.TimelineSerialization serialization=new com.replaymod.replaystudio.pathing.serialize.TimelineSerialization(new SPTimeline(),null);
        String data=serialization.serialize(java.util.Collections.singletonMap("Orbit",timeline.getTimeline()));
        SPTimeline loaded=new SPTimeline(serialization.deserialize(data).get("Orbit"));
        assertEquals(1,new ShotTiming(loaded).spans().size());
        ShotTiming timing=new ShotTiming(loaded); timing.apply(timing.plan(timing.whole(),10000,16000,12000,60000));
        assertEquals(12000,timing.whole().duration()); assertEquals(1,timing.spans().size());
        assertEquals(.5,timing.whole().speed(),0);
        timing.apply(timing.slide(timing.whole(),5000,60000)); assertEquals(15000,timing.whole().sourceStart); assertEquals(1,timing.spans().size());
    }
    @Test public void deletingAGeneratedBoundaryRemovesTheWholeShotInOneUndoStep() {
        SPTimeline timeline=timeline(); AutomatedShot.place(timeline,AutomatedShot.generate(settings(AutomatedShot.Kind.ORBIT),FIXED,60000));
        assertTrue(ShotGroups.removeGenerated(timeline,6000)); assertTrue(timeline.getPositionPath().getKeyframes().isEmpty());
        assertTrue(timeline.getTimePath().getKeyframes().isEmpty()); timeline.getTimeline().undoLastChange();
        assertEquals(121,timeline.getPositionPath().getKeyframes().size()); assertEquals(1,new ShotTiming(timeline).spans().size());
    }
    @Test public void placementIsOneUndoAndRedoAndKeepsTheRequestedReplaySpeed() {
        SPTimeline timeline=timeline(); AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.replayOut=13000;
        AutomatedShot.Plan p=AutomatedShot.generate(s,FIXED,60000); AutomatedShot.place(timeline,p);
        assertEquals(121,timeline.getPositionPath().getKeyframes().size());
        timeline.getTimePath().updateAll(); assertEquals(Integer.valueOf(11500),timeline.getTimePath().getValue(TimestampProperty.PROPERTY,3000).get());
        timeline.getTimeline().undoLastChange(); assertTrue(timeline.getPositionPath().getKeyframes().isEmpty()); assertTrue(timeline.getTimePath().getKeyframes().isEmpty());
        timeline.getTimeline().redoLastChange(); assertEquals(121,timeline.getPositionPath().getKeyframes().size());
    }
    @Test public void anOccupiedSegmentWithNoKeysInsideTheDraftCannotBeOverwritten() {
        SPTimeline timeline=timeline(); ShotModel shots=new ShotModel(timeline);
        shots.save(-1,0,0,0,80,0,0,0,0); shots.save(-1,10000,20000,10,80,0,0,0,0);
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.outputStart=2000; s.duration=1000;
        AutomatedShot.Plan p=AutomatedShot.generate(s,FIXED,60000);
        rejected(() -> AutomatedShot.place(timeline,p)); assertEquals(2,timeline.getPositionPath().getKeyframes().size());
    }
    @Test public void appendRejectsReverseReplayTimeAndPreservesExistingCameraKeys() {
        SPTimeline timeline=timeline(); ShotModel shots=new ShotModel(timeline);
        shots.save(-1,0,20000,1,80,0,20,0,0); shots.save(-1,4000,24000,2,80,0,20,0,0);
        AutomatedShot.Settings s=settings(AutomatedShot.Kind.ORBIT); s.outputStart=4050;
        AutomatedShot.Plan invalid=AutomatedShot.generate(s,FIXED,60000); rejected(() -> AutomatedShot.place(timeline,invalid));
        s.replayIn=24000; s.replayOut=30000; AutomatedShot.place(timeline,AutomatedShot.generate(s,FIXED,60000));
        assertEquals(Double.valueOf(1),timeline.getPositionPath().getKeyframe(0).getValue(CameraProperties.POSITION).get().getLeft());
        timeline.getTimeline().undoLastChange(); assertEquals(2,timeline.getPositionPath().getKeyframes().size());
    }
    private static SPTimeline timeline() { SPTimeline timeline=new SPTimeline(); timeline.setDefaultInterpolatorType(InterpolatorType.CATMULL_ROM); return timeline; }
    private static void rejected(Runnable action) { try { action.run(); fail("Expected validation failure"); } catch(IllegalArgumentException expected) { } }
    private static void assertLooksAtTarget(AutomatedShot.Frame f,double focus) {
        double yaw=Math.toRadians(f.yaw),pitch=Math.toRadians(f.pitch),dx=f.target.x-f.x,dy=f.target.y+focus-f.y,dz=f.target.z-f.z,d=Math.sqrt(dx*dx+dy*dy+dz*dz);
        assertEquals(dx/d,-Math.sin(yaw)*Math.cos(pitch),1e-5); assertEquals(dy/d,-Math.sin(pitch),1e-5); assertEquals(dz/d,Math.cos(yaw)*Math.cos(pitch),1e-5);
    }
}
