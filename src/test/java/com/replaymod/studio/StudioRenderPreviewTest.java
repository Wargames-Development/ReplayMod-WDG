package com.replaymod.studio;

import com.replaymod.render.gui.GuiVideoRenderer;
import de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import org.junit.Test;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class StudioRenderPreviewTest {
    @Test public void previewRectangleKeepsOutputAspectAndBoundsWhenDeliveredFrameDimensionsChange() throws Exception {
        Field allocator=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); allocator.setAccessible(true);
        com.replaymod.render.rendering.VideoRenderer renderer=(com.replaymod.render.rendering.VideoRenderer)((sun.misc.Unsafe)allocator.get(null)).allocateInstance(com.replaymod.render.rendering.VideoRenderer.class);
        com.replaymod.render.RenderSettings settings=new com.google.gson.Gson().fromJson("{\"videoWidth\":1920,\"videoHeight\":1080}",com.replaymod.render.RenderSettings.class);
        Field config=renderer.getClass().getDeclaredField("settings"); config.setAccessible(true); config.set(renderer,settings);
        GuiVideoRenderer screen=new GuiVideoRenderer(renderer);
        assertFalse((Boolean)field(screen,"previewEnabled"));
        screen.setPreviewEnabled(true);
        Field layout=GuiVideoRenderer.class.getDeclaredField("layout"); layout.setAccessible(true); layout.set(screen,new StudioRenderLayout(960,540));
        java.lang.reflect.Method bounds=GuiVideoRenderer.class.getDeclaredMethod("previewBounds"); bounds.setAccessible(true);
        int[] expected=(int[])bounds.invoke(screen);
        for(int[] size:new int[][]{{2,1},{3,2},{8,8},{16,9}}) {
            screen.updatePreview(ByteBuffer.allocate(size[0]*size[1]*4),new Dimension(size[0],size[1]));
            assertArrayEquals(expected,(int[])bounds.invoke(screen));
        }
        screen.setPreviewEnabled(false); assertArrayEquals(expected,(int[])bounds.invoke(screen));
    }
    @Test public void disablingPreviewSkipsPixelCopiesAndReenablingWaitsForANewFrame() throws Exception {
        GuiVideoRenderer screen=new GuiVideoRenderer(null);
        assertFalse((Boolean)field(screen,"previewEnabled"));
        screen.setPreviewEnabled(true);
        ByteBuffer frame=ByteBuffer.allocate(16);
        screen.updatePreview(frame,new Dimension(2,2)); assertNotNull(field(screen,"pixels"));
        screen.setPreviewEnabled(false); assertNull(field(screen,"pixels"));
        // An empty buffer proves that hidden preview does not even read/copy pixels.
        screen.updatePreview(ByteBuffer.allocate(0),new Dimension(3840,2160));
        assertNull(field(screen,"pixels"));
        screen.setPreviewEnabled(true); assertNull(field(screen,"pixels"));
        screen.updatePreview(frame,new Dimension(2,2)); assertEquals(4,((int[])field(screen,"pixels")).length);
        assertEquals(0,frame.position());
    }
    @Test public void savedPreviewPreferenceDefaultsOffAndSurvivesEncodingPresetChanges() {
        com.google.gson.Gson gson=new com.google.gson.Gson();
        com.replaymod.render.RenderSettings settings=gson.fromJson("{}",com.replaymod.render.RenderSettings.class);
        assertFalse(settings.isShowPreview());
        settings=gson.fromJson("{\"showPreview\":true}",com.replaymod.render.RenderSettings.class);
        assertTrue(settings.isShowPreview());
        settings=gson.fromJson("{\"showPreview\":false,\"renderAudio\":false}",com.replaymod.render.RenderSettings.class);
        assertFalse(settings.isShowPreview());
        assertFalse(settings.withEncodingPreset(com.replaymod.render.RenderSettings.EncodingPreset.MP4_DEFAULT).isShowPreview());
        assertFalse(settings.withEncodingPreset(com.replaymod.render.RenderSettings.EncodingPreset.MP4_DEFAULT).isRenderAudio());
    }
    @Test public void previewUsesDeliveredFrameDimensionsAndDoesNotConsumeEncoderBuffer() throws Exception {
        // No renderer or OpenGL context is needed by the worker-side pixel copy.
        GuiVideoRenderer screen = new GuiVideoRenderer(null);
        screen.setPreviewEnabled(true);
        ByteBuffer frame = ByteBuffer.wrap(new byte[]{99, 10, 20, 30, 0, 40, 50, 60, 0});
        frame.position(1);
        screen.updatePreview(frame, new Dimension(2, 1));
        assertEquals(1, frame.position());
        assertEquals(2, field(screen, "previewWidth"));
        assertEquals(1, field(screen, "previewHeight"));
        assertArrayEquals(new int[]{0xFF1E140A, 0xFF3C3228}, (int[]) field(screen, "pixels"));
        // With supersampling, capture size and delivered/encoded size differ.
        screen.updatePreview(ByteBuffer.allocate(4 * 3 * 2), new Dimension(3, 2));
        assertEquals(3, field(screen, "previewWidth"));
        assertEquals(2, field(screen, "previewHeight"));
        assertEquals(6, ((int[]) field(screen, "pixels")).length);
    }
    private Object field(Object screen, String name) throws Exception {
        Field field = GuiVideoRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }
}
