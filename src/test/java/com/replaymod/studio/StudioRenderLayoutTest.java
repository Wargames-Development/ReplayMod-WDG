package com.replaymod.studio;

import org.junit.Test;
import static org.junit.Assert.*;

public class StudioRenderLayoutTest {
    @Test public void renderingFillsTheWindowAndFitsDifferentFrameShapesWithoutCoveringControls() {
        for (int[] window : new int[][]{{1920, 1080}, {3440, 1440}, {800, 600}, {1080, 1920}, {320, 180}}) {
            StudioViewport viewport = new StudioViewport(window[0], window[1], 960, 540, true);
            StudioRenderLayout layout = new StudioRenderLayout(viewport.canvasWidth, viewport.canvasHeight);
            assertEquals(0, viewport.x); assertEquals(0, viewport.y);
            assertTrue((layout.width + 1) * viewport.scale >= window[0]);
            assertTrue((layout.height + 1) * viewport.scale >= window[1]);
            for (int[] frame : new int[][]{{1920, 1080}, {1080, 1920}, {2048, 2048}, {3840, 1080}}) {
                int[] box = layout.preview(frame[0], frame[1]);
                assertTrue(box[0] >= 32); assertTrue(box[0] + box[2] <= layout.width - 32);
                assertTrue(box[1] >= layout.previewTop); assertTrue(box[1] + box[3] < layout.progressTop);
                assertEquals(frame[0] / (double) frame[1], box[2] / (double) box[3], 0.02);
            }
            assertTrue(layout.progressTop + 20 < layout.buttonTop);
            assertTrue(layout.buttonTop + 20 < layout.height - 16);
        }
    }
}
