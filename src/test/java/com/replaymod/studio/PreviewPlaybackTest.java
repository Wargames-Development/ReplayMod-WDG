package com.replaymod.studio;

import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.utils.WrappedTimer;
import com.replaymod.pathing.player.AbstractTimelinePlayer;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplaySender;
import com.replaymod.replaystudio.pathing.impl.TimelineImpl;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.simplepathing.gui.GuiKeyframeTimeline;
import com.replaymod.simplepathing.gui.GuiPathing;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.InterpolatorType;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import cpw.mods.fml.common.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.Timer;
import org.junit.*;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.FutureTask;
import static org.junit.Assert.*;

/** Runs the actual player lifecycle with a controlled clock and a headless timer host. */
public class PreviewPlaybackTest {
    private static Object previousLoader;
    @BeforeClass public static void eventOwner() throws Exception {
        previousLoader=get(null,Loader.class,"instance");
        Loader loader=allocate(Loader.class); LoadController controller=allocate(LoadController.class);
        ModMetadata metadata=new ModMetadata(); metadata.modId="replaymod_test"; metadata.name="Playback test";
        set(controller,LoadController.class,"activeContainer",new DummyModContainer(metadata));
        set(loader,Loader.class,"modController",controller); set(null,Loader.class,"instance",loader);
    }
    @AfterClass public static void restoreOwner() throws Exception { set(null,Loader.class,"instance",previousLoader); }

    @Test public void stopRewindsTheVideoAndReplayClocksAndAllowsRepeatedPlayback() throws Exception {
        Fixture f=new Fixture();
        CinematicEditor editor=allocate(CinematicEditor.class);
        Controls controls=allocate(Controls.class); controls.player=f.player;
        GuiKeyframeTimeline playhead=allocate(GuiKeyframeTimeline.class);
        set(controls,GuiPathing.class,"timeline",playhead);
        SPTimeline path=new SPTimeline(); path.setDefaultInterpolatorType(InterpolatorType.LINEAR);
        new ShotModel(path).save(-1,0,10000,0,80,0,0,0,0);
        new ShotModel(path).save(-1,6000,16000,6,80,0,0,0,0);
        ReplayModSimplePathing mod=allocate(ReplayModSimplePathing.class);
        set(mod,ReplayModSimplePathing.class,"currentTimeline",path);
        set(editor,CinematicEditor.class,"mod",mod); set(editor,CinematicEditor.class,"pathing",controls);
        set(editor,CinematicEditor.class,"replay",f.handler);
        editor.mc=f.host; // No current screen; layout updates are irrelevant here.
        Font font=allocate(Font.class);
        GuiTextField output=new GuiTextField(font,0,0,120,18),source=new GuiTextField(font,0,0,120,18);
        set(editor,CinematicEditor.class,"output",output); set(editor,CinematicEditor.class,"source",source);
        Object previous=get(null,CinematicEditor.class,"previewWorkspace");
        try {
            for(int state=0;state<3;state++) {
                f.player.start(f.timeline,0); f.tick(state==2?6000:2400);
                if(state==1) f.player.getFuture().cancel(false); // Stop also works after Pause.
                set(editor,CinematicEditor.class,"cursor",f.player.getPlaybackPosition());
                set(editor,CinematicEditor.class,"selected",-1L);
                set(editor,CinematicEditor.class,"previewEnd",6000L);
                set(editor,CinematicEditor.class,"visibleStart",3000L);
                set(null,CinematicEditor.class,"previewWorkspace",editor);
                editor.activate(34);
                assertFalse(f.player.isActive()); assertSame(f.original,f.host.timer);
                assertEquals(0L,get(editor,CinematicEditor.class,"cursor")); assertEquals(0,playhead.getCursorPosition());
                assertEquals("0:00.000",output.getText()); assertEquals("0:10.000",source.getText());
                assertEquals(10000L,get(editor,CinematicEditor.class,"pendingSeek"));
                assertEquals(true,get(editor,CinematicEditor.class,"pendingTimelineSeek"));
                assertEquals(0L,get(editor,CinematicEditor.class,"visibleStart"));
                assertEquals(-1L,get(editor,CinematicEditor.class,"previewEnd"));
                assertNull(get(null,CinematicEditor.class,"previewWorkspace"));
                // The same cursor is used by the Play control to restart the player.
                f.player.start(f.timeline,playhead.getCursorPosition()); f.tick(50);
                assertEquals(50,f.player.getPlaybackPosition()); f.player.getFuture().cancel(false);
            }
        } finally { set(null,CinematicEditor.class,"previewWorkspace",previous); }
    }

    private static class Controls extends GuiPathing {
        Player player;
        Controls() { super(null,null,null); }
        @Override public void stopPreview() { if(player.isActive()) player.getFuture().cancel(false); }
    }
    private static class Font extends FontRenderer {
        Font() { super(null,null,null,false); }
        @Override public String trimStringToWidth(String text,int width) { return text; }
        @Override public String trimStringToWidth(String text,int width,boolean reverse) { return text; }
    }

