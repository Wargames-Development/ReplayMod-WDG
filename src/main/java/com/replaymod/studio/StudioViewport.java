package com.replaymod.studio;

/** One mapping shared by drawing, clicks, dragging and wheel input at every GUI scale. */
public final class StudioViewport {
    public final float scale;
    public final int x, y;
    public final int canvasWidth, canvasHeight;
    public StudioViewport(int screenWidth, int screenHeight, int canvasWidth, int canvasHeight) {
        this(screenWidth, screenHeight, canvasWidth, canvasHeight, false);
    }
    public StudioViewport(int screenWidth, int screenHeight, int canvasWidth, int canvasHeight, boolean fillWindow) {
        if (canvasWidth <= 0 || canvasHeight <= 0) throw new IllegalArgumentException("Canvas dimensions must be positive");
        screenWidth = Math.max(1, screenWidth); screenHeight = Math.max(1, screenHeight);
        scale = Math.min(screenWidth / (float) canvasWidth, screenHeight / (float) canvasHeight);
        this.canvasWidth = fillWindow ? Math.max(canvasWidth, Math.round(screenWidth / scale)) : canvasWidth;
        this.canvasHeight = fillWindow ? Math.max(canvasHeight, Math.round(screenHeight / scale)) : canvasHeight;
        x = fillWindow ? 0 : (screenWidth - Math.round(canvasWidth * scale)) / 2;
        y = fillWindow ? 0 : (screenHeight - Math.round(canvasHeight * scale)) / 2;
    }
    public int logicalX(int screenX) { return (int) Math.floor((screenX - x) / scale); }
    public int logicalY(int screenY) { return (int) Math.floor((screenY - y) / scale); }
}
