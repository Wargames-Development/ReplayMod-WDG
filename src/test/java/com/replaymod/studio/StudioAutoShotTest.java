package com.replaymod.studio;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class StudioAutoShotTest {
    @Test public void turningMotionOffDiscardsFailureFromPreviousVehicleScan() throws Exception {
        Replay replay=allocate(Replay.class); replay.camera=allocate(CameraEntity.class);
        StudioAutoShot screen=new StudioAutoShot(null,replay,0);
        screen.mc=allocate(Minecraft.class);
        Field font=net.minecraft.client.gui.GuiScreen.class.getDeclaredField("fontRendererObj"); font.setAccessible(true); font.set(screen,allocate(Font.class));
        screen.width=1120; screen.height=720; screen.build();
        java.util.concurrent.FutureTask<RecordedVehicleTrack> failed=new java.util.concurrent.FutureTask<>(() -> { throw new java.io.IOException("Old vehicle failed"); });
        failed.run();
        Field loading=StudioAutoShot.class.getDeclaredField("loading"); loading.setAccessible(true); loading.set(screen,failed);
        screen.activate(9);
        java.lang.reflect.Method poll=StudioAutoShot.class.getDeclaredMethod("pollDraft"); poll.setAccessible(true); poll.invoke(screen);
        Field error=StudioAutoShot.class.getDeclaredField("draftError"); error.setAccessible(true);
        assertEquals("",error.get(screen)); assertNull(loading.get(screen));
    }
    @Test public void repickingDifferentVehicleAtSameListIndexInvalidatesDraftIdentity() throws Exception {
        StudioAutoShot screen=new StudioAutoShot(null,null,0);
        Field targets=StudioAutoShot.class.getDeclaredField("targets"); targets.setAccessible(true);
        Field selected=StudioAutoShot.class.getDeclaredField("target"); selected.setAccessible(true); selected.setInt(screen,0);
        java.lang.reflect.Method signature=StudioAutoShot.class.getDeclaredMethod("signature"); signature.setAccessible(true);
        targets.set(screen,java.util.Collections.singletonList(new ShotTargets.Target(42,"Plane",new AutomatedShot.Pose(0,0,0,0))));
        String first=(String)signature.invoke(screen);
        targets.set(screen,java.util.Collections.singletonList(new ShotTargets.Target(77,"Plane",new AutomatedShot.Pose(0,0,0,0))));
        assertFalse(first.equals(signature.invoke(screen)));
    }
    @Test public void emptyVehicleCycleExplainsWhyItCannotSelectAnything() throws Exception {
        StudioAutoShot screen=new StudioAutoShot(null,null,0);
        Field targets=StudioAutoShot.class.getDeclaredField("targets"); targets.setAccessible(true);
        targets.set(screen,java.util.Collections.emptyList());
        screen.attempt(() -> screen.activate(7));
        assertTrue(screen.message.contains("No MCHR vehicles"));
    }
    @Test public void automaticShotControlsFitTheFullscreenCanvasAndManualTargetsWorkWithoutMchr() throws Exception {
        for(int[] window:new int[][]{{1120,720},{1920,1080},{3440,1440},{800,600}}) {
            Replay replay=allocate(Replay.class); replay.camera=allocate(CameraEntity.class);
            replay.camera.posX=10; replay.camera.posY=80; replay.camera.posZ=20;
            StudioAutoShot screen=new StudioAutoShot(null,replay,0);
            screen.mc=allocate(Minecraft.class);
            Field font=net.minecraft.client.gui.GuiScreen.class.getDeclaredField("fontRendererObj"); font.setAccessible(true); font.set(screen,allocate(Font.class));
            StudioViewport viewport=new StudioViewport(window[0],window[1],1120,720,true);
            screen.canvasWidth=viewport.canvasWidth; screen.canvasHeight=viewport.canvasHeight;
            screen.build();
            assertEquals(16,screen.fields.size());
            // Disabled target motion still explains its requirement. Changing
            // controls or leaving them resets the delay without activating them.
            assertNull(screen.hoverHelp(205,265,0));
            assertNull(screen.hoverHelp(205,265,499));
            assertTrue(screen.hoverHelp(205,265,500).contains("Requires a selected vehicle"));
            assertNull(screen.hoverHelp(21,114,501));
            assertTrue(screen.hoverHelp(21,114,1001).contains("Position in the finished video"));
            assertNull(screen.hoverHelp(390,40,1002));
            assertNull(screen.hoverHelp(205,265,1003));
            assertNull(screen.hoverHelp(205,265,1502));
            assertNotNull(screen.hoverHelp(205,265,1503));
            for(GuiTextField field:screen.fields) { assertTrue(field.xPosition>=0); assertTrue(field.yPosition>=0); assertTrue(field.xPosition+field.width<=screen.canvasWidth); }
            int types=0;
            for(Object value:buttons(screen)) {
                GuiButton button=(GuiButton)value;
                assertTrue(button.xPosition>=0 && button.yPosition>=0); assertTrue(button.xPosition+button.width<=screen.canvasWidth); assertTrue(button.yPosition+button.height<=screen.canvasHeight);
                if(button.id>=1 && button.id<=3) types++;
                if(button.id==9) assertFalse(button.enabled);
            }
            assertEquals(3,types); assertEquals("0:00.000",screen.fields.get(0).getText());
        }
    }
    private static java.util.List<?> buttons(StudioAutoShot screen) throws Exception { Field f=net.minecraft.client.gui.GuiScreen.class.getDeclaredField("buttonList"); f.setAccessible(true); return (java.util.List<?>)f.get(screen); }
    private static class Replay extends ReplayHandler {
        CameraEntity camera;
        Replay() throws java.io.IOException { super(null,false); }
        @Override public CameraEntity getCameraEntity() { return camera; }
        @Override public int getReplayDuration() { return 60000; }
        @Override public com.replaymod.replay.ReplaySender getReplaySender() {
            return (com.replaymod.replay.ReplaySender)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{com.replaymod.replay.ReplaySender.class},(object,method,args) -> method.getName().equals("currentTimeStamp")?10000:null);
        }
    }
    private static class Font extends FontRenderer {
        Font() { super(null,null,null,false); }
        @Override public int getStringWidth(String text) { return text.length()*6; }
        @Override public String trimStringToWidth(String text,int width) { return text.substring(0,Math.min(text.length(),Math.max(0,width/6))); }
        @Override public String trimStringToWidth(String text,int width,boolean reverse) { return trimStringToWidth(text,width); }
    }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
}
