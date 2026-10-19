package com.replaymod.studio;

import com.replaymod.pathing.properties.CameraProperties;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.simplepathing.InterpolatorType;
import com.replaymod.simplepathing.SPTimeline;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShotModelTest {
    private SPTimeline timeline;
    private ShotModel shots;
    @Before public void prepare() { timeline = new SPTimeline(); timeline.setDefaultInterpolatorType(InterpolatorType.CATMULL_ROM); shots = new ShotModel(timeline); }
    private void add(long output, int replay) { shots.save(-1, output, replay, output / 1000.0, 80, 12, 30, -5, 0); }
    @Test public void pairedShotIsOneUndoStepAndRedoRestoresBothTracks() {
        add(0, 5000);
        assertEquals(1, timeline.getPositionPath().getKeyframes().size());
        assertEquals(1, timeline.getTimePath().getKeyframes().size());
        timeline.getTimeline().undoLastChange();
        assertTrue(shots.times().isEmpty());
        timeline.getTimeline().redoLastChange();
        assertTrue(timeline.isPositionKeyframe(0)); assertTrue(timeline.isTimeKeyframe(0));
    }
    @Test public void movingAndEditingShotPreservesItsPairedSourceTimeAndUndoesTogether() {
        add(0, 1000); add(2000, 3000);
        shots.save(2000, 3500, 4500, 100, 80, 40, 90, 15, 20);
        assertFalse(timeline.isPositionKeyframe(2000));
        assertEquals(Integer.valueOf(4500), timeline.getTimePath().getKeyframe(3500).getValue(TimestampProperty.PROPERTY).get());
        timeline.getTimeline().undoLastChange();
        assertTrue(timeline.isTimeKeyframe(2000)); assertTrue(timeline.isPositionKeyframe(2000));
        assertFalse(timeline.isPositionKeyframe(3500));
        assertEquals(Double.valueOf(2), timeline.getPositionPath().getKeyframe(2000).getValue(CameraProperties.POSITION).get().getLeft());
    }
    @Test public void deletingPairedShotCanBeUndoneInOneStep() {
        add(0, 1000); add(2000, 3000); shots.remove(2000);
        assertFalse(timeline.isPositionKeyframe(2000)); assertFalse(timeline.isTimeKeyframe(2000));
        timeline.getTimeline().undoLastChange();
        assertTrue(timeline.isPositionKeyframe(2000)); assertTrue(timeline.isTimeKeyframe(2000));
    }
    @Test public void invalidClockOrderAndCollisionsLeaveBothTracksUnchanged() {
        add(0, 1000); add(2000, 3000);
        try { add(1000, 4000); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals(2, shots.times().size());
        try { shots.save(2000, 0, 1000, 0, 0, 0, 0, 0, 0); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(timeline.isPositionKeyframe(2000));
    }
    @Test public void equalSourceTimesAllowFrozenActionWhileCameraMoves() {
        add(0, 1000); add(2000, 1000);
        timeline.getTimePath().updateAll();
        assertEquals(Integer.valueOf(1000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 1000).get());
    }
    @Test public void failedCompoundEditRollsBackWithoutAddingHistory() {
        try { timeline.editAtomically(() -> { timeline.addTimeKeyframe(0, 1000); throw new IllegalArgumentException("abort"); }); fail(); }
        catch (IllegalArgumentException expected) { }
        assertTrue(timeline.getTimePath().getKeyframes().isEmpty());
        assertNull(timeline.getTimeline().peekUndoStack());
    }
    @Test public void nonFinitePoseDoesNotCreatePartialKeys() {
        try { shots.save(-1, 0, 1000, Double.NaN, 0, 0, 0, 0, 0); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(shots.times().isEmpty());
    }
    @Test public void firstShotFromBrowsedEmptySlotStartsAtZeroAndPreservesTheRecordedMomentAndPose() {
        long actual = shots.save(-1, 20230, 38803, -37.202, 89.983, 288.968, -113.205f, 20.218f, 0);
        assertEquals(0, actual);
        assertTrue(timeline.isPositionKeyframe(0)); assertTrue(timeline.isTimeKeyframe(0));
        assertFalse(timeline.isPositionKeyframe(20230));
        assertEquals(Integer.valueOf(38803), timeline.getTimePath().getKeyframe(0).getValue(TimestampProperty.PROPERTY).get());
        assertEquals(Double.valueOf(-37.202), timeline.getPositionPath().getKeyframe(0).getValue(CameraProperties.POSITION).get().getLeft());
        timeline.getTimeline().undoLastChange(); assertTrue(shots.times().isEmpty());
        timeline.getTimeline().redoLastChange(); assertTrue(timeline.isPositionKeyframe(0)); assertTrue(timeline.isTimeKeyframe(0));
        long second = shots.save(-1, 2000, 40803, 1, 2, 3, 4, 5, 6);
        assertEquals(2000, second);
        assertEquals(2, shots.times().size());
    }
    @Test public void firstFreeCameraCaptureUsesTheSameZeroBasedStartPolicy() {
        long actual = shots.save(20230, 20230, 38803, 1, 2, 3, 4, 5, 6);
        assertEquals(0, actual);
        assertTrue(timeline.isTimeKeyframe(0)); assertTrue(timeline.isPositionKeyframe(0));
        assertEquals(Integer.valueOf(38803), timeline.getTimePath().getKeyframe(0).getValue(TimestampProperty.PROPERTY).get());
    }
    @Test public void invalidRequestedTimeIsRejectedBeforeNormalisingTheFirstShot() {
        try { shots.save(-1, -10, 1000, 0, 0, 0, 0, 0, 0); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(shots.times().isEmpty());
    }
    @Test public void followingKeyframeCanBeExplicitlyDetachedAndUndone() {
        timeline.addPositionKeyframe(0, 1, 2, 3, 4, 5, 6, 42);
        timeline.addTimeKeyframe(0, 1000);
        assertTrue(timeline.isSpectatorKeyframe(0));
        timeline.recordAppliedChange(timeline.detachSpectatorKeyframe(0));
        assertFalse(timeline.isSpectatorKeyframe(0));
        timeline.getTimeline().undoLastChange();
        assertTrue(timeline.isSpectatorKeyframe(0));
    }
    @Test public void normalSpeedRepairsReportedFrozenPathWithoutMovingCameraKeysAndUndoesInOneStep() {
        add(0, 21958); add(4380, 21958);
        assertTrue(shots.isActionFrozen());
        shots.playActionAtNormalSpeed(33268);
        assertFalse(shots.isActionFrozen());
        assertEquals(Integer.valueOf(21958), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 0).get());
        assertEquals(Integer.valueOf(24148), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 2190).get());
        assertEquals(Integer.valueOf(26338), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 4380).get());
        assertEquals(Double.valueOf(4.38), timeline.getPositionPath().getKeyframe(4380).getValue(CameraProperties.POSITION).get().getLeft());
        timeline.getTimeline().undoLastChange();
        assertTrue(shots.isActionFrozen());
        timeline.getTimeline().redoLastChange();
        assertFalse(shots.isActionFrozen());
    }
    @Test public void normalSpeedRejectsRunningPastRecordingEndWithoutChangingHistoryOrKeys() {
        add(0, 31000); add(4380, 31000);
        Object previous = timeline.getTimeline().peekUndoStack();
        try { shots.playActionAtNormalSpeed(33268); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(shots.isActionFrozen());
        assertSame(previous, timeline.getTimeline().peekUndoStack());
    }
    @Test public void normalSpeedAddsMissingClockKeysAndRetimesExistingIntermediateClockKeys() {
        add(0, 1000);
        timeline.addPositionKeyframe(4000, 1, 2, 3, 4, 5, 6, -1);
        timeline.addTimeKeyframe(2000, 1000);
        shots.playActionAtNormalSpeed(10000);
        assertEquals(Integer.valueOf(3000), timeline.getTimePath().getKeyframe(2000).getValue(TimestampProperty.PROPERTY).get());
        assertEquals(Integer.valueOf(5000), timeline.getTimePath().getKeyframe(4000).getValue(TimestampProperty.PROPERTY).get());
        timeline.getTimeline().undoLastChange();
        assertFalse(timeline.isTimeKeyframe(4000));
        assertEquals(Integer.valueOf(1000), timeline.getTimePath().getKeyframe(2000).getValue(TimestampProperty.PROPERTY).get());
    }
}
