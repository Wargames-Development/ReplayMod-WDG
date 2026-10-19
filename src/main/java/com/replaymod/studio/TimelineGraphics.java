package com.replaymod.studio;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** Shared range graphics. The same clock-to-pixel mapping is used for hit tests. */
public final class TimelineGraphics {
    public static final int RANGE = 0xFFE46B67;
    private TimelineGraphics() { }
    public static int pixel(long time, long start, long duration, int left, int width) {
        double fraction = (time - (double) start) / Math.max(1, duration);
        return left + (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
    }
    public static long time(int x, long start, long duration, int left, int width) {
        double fraction = Math.max(0, Math.min(1, (x - left) / (double) Math.max(1, width)));
        return Math.max(0, Math.min(Integer.MAX_VALUE, start + Math.round(fraction * duration)));
    }
    /** 0/1 resize the ends, 2 slides both ends, -1 is outside the selection. */
    public static int rangeHandle(int x, int from, int to, boolean shift) {
        boolean endpoint = Math.abs(x - from) <= 6 || Math.abs(x - to) <= 6;
        if (!endpoint && (x <= from || x >= to)) return -1;
        if (shift || !endpoint) return 2;
        if (from == to) return 1;
        return Math.abs(x - from) <= Math.abs(x - to) ? 0 : 1;
    }
    public static void range(int from, int to, int top, int bottom, int color) {
        Gui.drawRect(Math.min(from, to), top, Math.max(from, to) + 1, bottom, color);
        Gui.drawRect(from - 2, top - 2, from + 2, bottom + 2, RANGE);
        Gui.drawRect(to - 2, top - 2, to + 2, bottom + 2, RANGE);
    }
    public static void connect(int sourceIn, int sourceOut, int sourceY, int videoIn, int videoOut, int videoY) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2);
        try {
            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_LINES); t.setColorOpaque_I(RANGE & 0xFFFFFF);
            t.addVertex(sourceIn, sourceY, 0); t.addVertex(videoIn, videoY, 0);
            t.addVertex(sourceOut, sourceY, 0); t.addVertex(videoOut, videoY, 0);
            t.draw();
        } finally { GL11.glLineWidth(1); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1, 1, 1, 1); }
    }
}
