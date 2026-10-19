package com.replaymod.studio;

import org.junit.Test;
import static org.junit.Assert.*;

public class StudioViewportTest {
    @Test public void buttonAndTimelineHitCoordinatesFollowTheDrawnCanvasAcrossScales() {
        for (int[] size : new int[][]{{320, 180}, {640, 360}, {960, 540}, {1920, 1080}, {800, 600}, {3440, 1440}}) {
            StudioViewport viewport = new StudioViewport(size[0], size[1], 960, 540);
            for (int[] point : new int[][]{{80, 22}, {826, 90}, {500, 489}, {800, 220}, {150, 360}}) {
                int px = Math.round(viewport.x + point[0] * viewport.scale);
                int py = Math.round(viewport.y + point[1] * viewport.scale);
                assertEquals(point[0], viewport.logicalX(px), Math.ceil(1 / viewport.scale));
                assertEquals(point[1], viewport.logicalY(py), Math.ceil(1 / viewport.scale));
            }
        }
    }
    @Test public void minimisedWindowCannotProduceAnInfiniteInputScale() {
        StudioViewport viewport = new StudioViewport(0, 0, 960, 540);
        assertTrue(viewport.scale > 0); assertTrue(Float.isFinite(viewport.scale));
    }
    @Test public void fullWindowEditorFillsWideAndTallWindowsAndKeepsTimelineHitsAligned() {
        for (int[] size : new int[][]{{1920, 1080}, {3440, 1440}, {800, 600}, {1280, 1024}, {1080, 1920}, {320, 180}}) {
            StudioViewport viewport = new StudioViewport(size[0], size[1], 960, 580, true);
            StudioEditorLayout layout = new StudioEditorLayout(viewport.canvasWidth, viewport.canvasHeight);
            assertEquals(0, viewport.x); assertEquals(0, viewport.y);
            // Integer logical extents round by at most half a logical pixel.
            assertEquals(size[0], viewport.canvasWidth * viewport.scale, viewport.scale / 2 + 0.001);
            assertEquals(size[1], viewport.canvasHeight * viewport.scale, viewport.scale / 2 + 0.001);
            assertEquals(viewport.canvasWidth - 16, layout.right(708) + 236);
            assertEquals(viewport.canvasHeight - 64, layout.videoTop + 22);
            assertTrue(80 + (layout.shotRows - 1) * 22 + 20 < layout.bottom(290));
            int tick = TimelineGraphics.pixel(5000, 0, 10000, 94, layout.trackWidth);
            int actualX = Math.round(tick * viewport.scale), actualY = Math.round((layout.videoTop + 11) * viewport.scale);
            assertEquals(tick, viewport.logicalX(actualX), Math.ceil(1 / viewport.scale));
            assertEquals(layout.videoTop + 11, viewport.logicalY(actualY), Math.ceil(1 / viewport.scale));
            long time = TimelineGraphics.time(viewport.logicalX(actualX), 0, 10000, 94, layout.trackWidth);
            // Time first rounds to a logical tick, then a screen pixel; native
            // hit testing floors the inverse. Include both quantisation steps.
            double clockTolerance = Math.ceil(10000.0 / layout.trackWidth * (1.5 + 0.5 / viewport.scale)) + 1;
            assertEquals(5000, time, clockTolerance);
        }
    }
}
