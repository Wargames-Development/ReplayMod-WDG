package com.replaymod.studio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import sun.misc.Unsafe;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import static org.junit.Assert.*;

public class StudioMenuTest {
    private static Field localeField;
    private static Object previousLocale;
    @org.junit.BeforeClass public static void prepareLanguage() throws Exception {
        for(Field field:net.minecraft.client.resources.I18n.class.getDeclaredFields()) if(field.getType()==net.minecraft.client.resources.Locale.class) localeField=field;
        localeField.setAccessible(true); previousLocale=localeField.get(null);
        localeField.set(null,new net.minecraft.client.resources.Locale());
    }
    @org.junit.AfterClass public static void restoreLanguage() throws Exception { localeField.set(null,previousLocale); }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void exportDoneUsesACompactCenteredPanelAndAllActionsFitInsideIt() throws Exception {
        StudioRenderDone done=new StudioRenderDone(new File(temporary.getRoot(),"PlaneTest.mp4"),900);
        prepare(done,new int[]{1920,1080}); assertControlsFit(done);
        for(Object value:buttons(done)) {
            GuiButton b=(GuiButton)value;
            assertTrue(b.xPosition>=236); assertTrue(b.xPosition+b.width<=724);
            assertEquals(294,b.yPosition);
        }
        // Only the inherited empty backdrop is used: no fullscreen dark overlay.
        try { StudioRenderDone.class.getDeclaredMethod("drawBackdrop",int.class,int.class,float.class); fail(); }
        catch(NoSuchMethodException expected) { }
    }
    @Test public void libraryFillsAllWindowShapesAndKeepsControlsInsideItsPanel() throws Exception {
        for(int[] size:new int[][]{{800,600},{1920,1080},{3440,1440},{1080,1920}}) {
            ReplayLibrary library=new ReplayLibrary(null);
            set(library,ReplayLibrary.class,"folder",temporary.getRoot());
            StudioViewport viewport=prepare(library,size);
            assertEquals(0,viewport.x); assertEquals(0,viewport.y);
            assertEquals(size[0],library.canvasWidth*viewport.scale,1);
            assertEquals(size[1],library.canvasHeight*viewport.scale,1);
            assertControlsFit(library);
            GuiTextField search=library.fields.get(0),name=library.fields.get(1);
            assertEquals(28,search.xPosition); assertEquals(library.canvasWidth-28,name.xPosition+name.width);
        }
    }
    @Test public void recordingPopupStacksScrollsSelectsDefaultNamesAndSavesEveryRow() throws Exception {
        List<Path> recordings=new ArrayList<>();
        for(int i=0;i<50;i++) recordings.add(Paths.get("take-"+i+".mcpr"));
        Map<Path,String> saved=new LinkedHashMap<>();
        StudioRecordingNames popup=new StudioRecordingNames(null,recordings,saved::put);
        prepare(popup,new int[]{1920,1080}); assertControlsFit(popup);
        int left=(int)get(popup,StudioRecordingNames.class,"left"),top=(int)get(popup,StudioRecordingNames.class,"listTop");
        GuiTextField first=popup.fields.get(0);
        StudioViewport viewport=(StudioViewport)get(popup,StudioScreen.class,"viewport");
        popup.mouseClicked(Math.round((first.xPosition+8)*viewport.scale),Math.round((first.yPosition+8)*viewport.scale),0);
        assertEquals(first.getText(),first.getSelectedText());
        first.setText("Takeoff");
        popup.activate(101); // Stage deletion, not an immediate filesystem operation.
        assertTrue(saved.isEmpty());
        popup.clicked(left+330,top+popup.fields.size()*32-1,0);
        popup.released(left+330,top,0);
        assertEquals(50-popup.fields.size(),get(popup,StudioRecordingNames.class,"offset"));
        popup.fields.get(popup.fields.size()-1).setText("Landing");
        popup.scrolled(left+20,top+5,120);
        popup.saveAll();
        assertEquals(50,saved.size()); assertEquals("Takeoff",saved.get(recordings.get(0)));
        assertEquals("",saved.get(recordings.get(1))); assertEquals("Landing",saved.get(recordings.get(49)));
        popup.saveAll(); assertEquals(50,saved.size());
    }
    @Test public void oneRecordingUsesACompactTopRightPopupWithoutPagingButtons() throws Exception {
        StudioRecordingNames popup=new StudioRecordingNames(null,Collections.singletonList(Paths.get("take.mcpr")),(p,n)->{});
        prepare(popup,new int[]{3440,1440});
        assertEquals(124,get(popup,StudioRecordingNames.class,"popupHeight"));
        assertEquals(popup.canvasWidth-352,get(popup,StudioRecordingNames.class,"left"));
        for(Object value:buttons(popup)) {
            GuiButton button=(GuiButton)value;
            assertFalse(button.displayString.equals("Previous")); assertFalse(button.displayString.equals("Next"));
        }
    }
    @Test public void trashCanBeRestoredAndInvalidNamesDoNotPartiallyApply() throws Exception {
        List<String> operations=new ArrayList<>();
        StudioRecordingNames popup=new StudioRecordingNames(null,Arrays.asList(Paths.get("a.mcpr"),Paths.get("b.mcpr")),(p,n)->operations.add(n));
        prepare(popup,new int[]{960,540});
        popup.activate(100); popup.activate(100);
        popup.fields.get(1).setText("bad/name");
        try { popup.saveAll(); fail(); } catch(IllegalArgumentException expected) { }
        assertTrue(operations.isEmpty());
        popup.fields.get(1).setText("Good"); popup.saveAll();
        assertEquals(Arrays.asList("a","Good"),operations);
    }
    @Test public void exportNameKeepsTheFolderAndUsesTheSelectedExtension() {
        File original=new File(temporary.getRoot(),"old.mp4");
        assertEquals(new File(temporary.getRoot(),"Takeoff.mp4"),StudioExport.namedOutput(original,"Takeoff","mp4"));
        assertEquals(new File(temporary.getRoot(),"Takeoff.mp4"),StudioExport.namedOutput(original,"Takeoff.MP4","mp4"));
        assertEquals(new File(temporary.getRoot(),"Takeoff.mkv"),StudioExport.namedOutput(original,"Takeoff.mp4","mkv"));
        assertEquals("Takeoff.v2",StudioExport.outputBaseName(new File("Takeoff.v2.mp4")));
        assertEquals(new File(temporary.getRoot(),"Frames"),StudioExport.namedOutput(original,"Frames",""));
        for(String bad:new String[]{"","..","../outside","bad:name","a\\b"}) {
            try { StudioExport.namedOutput(original,bad,"mp4"); fail(bad); } catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void exportNameFieldAndFullPathEditsBothReachTheOutputWithoutLosingTheFolder() throws Exception {
        StudioExport screen=allocate(StudioExport.class);
        set(screen,StudioExport.class,"widthText","1920"); set(screen,StudioExport.class,"heightText","1080");
        set(screen,StudioExport.class,"fpsText","60"); set(screen,StudioExport.class,"bitrateText","12");
        set(screen,StudioExport.class,"preset",com.replaymod.render.RenderSettings.EncodingPreset.MP4_DEFAULT);
        set(screen,StudioExport.class,"method",com.replaymod.render.RenderSettings.RenderMethod.DEFAULT);
        set(screen,StudioExport.class,"aa",com.replaymod.render.RenderSettings.AntiAliasing.NONE);
        set(screen,StudioScreen.class,"fields",new ArrayList<GuiTextField>());
        set(screen,GuiScreen.class,"buttonList",new ArrayList<GuiButton>());
        File original=new File(temporary.getRoot(),"original.mp4");
        set(screen,StudioExport.class,"fileText",original.getPath());
        prepare(screen,new int[]{960,540}); assertControlsFit(screen);
        assertEquals(6,screen.fields.size());
        assertEquals("original",screen.fields.get(5).getText());
        screen.mouseClicked(109,240,0);
        assertEquals("original",screen.fields.get(5).getSelectedText());
        File manual=new File(temporary.getRoot(),"Manual.mp4");
        screen.fields.get(4).setText(manual.getPath());
        java.lang.reflect.Method remember=StudioExport.class.getDeclaredMethod("remember"); remember.setAccessible(true); remember.invoke(screen);
        assertEquals(manual.getPath(),get(screen,StudioExport.class,"fileText"));
        screen.initGui();
        assertEquals("Manual",screen.fields.get(5).getText());
        screen.fields.get(5).setText("Takeoff"); remember.invoke(screen);
        assertEquals(new File(temporary.getRoot(),"Takeoff.mp4").getPath(),get(screen,StudioExport.class,"fileText"));
        screen.activate(4); // MP4 preset -> custom MP4 -> MKV, keeping a bare name.
        assertEquals("Takeoff",screen.fields.get(5).getText());
        screen.activate(4); assertEquals("Takeoff",screen.fields.get(5).getText());
        assertTrue(((String)get(screen,StudioExport.class,"fileText")).endsWith("Takeoff.mkv"));
        screen.fields.get(5).setText("bad?name");
        screen.beforeResize(); // Invalid in-progress text must not crash resize/close.
        screen.initGui(); assertEquals("bad?name",screen.fields.get(5).getText());
        assertFalse(((String)get(screen,StudioExport.class,"filenameError")).isEmpty());
    }
    private static StudioViewport prepare(StudioScreen screen,int[] size) throws Exception {
        screen.mc=allocate(Minecraft.class); screen.mc.currentScreen=screen;
        screen.canvasWidth=960; screen.canvasHeight=540;
        set(screen,GuiScreen.class,"fontRendererObj",allocate(Font.class));
        screen.width=size[0]; screen.height=size[1];
        StudioViewport viewport=screen.createViewport(); set(screen,StudioScreen.class,"viewport",viewport);
        screen.canvasWidth=viewport.canvasWidth; screen.canvasHeight=viewport.canvasHeight; screen.build(); return viewport;
    }
    private static void assertControlsFit(StudioScreen screen) throws Exception {
        for(GuiTextField field:screen.fields) { assertTrue(field.xPosition>=0); assertTrue(field.xPosition+field.width<=screen.canvasWidth); assertTrue(field.yPosition+field.height<=screen.canvasHeight); }
        for(Object value:buttons(screen)) { GuiButton button=(GuiButton)value; assertTrue(button.xPosition>=0); assertTrue(button.xPosition+button.width<=screen.canvasWidth); assertTrue(button.yPosition+button.height<=screen.canvasHeight); }
    }
    private static List<?> buttons(StudioScreen screen) throws Exception { return (List<?>)get(screen,GuiScreen.class,"buttonList"); }
    private static class Font extends FontRenderer {
        Font() { super(null,null,null,false); }
        @Override public int getStringWidth(String text) { return text.length()*6; }
        @Override public String trimStringToWidth(String text,int width) { return text.substring(0,Math.min(text.length(),Math.max(0,width/6))); }
        @Override public String trimStringToWidth(String text,int width,boolean reverse) { return trimStringToWidth(text,width); }
    }
    private static Object get(Object object,Class<?> owner,String name) throws Exception { Field f=owner.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static void set(Object object,Class<?> owner,String name,Object value) throws Exception { Field f=owner.getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
}
