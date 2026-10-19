package com.replaymod.studio;

import com.replaymod.pathing.properties.CameraProperties;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.simplepathing.InterpolatorType;
import com.replaymod.simplepathing.SPTimeline;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShotTimingTest {
    private SPTimeline timeline;
    private ShotModel shots;
    private ShotTiming timing;
    @Before public void setup() {
        timeline = new SPTimeline(); timeline.setDefaultInterpolatorType(InterpolatorType.CATMULL_ROM);
        shots = new ShotModel(timeline); timing = new ShotTiming(timeline);
    }
    private void shot(long time, int replay) { shots.save(-1, time, replay, time / 1000.0, 80, 10, 20, 30, 40); }
    @Test public void slidingOneTimesActionPreservesCameraTimingAndCanBeUndoneInOneStep() {
        shot(0, 20000); shot(4000, 24000);
        ShotTiming.Plan plan = timing.slide(timing.whole(), 10000, 60000);
        assertEquals(30000, plan.after.sourceStart); assertEquals(34000, plan.after.sourceEnd);
        assertEquals(1, plan.after.speed(), 0); assertEquals(0, plan.shift);
        timing.apply(plan);
        assertEquals(Integer.valueOf(32000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 2000).get());
        assertTrue(timeline.isPositionKeyframe(4000));
        assertEquals(Double.valueOf(4), timeline.getPositionPath().getKeyframe(4000).getValue(CameraProperties.POSITION).get().getLeft());
        timeline.getTimeline().undoLastChange();
        assertEquals(Integer.valueOf(22000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 2000).get());
        timeline.getTimeline().redoLastChange(); assertEquals(30000, timing.whole().sourceStart);
    }
    @Test public void slidingClampsAtRecordingEndsWithoutChangingSpeedOrDuration() {
        shot(0, 20000); shot(8000, 24000);
        ShotTiming.Plan left = timing.slide(timing.whole(), Long.MIN_VALUE, 60000);
        assertEquals(0, left.after.sourceStart); assertEquals(4000, left.after.sourceEnd);
        ShotTiming.Plan right = timing.slide(timing.whole(), Long.MAX_VALUE, 60000);
        assertEquals(56000, right.after.sourceStart); assertEquals(60000, right.after.sourceEnd);
        assertEquals(0.5, right.after.speed(), 0); assertEquals(8000, right.after.duration());
    }
    @Test public void slidingSelectedMoveStopsAtNeighboursAndLeavesOtherReplayKeysUnchanged() {
        shot(0, 0); shot(4000, 20000); shot(8000, 24000); shot(12000, 30000);
        ShotTiming.Span middle = timing.at(4000);
        ShotTiming.Plan right = timing.slide(middle, 50000, 60000);
        assertEquals(26000, right.after.sourceStart); assertEquals(30000, right.after.sourceEnd);
        timing.apply(right);
        assertEquals(Integer.valueOf(0), timeline.getTimePath().getKeyframe(0).getValue(TimestampProperty.PROPERTY).get());
        assertEquals(Integer.valueOf(30000), timeline.getTimePath().getKeyframe(12000).getValue(TimestampProperty.PROPERTY).get());
        ShotTiming.Plan left = timing.slide(timing.at(4000), -50000, 60000);
        assertEquals(0, left.after.sourceStart); assertEquals(4000, left.after.sourceEnd);
    }
    @Test public void slidingPreservesVariableClockDetailAndFrozenRanges() {
        shot(0, 1000); shot(4000, 5000); timeline.addTimeKeyframe(1000, 1100);
        timing.apply(timing.slide(timing.whole(), 10000, 20000));
        assertTrue(timing.whole().variableSpeed);
        assertEquals(Integer.valueOf(11100), timeline.getTimePath().getKeyframe(1000).getValue(TimestampProperty.PROPERTY).get());
        timeline.getTimeline().undoLastChange();
        assertEquals(Integer.valueOf(1100), timeline.getTimePath().getKeyframe(1000).getValue(TimestampProperty.PROPERTY).get());
        timing.apply(timing.plan(timing.whole(), 2000, 2000, 4000, 20000));
        timing.apply(timing.slide(timing.whole(), 3000, 20000));
        assertEquals(5000, timing.whole().sourceStart); assertEquals(5000, timing.whole().sourceEnd);
        assertEquals(0, timing.whole().speed(), 0); assertEquals(4000, timing.whole().duration());
    }
    @Test public void slidingTimingDraftKeepsItsChosenSpeedDurationAndRipple() {
        shot(0, 20000); shot(4000, 24000); shot(6000, 40000);
        ShotTiming.Plan draft = timing.plan(timing.at(0), 20000, 24000, 8000, 60000);
        Object undo = timeline.getTimeline().peekUndoStack();
        ShotTiming.Plan slid = timing.slide(draft, 5000, 60000);
        assertSame(undo, timeline.getTimeline().peekUndoStack());
        assertEquals(20000, timing.at(0).sourceStart);
        assertEquals(25000, slid.after.sourceStart); assertEquals(29000, slid.after.sourceEnd);
        assertEquals(0.5, slid.after.speed(), 0); assertEquals(4000, slid.shift);
        timing.apply(slid);
        assertTrue(timeline.isPositionKeyframe(8000)); assertTrue(timeline.isPositionKeyframe(10000));
        assertEquals(Integer.valueOf(27000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 4000).get());
        timeline.getTimeline().undoLastChange(); assertTrue(timeline.isPositionKeyframe(4000)); assertTrue(timeline.isPositionKeyframe(6000));
    }
    @Test public void rangeHitTestingDistinguishesSlidingFromResizingIncludingOverlappingEndpoints() {
        assertEquals(0, TimelineGraphics.rangeHandle(100, 100, 300, false));
        assertEquals(1, TimelineGraphics.rangeHandle(300, 100, 300, false));
        assertEquals(2, TimelineGraphics.rangeHandle(200, 100, 300, false));
        assertEquals(2, TimelineGraphics.rangeHandle(100, 100, 300, true));
        assertEquals(-1, TimelineGraphics.rangeHandle(400, 100, 300, true));
        assertEquals(1, TimelineGraphics.rangeHandle(100, 100, 100, false));
        assertEquals(2, TimelineGraphics.rangeHandle(100, 100, 100, true));
    }
    @Test public void slowMotionRescalesCameraKeysAndRipplesLaterShotsInOneUndoStep() {
        shot(0, 20000); shot(2000, 22000); shot(4000, 24000);
        ShotTiming.Plan plan = timing.plan(timing.at(0), 20000, 22000, 8000, 60000);
        assertEquals(6000, plan.shift); assertEquals(10000, plan.totalDuration);
        assertEquals(0.25, plan.after.speed(), 0.0001);
        timing.apply(plan);
        assertTrue(timeline.isPositionKeyframe(8000)); assertTrue(timeline.isTimeKeyframe(8000));
        assertTrue(timeline.isPositionKeyframe(10000));
        assertEquals(Double.valueOf(2), timeline.getPositionPath().getKeyframe(8000).getValue(CameraProperties.POSITION).get().getLeft());
        assertEquals(Integer.valueOf(21000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 4000).get());
        timeline.getTimeline().undoLastChange();
        assertTrue(timeline.isPositionKeyframe(2000)); assertTrue(timeline.isPositionKeyframe(4000));
        assertFalse(timeline.isPositionKeyframe(8000));
        timeline.getTimeline().redoLastChange(); assertTrue(timeline.isPositionKeyframe(10000));
    }
    @Test public void shorteningMovesInAscendingOrderWithoutCollidingWithOldKeyPositions() {
        shot(0, 1000); shot(2000, 3000); shot(4000, 5000); shot(6000, 7000);
        timing.apply(timing.plan(timing.whole(), 1000, 7000, 3000, 10000));
        assertTrue(timeline.isPositionKeyframe(1000)); assertTrue(timeline.isPositionKeyframe(2000)); assertTrue(timeline.isPositionKeyframe(3000));
        assertEquals(Double.valueOf(6), timeline.getPositionPath().getKeyframe(3000).getValue(CameraProperties.POSITION).get().getLeft());
        assertEquals(Integer.valueOf(5000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 2000).get());
    }
    @Test public void intermediateClockKeysUseTheSameMappingAndUndoRestoresTheirOriginalTimes() {
        shot(0, 1000); shot(4000, 5000); timeline.addTimeKeyframe(1000, 1200);
        timing.apply(timing.plan(timing.whole(), 10000, 12000, 8000, 20000));
        assertTrue(timeline.isTimeKeyframe(2000));
        assertEquals(Integer.valueOf(10500), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 2000).get());
        timeline.getTimeline().undoLastChange();
        assertTrue(timeline.isTimeKeyframe(1000)); assertFalse(timeline.isTimeKeyframe(2000));
        assertEquals(Integer.valueOf(1200), timeline.getTimePath().getKeyframe(1000).getValue(TimestampProperty.PROPERTY).get());
    }
    @Test public void freezeHasExplicitZeroRateAndRetainsTheCameraDuration() {
        shot(0, 1000); shot(4000, 5000);
        ShotTiming.Plan plan = timing.plan(timing.whole(), 2000, 2000, 4000, 10000);
        assertEquals("FROZEN", plan.after.rateLabel());
        timing.apply(plan);
        assertEquals(Integer.valueOf(2000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 3000).get());
        assertEquals(4000, timing.whole().duration());
    }
    @Test public void invalidNeighbourOrderAndOutOfRecordingRangeLeaveHistoryUntouched() {
        shot(0, 1000); shot(2000, 3000); shot(4000, 5000);
        Object undo = timeline.getTimeline().peekUndoStack();
        try { timing.plan(timing.at(0), 1000, 6000, 2000, 10000); fail(); } catch (IllegalArgumentException expected) { }
        try { timing.plan(timing.whole(), 1000, 11000, 4000, 10000); fail(); } catch (IllegalArgumentException expected) { }
        assertSame(undo, timeline.getTimeline().peekUndoStack());
        assertEquals(3, shots.times().size());
    }
    @Test public void veryShortDurationRejectsCollapsedKeysBeforeAnyMutation() {
        shot(0, 1000); shot(1, 1001); shot(10000, 11000);
        try { timing.plan(timing.whole(), 1000, 11000, 10, 20000); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(timeline.isPositionKeyframe(1)); assertTrue(timeline.isPositionKeyframe(10000));
    }
    @Test public void speedAndDurationCalculationMatchesSlowNormalAndFastAction() {
        assertEquals(8000, ShotTiming.durationAtSpeed(20000, 22000, 0.25));
        assertEquals(2000, ShotTiming.durationAtSpeed(20000, 22000, 1));
        assertEquals(1000, ShotTiming.durationAtSpeed(20000, 22000, 2));
        for (double speed : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { ShotTiming.durationAtSpeed(20000, 22000, speed); fail(); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void selectingLastShotEditsItsPrecedingMove() {
        shot(0, 1000); shot(2000, 3000); shot(4000, 5000);
        assertEquals(2000, timing.at(4000).start); assertEquals(4000, timing.at(4000).end);
        assertEquals(0, timing.at(0).start); assertEquals(0, timing.whole().start);
    }
    @Test public void leadingGapRemovalPreservesCameraAndClockValuesAndUndoesTogether() {
        shot(0, 20000); shot(4000, 24000);
        timeline.editAtomically(() -> {
            for (SPTimeline.SPPath path : SPTimeline.SPPath.values()) {
                timeline.recordAppliedChange(timeline.moveKeyframe(path, 4000, 6270));
                timeline.recordAppliedChange(timeline.moveKeyframe(path, 0, 2270));
            }
        });
        assertEquals(2270, timing.removeLeadingGap());
        assertEquals(0, timing.whole().start); assertEquals(4000, timing.whole().end);
        assertEquals(Integer.valueOf(20000), timeline.getTimePath().getKeyframe(0).getValue(TimestampProperty.PROPERTY).get());
        timeline.getTimeline().undoLastChange(); assertTrue(timeline.isPositionKeyframe(2270)); assertTrue(timeline.isTimeKeyframe(6270));
    }
    @Test public void timelinePixelsAndHitTimesUseOneMappingAtZoomedAndClippedRanges() {
        assertEquals(94, TimelineGraphics.pixel(0, 0, 10000, 94, 832));
        assertEquals(926, TimelineGraphics.pixel(10000, 0, 10000, 94, 832));
        assertEquals(5000, TimelineGraphics.time(510, 0, 10000, 94, 832));
        assertEquals(94, TimelineGraphics.pixel(1000, 5000, 10000, 94, 832));
        assertEquals(15000, TimelineGraphics.time(1000, 5000, 10000, 94, 832));
    }
    @Test public void speedCanChangeActionRangeWhileKeepingCameraDurationFixed() {
        assertEquals(22000, ShotTiming.sourceEndAtSpeed(20000, 8000, 0.25, 60000));
        assertEquals(28000, ShotTiming.sourceEndAtSpeed(20000, 8000, 1, 60000));
        assertEquals(36000, ShotTiming.sourceEndAtSpeed(20000, 8000, 2, 60000));
        shot(0, 20000); shot(8000, 28000);
        timing.apply(timing.plan(timing.whole(), 20000, ShotTiming.sourceEndAtSpeed(20000, 8000, 0.25, 60000), 8000, 60000));
        assertTrue(timeline.isPositionKeyframe(8000));
        assertEquals(Integer.valueOf(21000), timeline.getTimePath().getValue(TimestampProperty.PROPERTY, 4000).get());
        try { ShotTiming.sourceEndAtSpeed(58000, 8000, 1, 60000); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void variableClockKeysAreReportedAsAnAverageUntilExplicitlyRetimed() {
        shot(0, 1000); shot(4000, 5000); timeline.addTimeKeyframe(1000, 1100);
        assertTrue(timing.whole().variableSpeed);
        timing.apply(timing.plan(timing.whole(), 1000, 5000, 4000, 10000));
        assertFalse(timing.whole().variableSpeed);
        timeline.getTimeline().undoLastChange(); assertTrue(timing.whole().variableSpeed);
    }
    @Test public void removingGapAlsoTrimsEarlierClockOnlyKeysAndRestoresThemOnUndo() {
        shot(0, 10000); shot(4000, 14000);
        timeline.recordAppliedChange(timeline.moveKeyframe(SPTimeline.SPPath.POSITION, 0, 2000));
        assertEquals(2000, timing.removeLeadingGap());
        assertTrue(timeline.isPositionKeyframe(0)); assertTrue(timeline.isPositionKeyframe(2000));
        assertEquals(Integer.valueOf(12000), timeline.getTimePath().getKeyframe(0).getValue(TimestampProperty.PROPERTY).get());
        timeline.getTimeline().undoLastChange();
        assertFalse(timeline.isPositionKeyframe(0)); assertTrue(timeline.isPositionKeyframe(2000));
        assertEquals(Integer.valueOf(10000), timeline.getTimePath().getKeyframe(0).getValue(TimestampProperty.PROPERTY).get());
    }
}
