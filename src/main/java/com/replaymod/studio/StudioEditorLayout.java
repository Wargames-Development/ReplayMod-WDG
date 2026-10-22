package com.replaymod.studio;

/** Dock geometry shared by rendering, native controls and splitter hit tests. */
public final class StudioEditorLayout {
    public static final int MIN_WIDTH = 1120, MIN_HEIGHT = 720, HEADER = 40, TRACK_LEFT = 94;
    public static final int TRACK_HEIGHT = 46;
    public final int width, height, libraryWidth, inspectorLeft, inspectorWidth, previewLeft, timelineTop;
    public final int trackWidth, replayTop, videoTop, shotRows, laneBottom, panTop;
    public StudioEditorLayout(int width, int height) { this(width, height, .17, .23, .31); }
    public StudioEditorLayout(int width, int height, double library, double inspector, double timeline) {
        this.width = Math.max(MIN_WIDTH, width); this.height = Math.max(MIN_HEIGHT, height);
        libraryWidth = clamp((int) Math.round(this.width * finite(library, .17)), 160, this.width - 680);
        inspectorLeft = libraryWidth + 4;
        inspectorWidth = clamp((int) Math.round(this.width * finite(inspector, .23)), 240, this.width - inspectorLeft - 424);
        previewLeft = inspectorLeft + inspectorWidth + 4;
        timelineTop = clamp((int) Math.round(this.height * (1 - finite(timeline, .31))), 444, this.height - 224);
        trackWidth = this.width - TRACK_LEFT - 16;
        replayTop = timelineTop + 48; videoTop = timelineTop + 118;
        laneBottom = this.height - 48; panTop = this.height - 34;
        shotRows = Math.max(1, (timelineTop - 224) / 26);
    }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public int[] preview(boolean fullscreen, double aspect) {
        int[] area = viewerArea(fullscreen);
        int left = area[0], top = area[1], availableWidth = area[2], availableHeight = area[3];
        if (!Double.isFinite(aspect) || aspect <= 0) aspect = 16.0 / 9;
        int w = Math.max(1, Math.min(availableWidth, (int) Math.floor(availableHeight * aspect)));
        int h = Math.max(1, Math.min(availableHeight, (int) Math.floor(w / aspect)));
        return new int[]{left + (availableWidth - w) / 2, top + (availableHeight - h) / 2, w, h};
    }
    public int splitter(int x, int y) {
        if (Math.abs(y - timelineTop) <= 4) return 3;
        if (y < HEADER || y > timelineTop) return 0;
        if (Math.abs(x - libraryWidth) <= 4) return 1;
        if (Math.abs(x - (previewLeft - 4)) <= 4) return 2;
        return 0;
    }
    public int[] viewerArea(boolean fullscreen) {
        return fullscreen ? new int[]{0, 0, width, height} : new int[]{previewLeft, 72, width - previewLeft, timelineTop - 72};
    }
}
