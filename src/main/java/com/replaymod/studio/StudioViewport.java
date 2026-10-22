package com.replaymod.studio;

/** One mapping shared by drawing, clicks, dragging and wheel input at every GUI scale. */
public final class StudioViewport {
    public final float scale;
    public final int x, y;
    public final int left, top;
    public final int canvasWidth, canvasHeight;
    public StudioViewport(int screenWidth, int screenHeight, int canvasWidth, int canvasHeight) {
        this(screenWidth, screenHeight, canvasWidth, canvasHeight, false);
    }
    public StudioViewport(int screenWidth, int screenHeight, int canvasWidth, int canvasHeight, boolean fillWindow) {
        this(screenWidth, screenHeight, 0, 0, canvasWidth, canvasHeight, fillWindow);
    }
    /** Fit the actual dialog bounds, excluding the old decorative outer margin. */
    public StudioViewport(int screenWidth, int screenHeight, int left, int top, int canvasWidth, int canvasHeight, boolean fillWindow) {
        if (canvasWidth <= 0 || canvasHeight <= 0) throw new IllegalArgumentException("Canvas dimensions must be positive");
        this.left = left; this.top = top;
        screenWidth = Math.max(1, screenWidth); screenHeight = Math.max(1, screenHeight);
        scale = Math.min(screenWidth / (float) canvasWidth, screenHeight / (float) canvasHeight);
        this.canvasWidth = left + (fillWindow ? Math.max(canvasWidth, Math.round(screenWidth / scale)) : canvasWidth);
        this.canvasHeight = top + (fillWindow ? Math.max(canvasHeight, Math.round(screenHeight / scale)) : canvasHeight);
        x = (fillWindow ? 0 : (screenWidth - Math.round(canvasWidth * scale)) / 2) - Math.round(left * scale);
        y = (fillWindow ? 0 : (screenHeight - Math.round(canvasHeight * scale)) / 2) - Math.round(top * scale);
    }
    public int logicalX(int screenX) { return (int) Math.floor((screenX - x) / scale); }
    public int logicalY(int screenY) { return (int) Math.floor((screenY - y) / scale); }
}
