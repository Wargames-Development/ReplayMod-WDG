package com.replaymod.replay;

import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MouseHelper;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

public class FreeCameraPlaybackTest {
    @Test public void lateFmurAmmoScreenCannotReleaseTheMouseBetweenRapidPauseResumePresses() throws Exception {
        ReplayModReplay previous=ReplayModReplay.instance;
        try {
            ReplayModReplay mod=allocate(ReplayModReplay.class); ReplayModReplay.instance=mod;
            Host mc=allocate(Host.class); mc.mouseHelper=allocate(Mouse.class);
            Handler replay=allocate(Handler.class); Overlay overlay=allocate(Overlay.class); replay.overlay=overlay;
            set(mod,ReplayModReplay.class,"replayHandler",replay);
            set(overlay,GuiReplayOverlay.class,"mod",mod);
            double[] speed={1};
            replay.sender=(ReplaySender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ReplaySender.class},(p,m,a)-> {
                if(m.getName().equals("paused")) return speed[0]==0;
                if(m.getName().equals("setReplaySpeed")) speed[0]=(double)a[0];
                return null;
            });
            for(int press=0;press<8;press++) {
                ReplayModReplay.toggleFreeCameraPlayback(mc,replay);
                OpenScreen event=new OpenScreen(new com.flansmod.client.gui.GuiSelectAmmo());
                GuiReplayOverlay.rejectReplayAmmoScreen(replay,event); // FMUR's later KeyInputEvent callback.
                // Minecraft changes screen/focus only if GuiOpenEvent was not cancelled.
                if(!event.isCanceled()) { mc.currentScreen=event.gui; mc.inGameHasFocus=false; }
                assertTrue("Ammo menu must be rejected before it changes focus",event.isCanceled());
                assertNull(mc.currentScreen); assertTrue(mc.inGameHasFocus);
                assertEquals(press%2==0 ? 0 : 1.5,speed[0],0);
            }
            OpenScreen menu=new OpenScreen(new GuiScreen());
            GuiReplayOverlay.rejectReplayAmmoScreen(replay,menu); assertFalse("Other screens retain normal behavior",menu.isCanceled());
            set(mod,ReplayModReplay.class,"replayHandler",null);
            OpenScreen live=new OpenScreen(new com.flansmod.client.gui.GuiSelectAmmo());
            GuiReplayOverlay.rejectReplayAmmoScreen(null,live); assertFalse("Ammo selection still works in live play",live.isCanceled());
        } finally { ReplayModReplay.instance=previous; }
    }
    @Test public void lateMouseReleaseIsRepairedWithoutAnotherClickOrAnotherPlaybackToggle() throws Exception {
        Host mc=allocate(Host.class); Mouse mouse=allocate(Mouse.class); mc.mouseHelper=mouse;
        Handler replay=allocate(Handler.class); Overlay overlay=allocate(Overlay.class); replay.overlay=overlay;
        replay.camera=allocate(com.replaymod.replay.camera.CameraEntity.class);
        overlay.visible=false; mc.inGameHasFocus=true;
        // P already ran; a later callback releases the mouse before rendering.
        ReplayModReplay.keepFreeCameraCaptured(mc,replay,true,false);
        assertEquals(1,mouse.grabs); assertNull(mc.currentScreen); assertTrue(mc.inGameHasFocus);
        ReplayModReplay.keepFreeCameraCaptured(mc,replay,true,true);
        assertEquals("Healthy frames must not recenter the mouse",1,mouse.grabs);
        mc.inGameHasFocus=false;
        ReplayModReplay.keepFreeCameraCaptured(mc,replay,true,false);
        assertEquals(2,mouse.grabs); assertTrue(mc.inGameHasFocus);
    }
    @Test public void lateCaptureNeverStealsMenusAltTabOrLoadingScreens() throws Exception {
        Host mc=allocate(Host.class); Mouse mouse=allocate(Mouse.class); mc.mouseHelper=mouse;
        Handler replay=allocate(Handler.class); replay.overlay=allocate(Overlay.class);
        replay.camera=allocate(com.replaymod.replay.camera.CameraEntity.class);
        ReplayModReplay.keepFreeCameraCaptured(mc,replay,false,false);
        for(GuiScreen screen:new GuiScreen[]{new GuiScreen(),new net.minecraft.client.gui.GuiChat(),new net.minecraft.client.gui.GuiIngameMenu()}) {
            mc.currentScreen=screen;
            ReplayModReplay.keepFreeCameraCaptured(mc,replay,true,false);
            assertSame(screen,mc.currentScreen);
        }
        mc.currentScreen=null; replay.camera=null;
        ReplayModReplay.keepFreeCameraCaptured(mc,replay,true,false);
        ReplayModReplay.keepFreeCameraCaptured(mc,null,true,false);
        assertEquals(0,mouse.grabs);
    }
    @Test public void actualLegacyInputScreenIsClosedBeforeRecapturingTheMouse() throws Exception {
        Host mc=allocate(Host.class); Mouse mouse=allocate(Mouse.class); mc.mouseHelper=mouse;
        Handler replay=allocate(Handler.class); RealOverlay overlay=allocate(RealOverlay.class); overlay.host=mc; replay.overlay=overlay;
        replay.camera=allocate(com.replaymod.replay.camera.CameraEntity.class);
        Class<?> type=Class.forName("de.johni0702.minecraft.gui.container.AbstractGuiOverlay$UserInputGuiScreen");
        GuiScreen legacy=(GuiScreen)allocate(type);
        set(legacy,type,"this$0",overlay);
        Class<?> base=de.johni0702.minecraft.gui.container.AbstractGuiOverlay.class;
        set(overlay,base,"visible",true); set(overlay,base,"mouseVisible",true); set(overlay,base,"userInputGuiScreen",legacy);
        mc.currentScreen=legacy;
        ReplayModReplay.keepFreeCameraCaptured(mc,replay,true,false);
        assertNull(mc.currentScreen); assertFalse(overlay.isMouseVisible()); assertEquals(1,mouse.grabs);
        // A legacy input screen belonging to a different overlay is not ours to close.
        mc.currentScreen=legacy; replay.overlay=allocate(Overlay.class);
        ReplayModReplay.keepFreeCameraCaptured(mc,replay,true,false);
        assertSame(legacy,mc.currentScreen); assertEquals(1,mouse.grabs);
    }
    @Test public void pTogglesActionWithoutOpeningAnOverlayAndRecapturesTheMouse() throws Exception {
        Host mc=allocate(Host.class); Mouse mouse=allocate(Mouse.class); mc.mouseHelper=mouse;
        Handler replay=allocate(Handler.class); Overlay overlay=allocate(Overlay.class); replay.overlay=overlay;
        double[] speed={1};
        replay.sender=(ReplaySender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ReplaySender.class},(p,m,a)-> {
            if(m.getName().equals("paused")) return speed[0]==0;
            if(m.getName().equals("setReplaySpeed")) speed[0]=(double)a[0];
            return null;
        });
        ReplayModReplay.toggleFreeCameraPlayback(mc,replay);
        assertEquals(0,speed[0],0); assertEquals(1,mc.focused); assertEquals(1,mouse.grabs); assertFalse(overlay.visible); assertNull(mc.currentScreen);
        ReplayModReplay.toggleFreeCameraPlayback(mc,replay);
        assertEquals(1.5,speed[0],0); assertEquals(2,mouse.grabs); assertNull(mc.currentScreen);
        mc.currentScreen=new GuiScreen(); // P typed into a GUI must not toggle action or steal its mouse.
        ReplayModReplay.toggleFreeCameraPlayback(mc,replay);
        assertEquals(1.5,speed[0],0); assertEquals(2,mouse.grabs);
    }
    private static class Host extends Minecraft {
        int focused;
        Host() { super(null,0,0,false,false,null,null,null,null,null,null,null); }
        @Override public void setIngameFocus() { focused++; inGameHasFocus=true; }
        @Override public void displayGuiScreen(GuiScreen screen) { currentScreen=screen; }
    }
    // Forge's launch-time transformer normally supplies this for @Cancelable events.
    private static class OpenScreen extends net.minecraftforge.client.event.GuiOpenEvent {
        OpenScreen(GuiScreen screen) { super(screen); }
        @Override public boolean isCancelable() { return true; }
    }
    private static class Mouse extends MouseHelper { int grabs; @Override public void grabMouseCursor() { grabs++; } }
    private static class Handler extends ReplayHandler {
        ReplaySender sender; GuiReplayOverlay overlay;
        com.replaymod.replay.camera.CameraEntity camera;
        Handler() throws java.io.IOException { super(null,false); }
        @Override public ReplaySender getReplaySender() { return sender; }
        @Override public GuiReplayOverlay getOverlay() { return overlay; }
        @Override public com.replaymod.replay.camera.CameraEntity getCameraEntity() { return camera; }
    }
    private static class Overlay extends GuiReplayOverlay {
        boolean visible=true;
        Overlay() { super(null); }
        @Override public double getSpeedSliderValue() { return 1.5; }
        @Override public void setMouseVisible(boolean visible) { this.visible=visible; }
        @Override public boolean isMouseVisible() { return visible; }
    }
    private static class RealOverlay extends GuiReplayOverlay {
        Host host;
        RealOverlay() { super(null); }
        @Override public Minecraft getMinecraft() { return host; }
    }
    private static void set(Object object,Class<?> type,String name,Object value) throws Exception { Field field=type.getDeclaredField(name); field.setAccessible(true); field.set(object,value); }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
}
