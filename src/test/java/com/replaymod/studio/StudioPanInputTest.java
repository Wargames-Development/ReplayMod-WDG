package com.replaymod.studio;

import net.minecraft.client.Minecraft;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.ArrayList;
import com.replaymod.replay.EditorSeek;
import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.InterpolatorType;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.gui.GuiPathing;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModMetadata;
import static org.junit.Assert.*;

/** Exercises the native screen-to-canvas input path without starting a game or GL context. */
public class StudioPanInputTest {
    private static Object previousLoader;
    @org.junit.BeforeClass public static void headlessForgeEventOwner() throws Exception {
        previousLoader=get(null,Loader.class,"instance");
        Loader loader=allocate(Loader.class); LoadController controller=allocate(LoadController.class);
        ModMetadata metadata=new ModMetadata(); metadata.modId="replaymod_test"; metadata.name="Replay input test";
        set(controller,LoadController.class,"activeContainer",new DummyModContainer(metadata));
        set(loader,Loader.class,"modController",controller); set(null,Loader.class,"instance",loader);
    }
    @org.junit.AfterClass public static void restoreForgeEventOwner() throws Exception { set(null,Loader.class,"instance",previousLoader); }
    @Test public void greenRangeGrabsAfterScrubbingAndTakesOwnershipFromTheUnfinishedSeek() throws Exception {
        for(int[] window:new int[][]{{1120,720},{1920,1080},{3440,1440}}) {
            CinematicEditor editor=editor(window);
            StudioViewport viewport=(StudioViewport)get(editor,StudioScreen.class,"viewport");
            StudioEditorLayout layout=(StudioEditorLayout)get(editor,CinematicEditor.class,"layout");
            SPTimeline timeline=new SPTimeline(); timeline.setDefaultInterpolatorType(InterpolatorType.CATMULL_ROM);
            ShotModel shots=new ShotModel(timeline); shots.save(-1,0,20000,0,80,0,0,0,0); shots.save(-1,4000,24000,4,80,0,90,0,0);
            ReplayModSimplePathing mod=allocate(ReplayModSimplePathing.class); set(mod,ReplayModSimplePathing.class,"currentTimeline",timeline);
            ReplayHandler replay=allocate(ReplayHandler.class); set(replay,ReplayHandler.class,"replayDuration",60000);
            set(replay,ReplayHandler.class,"fullReplaySender",allocate(Sender.class));
            SeekBackend backend=new SeekBackend(); EditorSeek seek=new EditorSeek(backend); seek.request(45000,false,0); seek.tick(0);
            set(replay,ReplayHandler.class,"editorSeek",seek);
            set(editor,CinematicEditor.class,"mod",mod); set(editor,CinematicEditor.class,"replay",replay);
            set(editor,CinematicEditor.class,"pathing",allocate(Pathing.class));
            set(editor,CinematicEditor.class,"sourceStart",0L); set(editor,CinematicEditor.class,"sourceDuration",60000L);
            set(editor,CinematicEditor.class,"timingSelection",0L);
            int ruler=layout.replayTop+5;
            editor.mouseClicked(pixel(700,viewport),pixel(ruler,viewport),0);
            editor.mouseClickMove(pixel(800,viewport),pixel(ruler,viewport),0,10);
            editor.mouseReleased(pixel(800,viewport),pixel(ruler,viewport),0);
            assertTrue((long)get(editor,CinematicEditor.class,"pendingSeek")>24000);
            int from=TimelineGraphics.pixel(22000,0,60000,StudioEditorLayout.TRACK_LEFT,layout.trackWidth),to=from+80,y=layout.replayTop+30;
            editor.mouseClicked(pixel(from,viewport),pixel(y,viewport),0);
            assertEquals(editor.message,2,get(editor,CinematicEditor.class,"rangeHandle"));
            assertFalse(seek.pending()); assertEquals(1,backend.releases);
            assertEquals(-1L,get(editor,CinematicEditor.class,"pendingSeek"));
            editor.mouseReleased(pixel(from,viewport),pixel(y,viewport),0);
            assertEquals(-1L,get(editor,CinematicEditor.class,"pendingSeek"));
            assertEquals(-1,get(editor,CinematicEditor.class,"rangeHandle"));
            editor.mouseClicked(pixel(from,viewport),pixel(y,viewport),0);
            editor.mouseClickMove(pixel(to,viewport),pixel(y,viewport),0,10);
            int actualFrom=viewport.logicalX(pixel(from,viewport)),actualTo=viewport.logicalX(pixel(to,viewport));
            long shift=TimelineGraphics.time(actualTo,0,60000,StudioEditorLayout.TRACK_LEFT,layout.trackWidth)-TimelineGraphics.time(actualFrom,0,60000,StudioEditorLayout.TRACK_LEFT,layout.trackWidth);
            assertEquals(20000+shift,((Number)get(editor,CinematicEditor.class,"rangeFrom")).longValue());
            assertEquals(4000,(int)get(editor,CinematicEditor.class,"rangeTo")-(int)get(editor,CinematicEditor.class,"rangeFrom"));
            assertEquals(4000,new ShotTiming(timeline).whole().duration());
        }
    }
    @Test public void shortVisibleRangesKeepABodyGripBetweenTheirEndGrips() {
        for(int width:new int[]{4,8,12,20,40}) {
            assertEquals(2,TimelineGraphics.rangeHandle(100+width/2,100,100+width,false));
            assertEquals(0,TimelineGraphics.rangeHandle(100,100,100+width,false));
            assertEquals(1,TimelineGraphics.rangeHandle(100+width,100,100+width,false));
        }
    }
    @Test public void leftDragInTheEmptyCameraLanePansWithoutTouchingShotsOrThePlayhead() throws Exception {
        for (int[] window : new int[][]{{1120,720},{1920,1080},{3440,1440}}) {
            CinematicEditor editor=editor(window);
            StudioViewport mapping=(StudioViewport)get(editor,StudioScreen.class,"viewport");
            StudioEditorLayout layout=(StudioEditorLayout)get(editor,CinematicEditor.class,"layout");
            int y=layout.videoTop+StudioEditorLayout.TRACK_HEIGHT+8, from=700,to=500;
            editor.mouseClicked(pixel(from,mapping),pixel(y,mapping),0);
            editor.mouseClickMove(pixel(to,mapping),pixel(y,mapping),0,100);
            editor.mouseReleased(pixel(to,mapping),pixel(y,mapping),0);
            assertEquals(StudioTimelineView.pan(5000,10000, mapping.logicalX(pixel(to,mapping))-mapping.logicalX(pixel(from,mapping)),layout.trackWidth,Integer.MAX_VALUE),get(editor,CinematicEditor.class,"visibleStart"));
            assertEquals(7500L,get(editor,CinematicEditor.class,"cursor"));
            assertEquals(6000L,get(editor,CinematicEditor.class,"selected"));
            assertEquals(1000L,get(editor,CinematicEditor.class,"sourceStart"));
            assertEquals(-1L,get(editor,CinematicEditor.class,"pendingSeek"));
            assertEquals(0,get(editor,CinematicEditor.class,"panTrack"));
            assertEquals(false,get(editor,CinematicEditor.class,"followPlayhead"));
        }
    }
    @Test public void middleDragOnRulerPansInsteadOfSeeking() throws Exception {
        CinematicEditor editor=editor(new int[]{1120,720});
        StudioEditorLayout layout=(StudioEditorLayout)get(editor,CinematicEditor.class,"layout");
        editor.mouseClicked(500,layout.videoTop+4,2);
        editor.mouseClickMove(700,layout.videoTop+4,2,100);
        editor.mouseReleased(700,layout.videoTop+4,2);
        assertEquals(StudioTimelineView.pan(5000,10000,200,layout.trackWidth,Integer.MAX_VALUE),get(editor,CinematicEditor.class,"visibleStart"));
        assertEquals(7500L,get(editor,CinematicEditor.class,"cursor"));
        assertEquals(-1L,get(editor,CinematicEditor.class,"pendingSeek"));
    }
    private static int pixel(int logical,StudioViewport viewport) { return Math.round(logical*viewport.scale); }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
    private static class Sender extends FullReplaySender {
        Sender() throws java.io.IOException { super(null,null,false); }
        @Override public int currentTimeStamp() { return 20000; }
    }
    private static class Pathing extends GuiPathing {
        Pathing() { super(null,null,null); }
        @Override public void stopPreview() { }
    }
    private static class SeekBackend implements EditorSeek.Backend {
        int releases;
        public void begin() { }
        public int position() { return 0; }
        public boolean advance(int target,long budget) { return false; }
        public void finish() { }
        public void release() { releases++; }
    }
    private static CinematicEditor editor(int[] window) throws Exception {
        Field allocator=Unsafe.class.getDeclaredField("theUnsafe"); allocator.setAccessible(true);
        Unsafe unsafe=(Unsafe)allocator.get(null);
        CinematicEditor editor=(CinematicEditor)unsafe.allocateInstance(CinematicEditor.class);
        Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
        mc.currentScreen=editor; editor.mc=mc;
        StudioViewport viewport=new StudioViewport(window[0],window[1],1120,720,true);
        set(editor,StudioScreen.class,"fields",new ArrayList<>());
        set(editor,net.minecraft.client.gui.GuiScreen.class,"buttonList",new ArrayList<>());
        set(editor,StudioScreen.class,"viewport",viewport);
        set(editor,CinematicEditor.class,"layout",new StudioEditorLayout(viewport.canvasWidth,viewport.canvasHeight));
        set(editor,CinematicEditor.class,"visibleStart",5000L); set(editor,CinematicEditor.class,"visibleDuration",10000L);
        set(editor,CinematicEditor.class,"cursor",7500L); set(editor,CinematicEditor.class,"selected",6000L);
        set(editor,CinematicEditor.class,"sourceStart",1000L); set(editor,CinematicEditor.class,"pendingSeek",-1L);
        set(editor,CinematicEditor.class,"rangeHandle",-1); set(editor,CinematicEditor.class,"dragKey",-1L);
        set(editor,CinematicEditor.class,"followPlayhead",true);
        return editor;
    }
    private static Object get(Object object,Class<?> owner,String name) throws Exception { Field field=owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void set(Object object,Class<?> owner,String name,Object value) throws Exception { Field field=owner.getDeclaredField(name); field.setAccessible(true); field.set(object,value); }
}
