package com.replaymod.studio;

import com.replaymod.render.gui.GuiVideoRenderer;
import de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import org.junit.Test;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class StudioRenderPreviewTest {
    @Test public void previewUsesDeliveredFrameDimensionsAndDoesNotConsumeEncoderBuffer() throws Exception {
        // No renderer or OpenGL context is needed by the worker-side pixel copy.
        GuiVideoRenderer screen = new GuiVideoRenderer(null);
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
