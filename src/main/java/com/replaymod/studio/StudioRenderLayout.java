package com.replaymod.studio;

/** Window-filling render status layout with an aspect-correct preview. */
public final class StudioRenderLayout {
    public final int width, height, previewTop = 88, previewHeight, progressTop, buttonTop;
    public StudioRenderLayout(int width, int height) {
        this.width = width; this.height = height;
        progressTop = height - 94; buttonTop = height - 52;
        previewHeight = progressTop - previewTop - 14;
    }
    public int[] preview(int frameWidth, int frameHeight) {
        double scale = Math.min((width - 64.0) / Math.max(1, frameWidth), previewHeight / (double) Math.max(1, frameHeight));
        int w = Math.max(1, (int) (frameWidth * scale)), h = Math.max(1, (int) (frameHeight * scale));
        return new int[]{(width - w) / 2, previewTop + (previewHeight - h) / 2, w, h};
    }
}
