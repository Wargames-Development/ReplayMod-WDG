package com.replaymod.studio;

import com.replaymod.pathing.properties.SpectatorProperty;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.SPTimeline.SPPath;
import java.util.SortedSet;
import java.util.TreeSet;

/** Edits the existing ReplayStudio timeline; no separate project or save format. */
public final class ShotModel {
    private final SPTimeline timeline;
    public ShotModel(SPTimeline timeline) { this.timeline = timeline; }
    public SortedSet<Long> times() {
        SortedSet<Long> result = new TreeSet<>();
        timeline.getTimeline().getPaths().forEach(path -> path.getKeyframes().forEach(key -> result.add(key.getTime())));
        return result;
    }
    public long save(long oldTime, long requestedTime, int sourceTime, double x, double y, double z,
                     float yaw, float pitch, float roll) {
        if (requestedTime < 0 || requestedTime > Integer.MAX_VALUE || sourceTime < 0) throw new IllegalArgumentException("Times must be positive and within the replay.");
        // A new sequence starts at video zero, whatever moment or empty output
        // slot the user was browsing. Keep the chosen recorded moment and pose.
        final long time = timeline.getPositionPath().getKeyframes().isEmpty() ? 0 : requestedTime;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("Camera values must be finite.");
        }
        if (pitch < -90 || pitch > 90) throw new IllegalArgumentException("Pitch must be between -90 and 90 degrees.");
        if (oldTime != time && times().contains(time)) throw new IllegalArgumentException("A shot already exists at that output time. Use Next slot + step to add another shot, or Apply values / update shot to edit the selected shot.");
        Keyframe old = timeline.getPositionPath().getKeyframe(oldTime);
        if (old != null && old.getValue(SpectatorProperty.PROPERTY).isPresent()) {
            throw new IllegalArgumentException("This keyframe follows a recorded player. Click Use current camera to detach it before editing its pose.");
        }
        for (Keyframe key : timeline.getTimePath().getKeyframes()) {
            if (key.getTime() == oldTime) continue;
            int source = key.getValue(TimestampProperty.PROPERTY).orElse(0);
            if ((key.getTime() < time && source > sourceTime) || (key.getTime() > time && source < sourceTime)) {
                throw new IllegalArgumentException("Replay time must run forward. Equal replay times create a frozen moment.");
            }
        }
        timeline.editAtomically(() -> {
            if (oldTime != time) {
                for (SPPath path : SPPath.values()) {
                    if (timeline.getKeyframe(path, oldTime) != null) {
                        timeline.recordAppliedChange(timeline.moveKeyframe(path, oldTime, time));
                    }
                }
            }
            if (timeline.isPositionKeyframe(time)) timeline.recordAppliedChange(timeline.updatePositionKeyframe(time, x, y, z, yaw, pitch, roll));
            else timeline.addPositionKeyframe(time, x, y, z, yaw, pitch, roll, -1);
            if (timeline.isTimeKeyframe(time)) timeline.recordAppliedChange(timeline.updateTimeKeyframe(time, sourceTime));
            else timeline.addTimeKeyframe(time, sourceTime);
        });
        return time;
    }
    /** Capture appends at an occupied cursor; updating an existing shot remains explicit. */
    public long capture(long requestedTime, long step, int sourceTime, boolean ending, boolean advanceAction, int recordingDuration,
                        double x, double y, double z, float yaw, float pitch, float roll) {
        SortedSet<Long> shots = times();
        if (ending && timeline.getPositionPath().getKeyframes().isEmpty()) throw new IllegalArgumentException("Capture the starting camera with I first.");
        long time = requestedTime;
        if (!shots.isEmpty()) {
            long last = shots.last();
            if (shots.contains(time) || ending && time <= last) {
                if (step < 10) throw new IllegalArgumentException("Set a camera step of at least 0.01 seconds.");
                time = last + step;
            }
            if (time > last && advanceAction) {
                Keyframe previous = timeline.getTimePath().getKeyframe(last);
                int previousSource = previous == null ? sourceTime : previous.getValue(TimestampProperty.PROPERTY).orElse(sourceTime);
                if (sourceTime <= previousSource) {
                    long nextSource = previousSource + time - last;
                    if (nextSource > recordingDuration) throw new IllegalArgumentException("The recording ends before this camera step. Shorten Step or choose frozen action.");
                    sourceTime = (int) nextSource;
                }
            }
        }
        if (sourceTime > recordingDuration) throw new IllegalArgumentException("Replay time is outside this recording.");
        return save(-1, time, sourceTime, x, y, z, yaw, pitch, roll);
    }
    public void remove(long time) {
        timeline.editAtomically(() -> {
            if (timeline.isPositionKeyframe(time)) timeline.removePositionKeyframe(time);
            if (timeline.isTimeKeyframe(time)) timeline.removeTimeKeyframe(time);
        });
    }

    public boolean isActionFrozen() {
        Integer first = null;
        int count = 0;
        for (Keyframe key : timeline.getTimePath().getKeyframes()) {
            Integer stamp = key.getValue(TimestampProperty.PROPERTY).orElse(null);
            if (stamp == null) return false;
            if (first == null) first = stamp;
            else if (!first.equals(stamp)) return false;
            count++;
        }
        return count >= 2;
    }

    /** Keep the first recorded moment and camera poses; advance action at 1x. */
    public void playActionAtNormalSpeed(int recordingDuration) {
        SortedSet<Long> shots = times();
        if (timeline.getPositionPath().getSegments().isEmpty() || shots.size() < 2) {
            throw new IllegalArgumentException("Add at least two camera shots first.");
        }
        long firstOutput = shots.first();
        timeline.getTimePath().updateAll();
        int firstSource = timeline.getTimePath().getValue(TimestampProperty.PROPERTY, firstOutput)
                .orElseThrow(() -> new IllegalArgumentException("Set a replay time on the first shot first."));
        long lastSource = firstSource + shots.last() - firstOutput;
        if (firstSource < 0 || lastSource > recordingDuration || lastSource > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("There is not enough recording after the first shot for 1x playback. Choose an earlier replay time for the first shot, or shorten the video path.");
        }
        timeline.editAtomically(() -> {
            for (long time : shots) {
                int stamp = (int) (firstSource + time - firstOutput);
                if (timeline.isTimeKeyframe(time)) timeline.recordAppliedChange(timeline.updateTimeKeyframe(time, stamp));
                else timeline.addTimeKeyframe(time, stamp);
            }
        });
        timeline.getTimePath().updateAll();
    }
}
