package com.replaymod.studio;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class StudioWorkspaceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void docksKeepControlsAndViewerSeparateAtEveryWindowShape() {
        for (int[] window : new int[][]{{1120,720}, {1920,1080}, {3440,1440}, {1080,1920}}) {
            StudioViewport viewport = new StudioViewport(window[0], window[1], 1120, 720, true);
            for (double[] split : new double[][]{{.17,.23,.31}, {-.5,9,9}, {9,-.5,-.5}, {Double.NaN,Double.POSITIVE_INFINITY,Double.NaN}}) {
                StudioEditorLayout layout = new StudioEditorLayout(viewport.canvasWidth, viewport.canvasHeight, split[0],split[1],split[2]);
                assertTrue(layout.libraryWidth >= 160);
                assertTrue(layout.inspectorWidth >= 240);
                assertTrue(layout.previewLeft + 420 <= layout.width);
                assertTrue(layout.timelineTop >= 444);
                assertTrue(layout.videoTop + 54 <= layout.laneBottom);
                assertTrue(layout.laneBottom < layout.panTop);
                assertEquals(1, layout.splitter(layout.libraryWidth, 100));
                assertEquals(2, layout.splitter(layout.previewLeft - 4, 100));
                assertEquals(3, layout.splitter(600, layout.timelineTop));
                for (double aspect : new double[]{16.0/9, 4.0/3, 9.0/16, Double.NaN}) {
                    for (boolean full : new boolean[]{true,false}) {
                        int[] image = layout.preview(full, aspect);
                        assertTrue(image[0] >= (full ? 0 : layout.previewLeft));
                        assertTrue(image[1] >= (full ? 0 : 72));
                        assertTrue(image[0]+image[2] <= layout.width);
                        assertTrue(image[1]+image[3] <= (full ? layout.height : layout.timelineTop));
                        assertTrue(image[2]>0 && image[3]>0);
                        assertEquals(Double.isFinite(aspect) ? aspect : 16.0/9, image[2]/(double)image[3], .04);
                    }
                }
            }
        }
    }
    @Test public void draggingNavigationUsesGrabDirectionAndRespectsRecordingBounds() {
        assertEquals(7000, StudioTimelineView.pan(5000,10000,-200,1000,60000));
        assertEquals(3000, StudioTimelineView.pan(5000,10000,200,1000,60000));
        assertEquals(0, StudioTimelineView.pan(5000,10000,900,1000,60000));
        assertEquals(50000, StudioTimelineView.pan(5000,10000,-9000,1000,60000));
        assertEquals(0, StudioTimelineView.pan(0,60000,-500,1000,60000));
    }
    @Test public void filledViewerTouchesEveryPanelEdgeAndCropsWithoutDistorting() {
        StudioEditorLayout layout=new StudioEditorLayout(1920,1080);
        assertArrayEquals(new int[]{layout.previewLeft,72,1920-layout.previewLeft,layout.timelineTop-72},layout.viewerArea(false));
        assertArrayEquals(new int[]{0,0,1920,1080},layout.viewerArea(true));
        assertTrue(new StudioWorkspacePreferences().fillViewer);
        float[] wide=StudioViewerGeometry.crop(16.0/9,2000,500);
        assertEquals(0,wide[0],.0001); assertEquals(1,wide[2],.0001); assertTrue(wide[1]>0 && wide[3]<1);
        assertEquals(4,16.0/9*(wide[2]-wide[0])/(wide[3]-wide[1]),.001);
        float[] tall=StudioViewerGeometry.crop(16.0/9,500,1000);
        assertEquals(0,tall[1],.0001); assertEquals(1,tall[3],.0001); assertTrue(tall[0]>0 && tall[2]<1);
        assertEquals(.5,16.0/9*(tall[2]-tall[0])/(tall[3]-tall[1]),.001);
    }
    @Test public void zoomKeepsTheTimeUnderTheMouseStationary() {
        long next = StudioTimelineView.zoomDuration(10000,true,60000);
        long start = StudioTimelineView.zoomStart(5000,10000,next,.75,60000);
        assertEquals(12500, start+Math.round(next*.75));
        assertEquals(100, StudioTimelineView.zoomDuration(100,true,60000));
        assertEquals(60000, StudioTimelineView.zoomDuration(60000,false,60000));
        assertEquals(1, StudioTimelineView.zoomDuration(1,true,1));
    }
    @Test public void steppingFramesDoesNotAccumulateRoundedMillisecondDrift() {
        for (int fps : new int[]{24,30,60,120,240}) {
            long time=0;
            for (int i=0;i<fps*10;i++) time=StudioTimelineView.stepFrame(time,fps,1);
            assertEquals(10000,time);
            for (int i=0;i<fps*10;i++) time=StudioTimelineView.stepFrame(time,fps,-1);
            assertEquals(0,time);
            assertEquals(0,StudioTimelineView.stepFrame(0,fps,-1));
        }
    }
    @Test public void resizingPreferencesSurviveReopeningAndInvalidFilesUseDefaults() throws Exception {
        File file=new File(temporary.getRoot(),"config/replaymod-workspace.json");
        StudioWorkspacePreferences preferences=new StudioWorkspacePreferences();
        preferences.library=.22; preferences.inspector=.27; preferences.timeline=.36;
        preferences.save(file);
        StudioWorkspacePreferences reopened=StudioWorkspacePreferences.load(file);
        assertEquals(.22,reopened.library,0); assertEquals(.27,reopened.inspector,0); assertEquals(.36,reopened.timeline,0);
        Files.write(file.toPath(),"broken json".getBytes(StandardCharsets.UTF_8));
        assertEquals(.17,StudioWorkspacePreferences.load(file).library,0);
        preferences.library=Double.NaN; preferences.inspector=Double.POSITIVE_INFINITY;
        preferences.save(file);
        assertEquals(.17,StudioWorkspacePreferences.load(file).library,0);
        assertEquals(.23,StudioWorkspacePreferences.load(file).inspector,0);
    }
}
