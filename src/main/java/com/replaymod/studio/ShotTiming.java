package com.replaymod.studio;

import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.replaystudio.pathing.change.Change;
import com.replaymod.replaystudio.pathing.change.SetInterpolator;
import com.replaymod.replaystudio.pathing.interpolation.LinearInterpolator;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.PathSegment;
import com.replaymod.simplepathing.SPTimeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

/** One timing model for numerical controls, timeline graphics and committed edits. */
public final class ShotTiming {
    private final SPTimeline timeline;
    public ShotTiming(SPTimeline timeline) { this.timeline = timeline; }

    public static final class Span {
        public final long start, end;
        public final int sourceStart, sourceEnd;
        public final boolean variableSpeed;
        public Span(long start, long end, int sourceStart, int sourceEnd) {
            this(start, end, sourceStart, sourceEnd, false);
        }
        private Span(long start, long end, int sourceStart, int sourceEnd, boolean variableSpeed) {
            this.start = start; this.end = end; this.sourceStart = sourceStart; this.sourceEnd = sourceEnd;
            this.variableSpeed = variableSpeed;
        }
        public long duration() { return end - start; }
        public double speed() { return (sourceEnd - (double) sourceStart) / duration(); }
        public String rateLabel() {
            if (sourceEnd < sourceStart) return "INVALID: Replay Out is before In";
            if (sourceEnd == sourceStart) return "FROZEN";
            double rate = speed();
            return (variableSpeed ? "Variable; average " : "") + String.format(Locale.ROOT, rate < 0.001 ? "%.6fx %s" : "%.3fx %s", rate, rate < 0.9995 ? "slow motion" : rate > 1.0005 ? "fast motion" : "normal speed");
        }
    }

