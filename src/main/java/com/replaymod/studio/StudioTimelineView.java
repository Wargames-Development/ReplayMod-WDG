package com.replaymod.studio;

/** Navigation changes the visible clock only; it never edits shots or the playhead. */
public final class StudioTimelineView {
    private StudioTimelineView() { }
    public static long pan(long start, long duration, int pixels, int width, long limit) {
        return bound(start - Math.round(pixels * (double) duration / Math.max(1, width)), duration, limit);
    }
    public static long bound(long start, long duration, long limit) { return Math.max(0, Math.min(Math.max(0, limit - duration), start)); }
    public static long zoomDuration(long duration, boolean in, long limit) { return Math.max(Math.min(100, limit), Math.min(limit, Math.round(duration * (in ? .8 : 1.25)))); }
    public static long zoomStart(long start, long before, long after, double fraction, long limit) {
        return bound(Math.round(start + before * fraction - after * fraction), after, limit);
    }
    public static long stepFrame(long time, int fps, int direction) {
        long frame = Math.round(time * (double) fps / 1000) + direction;
        return Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(frame * 1000.0 / Math.max(1, fps))));
    }
}
