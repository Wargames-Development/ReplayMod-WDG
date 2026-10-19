package com.replaymod.studio;

/** Edge anchors expand the editor without stretching fonts or control shapes. */
public final class StudioEditorLayout {
    public static final int MIN_WIDTH = 960, MIN_HEIGHT = 580;
    public final int extraWidth, extraHeight, trackWidth, replayTop, videoTop, shotRows;
    public StudioEditorLayout(int width, int height) {
        extraWidth = Math.max(0, width - MIN_WIDTH);
        extraHeight = Math.max(0, height - MIN_HEIGHT);
        trackWidth = 832 + extraWidth;
        replayTop = bottom(438); videoTop = bottom(494);
        shotRows = 9 + extraHeight / 22;
    }
    public int right(int x) { return x + extraWidth; }
    public int center(int x) { return x + extraWidth / 2; }
    public int bottom(int y) { return y + extraHeight; }
}