    public List<Span> spans() {
        timeline.getTimePath().updateAll();
        List<Span> result = new ArrayList<>();
        Keyframe previous = null;
        for (Keyframe key : ShotGroups.boundaries(timeline)) {
            if (previous != null) {
                Integer from = timeline.getTimePath().getValue(TimestampProperty.PROPERTY, previous.getTime()).orElse(null);
                Integer to = timeline.getTimePath().getValue(TimestampProperty.PROPERTY, key.getTime()).orElse(null);
                if (from != null && to != null) result.add(describe(previous.getTime(), key.getTime(), from, to));
            }
            previous = key;
        }
        return result;
    }
    public Span at(long time) {
        List<Span> spans = spans();
        if (spans.isEmpty()) return null;
        for (Span span : spans) if (time < span.end) return span;
        return spans.get(spans.size() - 1);
    }
    public Span whole() {
        List<Span> spans = spans();
        if (spans.isEmpty()) return null;
        Span first = spans.get(0), last = spans.get(spans.size() - 1);
        return describe(first.start, last.end, first.sourceStart, last.sourceEnd);
    }
    private Span describe(long start, long end, int from, int to) {
        boolean variable = false;
        for (Keyframe key : timeline.getTimePath().getKeyframes()) if (key.getTime() > start && key.getTime() < end) {
            int stamp = key.getValue(TimestampProperty.PROPERTY).orElse(from);
            double expected = from + (key.getTime() - start) * (double) (to - from) / (end - start);
            if (Math.abs(stamp - expected) > 1) variable = true;
        }
        return new Span(start, end, from, to, variable);
    }
    public static long durationAtSpeed(int from, int to, double speed) {
        if (!Double.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("Action speed must be a positive finite number. Use Freeze for a still moment.");
        if (to <= from) throw new IllegalArgumentException("Choose an advancing replay range for a playback speed. For frozen action, choose a camera duration instead.");
        double duration = (to - (double) from) / speed;
        if (duration < 10 || duration > Integer.MAX_VALUE) throw new IllegalArgumentException("That speed would make the video duration too short or too long.");
        return Math.round(duration);
    }
    public static int sourceEndAtSpeed(int from, long duration, double speed, int recordingDuration) {
        if (!Double.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("Action speed must be a positive finite number. Use Freeze for a still moment.");
        if (duration < 10 || duration > Integer.MAX_VALUE) throw new IllegalArgumentException("Choose a video duration of at least 0.01 seconds.");
        double end = from + duration * speed;
        if (from < 0 || end > recordingDuration || end > Integer.MAX_VALUE) throw new IllegalArgumentException("That speed needs action beyond the recording. Choose a slower speed, shorter duration or earlier Replay In.");
        if (Math.round(end) <= from) throw new IllegalArgumentException("That speed advances less than one millisecond. Use Freeze instead.");
        return (int) Math.round(end);
    }

    public static final class Plan {
        public final Span before, after;
        public final long shift, totalDuration;
        public final Map<Long, Long> outputTimes;
        private final Map<Long, Integer> sourceTimes;
        private final boolean linearize;
        private Plan(Span before, Span after, long totalDuration, Map<Long, Long> outputTimes, Map<Long, Integer> sourceTimes) {
            this(before, after, totalDuration, outputTimes, sourceTimes, true);
        }
        private Plan(Span before, Span after, long totalDuration, Map<Long, Long> outputTimes, Map<Long, Integer> sourceTimes, boolean linearize) {
            this.before = before; this.after = after; this.shift = after.end - before.end;
            this.totalDuration = totalDuration;
            this.outputTimes = Collections.unmodifiableMap(outputTimes);
            this.sourceTimes = sourceTimes;
            this.linearize = linearize;
        }
    }

    /** Slide recorded action beneath a stationary camera move, preserving rate and clock detail. */
    public Plan slide(Span span, long delta, int recordingDuration) {
        if (span == null || span.duration() <= 0 || !timeline.isPositionKeyframe(span.start) || !timeline.isPositionKeyframe(span.end)) throw new IllegalArgumentException("Choose a camera move to slide its recorded action.");
        SortedSet<Long> times = new ShotModel(timeline).times();
        Map<Long, Long> outputs = new LinkedHashMap<>();
        for (long time : times) outputs.put(time, time);
        Map<Long, Integer> sources = new TreeMap<>();
        for (Keyframe key : timeline.getTimePath().getKeyframes()) sources.put(key.getTime(), key.getValue(TimestampProperty.PROPERTY).orElseThrow(() -> new IllegalArgumentException("A clock key has no replay time.")));
        sources.put(span.start, span.sourceStart); sources.put(span.end, span.sourceEnd);
        return slide(new Plan(span, span, times.last(), outputs, sources, false), delta, recordingDuration);
    }

    /** Shift a draft as well as a saved range; clamp both ends together at recording/neighbour limits. */
    public Plan slide(Plan draft, long delta, int recordingDuration) {
        Span range = draft.after;
        long minimum = -((long) range.sourceStart), maximum = (long) recordingDuration - range.sourceEnd;
        for (Map.Entry<Long, Integer> key : draft.sourceTimes.entrySet()) {
            if (key.getKey() < range.start) minimum = Math.max(minimum, (long) key.getValue() - range.sourceStart);
            if (key.getKey() > range.end) maximum = Math.min(maximum, (long) key.getValue() - range.sourceEnd);
        }
        if (minimum > maximum) throw new IllegalArgumentException("This range cannot fit between its neighbouring replay times. Edit Whole sequence instead.");
        long shift = Math.max(minimum, Math.min(maximum, delta));
        Map<Long, Integer> sources = new TreeMap<>();
        int previous = -1;
        for (Map.Entry<Long, Integer> key : draft.sourceTimes.entrySet()) {
            long time = key.getKey();
            long stamp = key.getValue() + (time >= range.start && time <= range.end ? shift : 0);
            if (stamp < previous || stamp < 0 || stamp > recordingDuration) throw new IllegalArgumentException("The replay range must run forward inside the recording.");
            sources.put(time, (int) stamp); previous = (int) stamp;
        }
        Span after = new Span(range.start, range.end, (int) (range.sourceStart + shift), (int) (range.sourceEnd + shift), range.variableSpeed);
        return new Plan(draft.before, after, draft.totalDuration, draft.outputTimes, sources, draft.linearize);
    }

    /** Rescale keys inside the range; ripple following keys by the same delta. */
    public Plan plan(Span span, int sourceStart, int sourceEnd, long duration, int recordingDuration) {
        if (span == null || span.duration() <= 0 || !timeline.isPositionKeyframe(span.start) || !timeline.isPositionKeyframe(span.end)) throw new IllegalArgumentException("Capture at least two shots and choose a camera transition.");
        if (duration < 10 || duration > Integer.MAX_VALUE) throw new IllegalArgumentException("Video duration must be at least 0.01 seconds and fit within the video clock.");
        if (sourceStart < 0 || sourceEnd < sourceStart || sourceEnd > recordingDuration) throw new IllegalArgumentException("Replay In and Out must be inside the recording, with Out at or after In. Equal times freeze the action.");
        long end = span.start + duration, shift = end - span.end;
        SortedSet<Long> times = new ShotModel(timeline).times();
        Map<Long, Long> outputs = new LinkedHashMap<>();
        long previous = -1;
        for (long time : times) {
            long target = time < span.start ? time : time > span.end ? time + shift : span.start + Math.round((time - span.start) * (double) duration / span.duration());
            if (target < 0 || target > Integer.MAX_VALUE) throw new IllegalArgumentException("This edit would move a shot outside the video clock.");
            if (target <= previous) throw new IllegalArgumentException("This duration would collapse camera or clock keys together. Use a longer duration.");
            outputs.put(time, target); previous = target;
        }
        Map<Long, Integer> sources = new TreeMap<>();
        for (Keyframe key : timeline.getTimePath().getKeyframes()) {
            long time = key.getTime(), target = outputs.get(time);
            int stamp = key.getValue(TimestampProperty.PROPERTY).orElseThrow(() -> new IllegalArgumentException("A clock key has no replay time."));
            if (time >= span.start && time <= span.end) stamp = sourceStart + (int) Math.round((target - span.start) * (double) (sourceEnd - sourceStart) / duration);
            sources.put(target, stamp);
        }
        sources.put(span.start, sourceStart); sources.put(end, sourceEnd);
        int lastSource = -1;
        for (int stamp : sources.values()) {
            if (stamp < lastSource || stamp < 0 || stamp > recordingDuration) throw new IllegalArgumentException("This replay range would run backward at a neighbouring shot. Adjust that neighbouring shot or use Whole sequence.");
            lastSource = stamp;
        }
        return new Plan(span, new Span(span.start, end, sourceStart, sourceEnd), outputs.get(times.last()), outputs, sources);
    }

    public void apply(Plan plan) {
        if (!new ArrayList<>(new ShotModel(timeline).times()).equals(new ArrayList<>(plan.outputTimes.keySet()))) throw new IllegalStateException("The camera path changed. Reopen Timing / speed and try again.");
        List<Long> moves = new ArrayList<>(plan.outputTimes.keySet());
        if (plan.shift > 0) Collections.reverse(moves);
        timeline.editAtomically(() -> {
            for (long from : moves) {
                long to = plan.outputTimes.get(from);
                if (from == to) continue;
                for (SPTimeline.SPPath path : SPTimeline.SPPath.values()) if (timeline.getKeyframe(path, from) != null) timeline.recordAppliedChange(timeline.moveKeyframe(path, from, to));
            }
            for (Map.Entry<Long, Integer> entry : plan.sourceTimes.entrySet()) {
                long time = entry.getKey();
                if (timeline.isTimeKeyframe(time)) {
                    if (!timeline.getTimePath().getKeyframe(time).getValue(TimestampProperty.PROPERTY).filter(entry.getValue()::equals).isPresent()) timeline.recordAppliedChange(timeline.updateTimeKeyframe(time, entry.getValue()));
                } else timeline.addTimeKeyframe(time, entry.getValue());
            }
            for (PathSegment segment : timeline.getTimePath().getSegments()) {
                if (!plan.linearize) continue;
                if (segment.getStartKeyframe().getTime() < plan.after.start || segment.getEndKeyframe().getTime() > plan.after.end) continue;
                LinearInterpolator linear = new LinearInterpolator(); linear.registerProperty(TimestampProperty.PROPERTY);
                Change change = SetInterpolator.create(segment, linear); change.apply(timeline.getTimeline()); timeline.recordAppliedChange(change);
            }
        });
        timeline.getTimeline().getPaths().forEach(path -> path.updateAll());
    }

    public long removeLeadingGap() {
        if (timeline.getPositionPath().getKeyframes().isEmpty()) throw new IllegalArgumentException("Capture a camera shot first.");
        long first = timeline.getPositionPath().getKeyframes().iterator().next().getTime();
        if (first == 0) return 0;
        timeline.getTimePath().updateAll();
        Integer source = timeline.getTimePath().getValue(TimestampProperty.PROPERTY, first).orElse(null);
        if (source == null) throw new IllegalArgumentException("Set a replay time on the first camera shot before removing the gap.");
        List<Long> times = new ArrayList<>(new ShotModel(timeline).times());
        timeline.editAtomically(() -> {
            if (!timeline.isTimeKeyframe(first) && source != null) timeline.addTimeKeyframe(first, source);
            for (Keyframe key : new ArrayList<>(timeline.getTimePath().getKeyframes())) if (key.getTime() < first) timeline.removeTimeKeyframe(key.getTime());
            if (!times.contains(first)) times.add(first);
            Collections.sort(times);
            for (long time : times) if (time >= first) {
                for (SPTimeline.SPPath path : SPTimeline.SPPath.values()) if (timeline.getKeyframe(path, time) != null) timeline.recordAppliedChange(timeline.moveKeyframe(path, time, time - first));
            }
        });
        timeline.getTimeline().getPaths().forEach(path -> path.updateAll());
        return first;
    }
}
