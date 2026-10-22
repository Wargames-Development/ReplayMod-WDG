package com.replaymod.studio;

import org.junit.Test;
import static org.junit.Assert.*;

public class StudioHelpTest {
    @Test public void everyPrimaryWorkflowButtonHasSpecificHelp() {
        checkButtons("StudioAutoShot",new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,99});
        checkButtons("CinematicEditor",new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,30,31,34,35,36,37,38});
        checkButtons("StudioShotTiming",new int[]{1,2,3,10,11,12,13,14,15,20,21,22,23,24,30});
        checkButtons("StudioExport",new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,30,31,32});
    }
    private static void checkButtons(String screen,int[] ids) {
        for(int id:ids) {
            String title="Test";
            if(id==99 || screen.equals("StudioShotTiming") && id==20 || screen.equals("StudioExport") && id==13) title="Back to editor";
            if(screen.equals("StudioExport") && (id==3 || id==9)) title="Browse...";
            if(screen.equals("StudioExport") && id==14) title="Open output folder";
            String help=StudioHelp.button(screen,id,title);
            assertFalse(screen+" #"+id,help.startsWith("Use Test"));
            assertTrue(help.length()>20);
        }
    }
    @Test public void clockAndAppendHelpExplainTheTwoSeparateConstraints() {
        String append=StudioHelp.button("StudioAutoShot",6,"Append");
        assertTrue(append.contains("Video start only")); assertTrue(append.contains("Replay In must still"));
        assertTrue(StudioHelp.field("StudioAutoShot",0).contains("range must be empty"));
        assertTrue(StudioHelp.field("StudioAutoShot",2).contains("separate saved path"));
        assertTrue(StudioHelp.field("StudioAutoShot",3).contains("next shot"));
        assertTrue(StudioHelp.button("CinematicEditor",34,"Stop").contains("video time zero"));
        for(int i=0;i<16;i++) assertNotNull(StudioHelp.field("StudioAutoShot",i));
        for(int i=0;i<10;i++) assertNotNull(StudioHelp.field("CinematicEditor",i));
        assertTrue(StudioHelp.exportField(true,0).contains("FFmpeg"));
        assertTrue(StudioHelp.exportField(false,0).contains("width"));
    }
    @Test public void helpBoxesFitNearAllEdgesAtSupportedWindowShapes() {
        for(int[] size:new int[][]{{800,600},{1120,720},{1920,1080},{3440,1440}}) {
            StudioViewport viewport=new StudioViewport(size[0],size[1],1120,720,true);
            for(int x:new int[]{0,viewport.canvasWidth/2,viewport.canvasWidth-1})
                for(int y:new int[]{0,viewport.canvasHeight/2,viewport.canvasHeight-1}) {
                    int[] box=StudioHelp.box(x,y,316,234,viewport.canvasWidth,viewport.canvasHeight);
                    assertTrue(box[0]>=6); assertTrue(box[1]>=6);
                    assertTrue(box[0]+box[2]<=viewport.canvasWidth-6);
                    assertTrue(box[1]+box[3]<=viewport.canvasHeight-6);
                }
        }
    }
}
