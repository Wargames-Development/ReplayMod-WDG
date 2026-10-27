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
    private static Object previousLoader;
    @org.junit.BeforeClass public static void eventOwner() throws Exception {
        Field instance=cpw.mods.fml.common.Loader.class.getDeclaredField("instance"); instance.setAccessible(true); previousLoader=instance.get(null);
        cpw.mods.fml.common.Loader loader=allocate(cpw.mods.fml.common.Loader.class);
        cpw.mods.fml.common.LoadController controller=allocate(cpw.mods.fml.common.LoadController.class);
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata(); metadata.modId="replaymod_test"; metadata.name="Auto Shot test";
        Field active=controller.getClass().getDeclaredField("activeContainer"); active.setAccessible(true); active.set(controller,new cpw.mods.fml.common.DummyModContainer(metadata));
        Field owner=loader.getClass().getDeclaredField("modController"); owner.setAccessible(true); owner.set(loader,controller); instance.set(null,loader);
    }
    @org.junit.AfterClass public static void restoreOwner() throws Exception {
        Field instance=cpw.mods.fml.common.Loader.class.getDeclaredField("instance"); instance.setAccessible(true); instance.set(null,previousLoader);
    }
    @Test public void oneDragWithManyMouseMovesIsOneUndoAndRedoRestoresItsExactCurve() throws Exception {
        try(TestTimeline fixture=new TestTimeline()) {
            StudioAutoShot screen=screen(); screen.activate(11);
            ShotCurve original=(ShotCurve)get(screen,"curve");
            dragPoint(screen,2,25,-15,20);
            ShotCurve moved=(ShotCurve)get(screen,"curve"); ShotDiagram diagram=(ShotDiagram)get(screen,"diagram");
            assertFalse(original==moved);
            assertTrue(screen.historyKey(org.lwjgl.input.Keyboard.KEY_Z,true,false));
            assertSame(original,get(screen,"curve")); assertEquals(2,get(screen,"selectedPoint")); assertSame(diagram,get(screen,"diagram"));
            screen.historyKey(org.lwjgl.input.Keyboard.KEY_Z,true,false); assertSame(original,get(screen,"curve"));
            screen.historyKey(org.lwjgl.input.Keyboard.KEY_Z,true,true); assertSame(moved,get(screen,"curve"));
            screen.activate(22); assertSame(original,get(screen,"curve"));
            screen.activate(23); assertSame(moved,get(screen,"curve"));
            assertTrue(fixture.timeline.getPositionPath().getKeyframes().isEmpty());
        }
    }
    @Test public void splineHistoryRetainsThirtyTwoCompleteActionsAndDropsOnlyTheOldest() throws Exception {
        try(TestTimeline fixture=new TestTimeline()) {
            StudioAutoShot screen=screen(); screen.activate(11);
            java.util.List<ShotCurve> states=new java.util.ArrayList<>(); states.add((ShotCurve)get(screen,"curve"));
            for(int i=0;i<40;i++) { dragPoint(screen,2,1,0,3); states.add((ShotCurve)get(screen,"curve")); }
            for(int i=39;i>=8;i--) { screen.historyKey(org.lwjgl.input.Keyboard.KEY_Z,true,false); assertSame(states.get(i),get(screen,"curve")); }
            screen.historyKey(org.lwjgl.input.Keyboard.KEY_Z,true,false); assertSame(states.get(8),get(screen,"curve"));
            for(int i=9;i<=40;i++) { screen.historyKey(org.lwjgl.input.Keyboard.KEY_Y,true,false); assertSame(states.get(i),get(screen,"curve")); }
            screen.historyKey(org.lwjgl.input.Keyboard.KEY_Y,true,false); assertSame(states.get(40),get(screen,"curve"));
        }
    }
    @Test public void pointAdditionRemovalAndResetCanBeUndoneAndNewEditsReplaceRedo() throws Exception {
        try(TestTimeline fixture=new TestTimeline()) {
            StudioAutoShot screen=screen(); screen.activate(11);
            ShotCurve original=(ShotCurve)get(screen,"curve");
            screen.activate(20); ShotCurve added=(ShotCurve)get(screen,"curve");
            screen.activate(21); screen.activate(22); assertSame(added,get(screen,"curve")); assertEquals(1,get(screen,"selectedPoint"));
            screen.activate(22); assertSame(original,get(screen,"curve")); assertEquals(0,get(screen,"selectedPoint"));
            screen.activate(23); assertSame(added,get(screen,"curve"));
            screen.activate(15); screen.activate(14); screen.activate(11); // View/reference changes retain draft history.
            screen.activate(22); assertSame(original,get(screen,"curve"));
            screen.activate(23); assertSame(added,get(screen,"curve"));
            screen.activate(16); screen.activate(22); assertSame(added,get(screen,"curve"));
            screen.activate(20); ShotCurve branched=(ShotCurve)get(screen,"curve");
            screen.activate(23); assertSame(branched,get(screen,"curve"));
            assertFalse(screen.historyKey(org.lwjgl.input.Keyboard.KEY_Z,false,false));
            assertTrue(fixture.timeline.getTimePath().getKeyframes().isEmpty());
        }
    }
    @Test public void noOpAndRejectedDragsDoNotConsumeHistoryAndUndoNeverChangesThePlacedTimeline() throws Exception {
        try(TestTimeline fixture=new TestTimeline()) {
            fixture.timeline.setDefaultInterpolatorType(com.replaymod.simplepathing.InterpolatorType.CATMULL_ROM);
            fixture.timeline.addPositionKeyframe(12000,10,80,20,0,0,0,-1);
            fixture.timeline.addTimeKeyframe(12000,20000);
            Object previousUndo=fixture.timeline.getTimeline().peekUndoStack();
            StudioAutoShot screen=screen(); screen.fields.get(9).setText("2"); screen.fields.get(10).setText("2"); screen.activate(11);
            ShotCurve original=(ShotCurve)get(screen,"curve");
            dragPoint(screen,2,0,0,1); screen.activate(22); assertSame(original,get(screen,"curve"));
            dragPoint(screen,2,0,-138,1); assertSame(original,get(screen,"curve"));
            screen.activate(22); assertSame(original,get(screen,"curve"));
            assertSame(previousUndo,fixture.timeline.getTimeline().peekUndoStack());
            assertEquals(1,fixture.timeline.getPositionPath().getKeyframes().size());
        }
    }
    @Test public void undoWhileHoldingADragStopsTheGestureAndRetainsRedo() throws Exception {
        try(TestTimeline fixture=new TestTimeline()) {
            StudioAutoShot screen=screen(); screen.activate(11);
            AutomatedShot.Plan plan=(AutomatedShot.Plan)get(screen,"proposal"); ShotCurve original=(ShotCurve)get(screen,"curve");
            ShotDiagram diagram=new ShotDiagram(plan,ShotDiagram.View.TOP,414,166,670,340); set(screen,"diagram",diagram);
            int x=(int)Math.round(diagram.x(plan.handles.get(2))),y=(int)Math.round(diagram.y(plan.handles.get(2)));
            screen.clicked(x,y,0); screen.dragged(x+12,y-8,0); ShotCurve moved=(ShotCurve)get(screen,"curve");
            screen.historyKey(org.lwjgl.input.Keyboard.KEY_Z,true,false);
            assertSame(original,get(screen,"curve")); assertEquals(-1,get(screen,"draggingPoint"));
            screen.dragged(x+30,y-20,0); screen.released(x+30,y-20,0); assertSame(original,get(screen,"curve"));
            screen.historyKey(org.lwjgl.input.Keyboard.KEY_Y,true,false); assertSame(moved,get(screen,"curve"));
        }
    }
    private static void dragPoint(StudioAutoShot screen,int index,int dx,int dy,int steps) throws Exception {
        AutomatedShot.Plan plan=(AutomatedShot.Plan)get(screen,"proposal");
        if(plan==null) { screen.activate(11); plan=(AutomatedShot.Plan)get(screen,"proposal"); }
        ShotDiagram diagram=(ShotDiagram)get(screen,"diagram");
        if(diagram==null) { diagram=new ShotDiagram(plan,(ShotDiagram.View)get(screen,"view"),414,166,670,340); set(screen,"diagram",diagram); }
        AutomatedShot.Frame handle=plan.handles.get(index);
        int x=(int)Math.round(diagram.x(handle)),y=(int)Math.round(diagram.y(handle));
        screen.clicked(x,y,0);
        for(int i=1;i<=steps;i++) screen.dragged(x+(int)Math.round(dx*i/(double)steps),y+(int)Math.round(dy*i/(double)steps),0);
        screen.released(x+dx,y+dy,0);
    }
    private static final class TestTimeline implements AutoCloseable {
        final com.replaymod.simplepathing.ReplayModSimplePathing previous=com.replaymod.simplepathing.ReplayModSimplePathing.instance;
        final com.replaymod.simplepathing.SPTimeline timeline=new com.replaymod.simplepathing.SPTimeline();
        TestTimeline() throws Exception {
            com.replaymod.simplepathing.ReplayModSimplePathing mod=allocate(com.replaymod.simplepathing.ReplayModSimplePathing.class);
            Field current=mod.getClass().getDeclaredField("currentTimeline"); current.setAccessible(true); current.set(mod,timeline);
            com.replaymod.simplepathing.ReplayModSimplePathing.instance=mod;
        }
        public void close() { com.replaymod.simplepathing.ReplayModSimplePathing.instance=previous; }
    }
    @Test public void draggingRefinesTheDraftWithoutTouchingTimelineAndSurvivesModeAndViewChanges() throws Exception {
        com.replaymod.simplepathing.ReplayModSimplePathing old=com.replaymod.simplepathing.ReplayModSimplePathing.instance;
        try {
            com.replaymod.simplepathing.ReplayModSimplePathing mod=allocate(com.replaymod.simplepathing.ReplayModSimplePathing.class);
            com.replaymod.simplepathing.SPTimeline timeline=new com.replaymod.simplepathing.SPTimeline();
            Field current=mod.getClass().getDeclaredField("currentTimeline"); current.setAccessible(true); current.set(mod,timeline);
            com.replaymod.simplepathing.ReplayModSimplePathing.instance=mod;
            StudioAutoShot screen=screen(); screen.activate(11);
            AutomatedShot.Plan before=(AutomatedShot.Plan)get(screen,"proposal");
            ShotDiagram diagram=new ShotDiagram(before,ShotDiagram.View.TOP,414,166,670,340); set(screen,"diagram",diagram);
            AutomatedShot.Frame handle=before.handles.get(2);
            int x=(int)Math.round(diagram.x(handle)),y=(int)Math.round(diagram.y(handle));
            screen.clicked(x,y,0); screen.dragged(x+25,y-15,0);
            AutomatedShot.Plan after=(AutomatedShot.Plan)get(screen,"proposal");
            assertEquals(25,diagram.x(after.handles.get(2))-diagram.x(handle),1e-8);
            assertEquals(-15,diagram.y(after.handles.get(2))-diagram.y(handle),1e-8);
            assertSame(diagram,get(screen,"diagram"));
            screen.released(x+25,y-15,0); assertEquals(-1,get(screen,"draggingPoint"));
            assertTrue(timeline.getPositionPath().getKeyframes().isEmpty()); assertTrue(timeline.getTimePath().getKeyframes().isEmpty());
            ShotCurve curve=(ShotCurve)get(screen,"curve");
            screen.activate(15); assertSame(curve,get(screen,"curve")); assertEquals(ShotDiagram.View.SIDE,get(screen,"view"));
            screen.activate(14); assertSame(curve,get(screen,"curve")); assertEquals(AutomatedShot.Space.WORLD,get(screen,"space"));
            screen.activate(11); assertEquals(AutomatedShot.Space.WORLD,((AutomatedShot.Plan)get(screen,"proposal")).space);
            screen.activate(20); assertEquals(6,((ShotCurve)get(screen,"curve")).points.size());
            screen.activate(21); assertEquals(5,((ShotCurve)get(screen,"curve")).points.size());
            screen.activate(16); assertEquals(0,((ShotCurve)get(screen,"curve")).points.get(2).x,0);
        } finally { com.replaymod.simplepathing.ReplayModSimplePathing.instance=old; }
    }
    @Test public void overlappingOrbitEndpointsCanEachBeSelectedAndDragged() {
        AutomatedShot.Settings s=new AutomatedShot.Settings(); s.angleChange=360;
        AutomatedShot.Plan plan=AutomatedShot.generate(s,t -> new AutomatedShot.Pose(0,80,0,0),60000);
        ShotDiagram d=new ShotDiagram(plan,ShotDiagram.View.TOP,414,166,670,340);
        int x=(int)Math.round(d.x(plan.handles.get(0))),y=(int)Math.round(d.y(plan.handles.get(0)));
        assertEquals(0,d.pick(plan,x,y,0)); assertEquals(4,d.pick(plan,x,y,4));
    }
    @Test public void renderedDiagramClipsRefinedPathsAtDifferentWindowAndDisplayScales() throws Exception {
        org.junit.Assume.assumeNotNull(System.getenv("REPLAYMOD_TEST_GL_NATIVES"));
        for(int[] window:new int[][]{{1120,720},{800,600},{1720,720}}) for(ShotDiagram.View view:ShotDiagram.View.values()) {
            int displayWidth=window[0]*2,displayHeight=window[1]*2;
            org.lwjgl.opengl.Pbuffer surface=new org.lwjgl.opengl.Pbuffer(displayWidth,displayHeight,new org.lwjgl.opengl.PixelFormat(),null,null);
            try {
                surface.makeCurrent(); org.lwjgl.opengl.GL11.glViewport(0,0,displayWidth,displayHeight);
                org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION); org.lwjgl.opengl.GL11.glLoadIdentity();
                org.lwjgl.opengl.GL11.glOrtho(0,window[0],window[1],0,-1,1);
                org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW); org.lwjgl.opengl.GL11.glLoadIdentity();
                org.lwjgl.opengl.GL11.glClearColor(0,0,0,1); org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
                StudioAutoShot screen=screen(); screen.width=window[0]; screen.height=window[1]; screen.mc.displayWidth=displayWidth; screen.mc.displayHeight=displayHeight;
                StudioViewport viewport=screen.createViewport(); screen.canvasWidth=viewport.canvasWidth; screen.canvasHeight=viewport.canvasHeight;
                org.lwjgl.opengl.GL11.glTranslatef(viewport.x,viewport.y,0); org.lwjgl.opengl.GL11.glScalef(viewport.scale,viewport.scale,1);
                AutomatedShot.Settings s=new AutomatedShot.Settings(); AutomatedShot.Target target=t -> new AutomatedShot.Pose(0,80,0,0);
                AutomatedShot.Plan original=AutomatedShot.generate(s,target,60000);
                int left=414,top=166,width=screen.canvasWidth-450,height=screen.canvasHeight-380;
                ShotDiagram diagram=new ShotDiagram(original,view,left,top,width,height); set(screen,"diagram",diagram);
                s.curve=s.curve.move(2,3000,700,-3000); AutomatedShot.Plan refined=AutomatedShot.generate(s,target,60000);
                java.lang.reflect.Method draw=StudioAutoShot.class.getDeclaredMethod("drawDiagram",AutomatedShot.Plan.class,int.class,int.class,int.class,int.class); draw.setAccessible(true);
                draw.invoke(screen,refined,left,top,width,height);
                java.nio.ByteBuffer pixels=org.lwjgl.BufferUtils.createByteBuffer(displayWidth*displayHeight*4);
                org.lwjgl.opengl.GL11.glReadPixels(0,0,displayWidth,displayHeight,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,pixels);
                int coloured=0;
                for(int y=0;y<displayHeight;y++) for(int x=0;x<displayWidth;x++) {
                    int index=(y*displayWidth+x)*4;
                    if(pixels.get(index)==0 && pixels.get(index+1)==0 && pixels.get(index+2)==0) continue;
                    coloured++;
                    double logicalX=(x/2.0-viewport.x)/viewport.scale,logicalY=((displayHeight-y-1)/2.0-viewport.y)/viewport.scale;
                    assertTrue("Curve escaped its diagram",logicalX>=left-2 && logicalX<=left+width+2 && logicalY>=top-2 && logicalY<=top+height+2);
                }
                assertTrue("Camera curve and handles must render",coloured>100);
                assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());
            } finally { surface.destroy(); }
        }
    }
    private static StudioAutoShot screen() throws Exception {
        Replay replay=allocate(Replay.class); replay.camera=allocate(CameraEntity.class);
        StudioAutoShot screen=new StudioAutoShot(null,replay,0); screen.mc=allocate(Minecraft.class);
        Field font=net.minecraft.client.gui.GuiScreen.class.getDeclaredField("fontRendererObj"); font.setAccessible(true); font.set(screen,allocate(Font.class));
        screen.width=1120; screen.height=720; screen.build(); return screen;
    }
    private static Object get(StudioAutoShot screen,String name) throws Exception { Field f=StudioAutoShot.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen); }
    private static void set(StudioAutoShot screen,String name,Object value) throws Exception { Field f=StudioAutoShot.class.getDeclaredField(name); f.setAccessible(true); f.set(screen,value); }
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
        @Override public int drawString(String text,int x,int y,int color) { return 0; }
    }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
}