    @Test public void finishedShotReplaysRepeatedlyWithoutNestedTimersOrStalePlayheads() throws Exception {
        Fixture f=new Fixture();
        for(int run=0;run<4;run++) {
            ListenableFuture<Void> future=f.player.start(f.timeline,0);
            assertSame(f.original,((ReplayTimer)f.host.timer).getWrapped());
            f.tick(5980); assertEquals(5980,f.player.getPlaybackPosition());
            f.tick(6025);
            assertTrue(future.isDone()); assertFalse(f.player.isActive());
            assertEquals(6000,f.player.getPlaybackPosition());
            assertSame("Completion must restore the timer without another tick",f.original,f.host.timer);
            assertEquals(0,f.player.registrations);
            f.tick(15000); assertSame(f.original,f.host.timer);
            assertEquals(6000,f.player.getPlaybackPosition());
        }
    }
    @Test public void pauseReleasesTheTimerImmediatelyAndResumeKeepsTheAppliedFrame() throws Exception {
        Fixture f=new Fixture(); ListenableFuture<Void> future=f.player.start(f.timeline,0);
        f.tick(2400); future.cancel(false);
        assertSame(f.original,f.host.timer); assertEquals(2400,f.player.getPlaybackPosition());
        assertEquals(0,f.player.registrations);
        f.player.start(f.timeline,2400); f.tick(2450);
        assertEquals(2450,f.player.getPlaybackPosition()); assertTrue(f.player.isActive());
        f.tick(6000); assertSame(f.original,f.host.timer);
    }
    @Test public void completionCallbacksCanStartAnotherRunBeforeTheNextTimerTick() throws Exception {
        Fixture f=new Fixture(); ListenableFuture<Void> first=f.player.start(f.timeline,0);
        first.addListener(() -> {
            assertSame(f.original,f.host.timer);
            f.player.start(f.timeline,0);
        },Runnable::run);
        f.tick(6000);
        assertTrue(f.player.isActive()); assertEquals(1,f.player.registrations);
        assertSame(f.original,((ReplayTimer)f.host.timer).getWrapped());
        f.tick(1000); assertEquals(1000,f.player.getPlaybackPosition());
        f.player.getFuture().cancel(false); assertSame(f.original,f.host.timer);
    }
    @Test public void finalFrameIsPublishedToTheEditorEvenWhenItMissedTheLastActiveTick() throws Exception {
        CinematicEditor editor=allocate(CinematicEditor.class); GuiPathing pathing=allocate(GuiPathing.class);
        GuiKeyframeTimeline timeline=allocate(GuiKeyframeTimeline.class);
        set(pathing,GuiPathing.class,"timeline",timeline); set(editor,CinematicEditor.class,"pathing",pathing);
        set(editor,CinematicEditor.class,"cursor",5980L); set(editor,CinematicEditor.class,"previewEnd",6000L);
        Object previous=get(null,CinematicEditor.class,"previewWorkspace");
        try {
            set(null,CinematicEditor.class,"previewWorkspace",editor);
            CinematicEditor.previewFinished(pathing,6000);
            assertEquals(6000L,get(editor,CinematicEditor.class,"cursor"));
            assertEquals(6000,timeline.getCursorPosition()); assertEquals(-1L,get(editor,CinematicEditor.class,"previewEnd"));
        } finally { set(null,CinematicEditor.class,"previewWorkspace",previous); }
    }
    private static class Fixture {
        Host host=allocate(Host.class); Timer original=allocate(Timer.class);
        Handler handler=allocate(Handler.class); Player player;
        Timeline timeline=new TimelineImpl();
        Fixture() throws Exception {
            host.timer=original;
            handler.sender=(ReplaySender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ReplaySender.class},(proxy,method,args)-> {
                if(method.getName().equals("isAsyncMode")) return false;
                if(method.getName().equals("currentTimeStamp")) return 10000;
                return null;
            });
            player=new Player(handler); set(player,AbstractTimelinePlayer.class,"mc",host);
            Path path=timeline.createPath(); path.insert(0); path.insert(6000); path.setActive(false);
        }
        void tick(long time) throws Exception {
            player.time=time;
            // Network initialization is tested separately; these tests exercise subsequent frames.
            set(player,AbstractTimelinePlayer.class,"firstFrame",false); player.onTick();
        }
    }
    private static class Player extends AbstractTimelinePlayer {
        long time; int registrations;
        Player(ReplayHandler handler) { super(handler); }
        @Override public long getTimePassed() { return time; }
        @Override public void register() { registrations++; }
        @Override public void unregister() { registrations--; }
        @Override protected ReplayTimer createPlaybackTimer(Timer wrapped) {
            try {
                ReplayTimer timer=allocate(ReplayTimer.class); set(timer,WrappedTimer.class,"wrapped",wrapped); return timer;
            } catch(Exception error) { throw new AssertionError(error); }
        }
    }
    private static class Handler extends ReplayHandler {
        ReplaySender sender;
        Handler() throws java.io.IOException { super(null,false); }
        @Override public ReplaySender getReplaySender() { return sender; }
        @Override public void spectateCamera() { }
        @Override public com.replaymod.replay.camera.CameraEntity getCameraEntity() { return null; }
    }
    private static class Host extends Minecraft implements MinecraftAccessor {
        Timer timer;
        Host() { super(null,0,0,false,false,null,null,null,null,null,null,null); }
        public Timer getTimer() { return timer; }
        public void setTimer(Timer timer) { this.timer=timer; }
        public Queue<FutureTask<?>> getScheduledTasks() { return null; }
        public CrashReport getCrashReporter() { return null; }
        public List<IResourcePack> getDefaultResourcePacks() { return null; }
        public void replayModSetFramebuffer(Framebuffer frame) { }
    }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
    private static Object get(Object object,Class<?> owner,String name) throws Exception { Field f=owner.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static void set(Object object,Class<?> owner,String name,Object value) throws Exception { Field f=owner.getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
}
