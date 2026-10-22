package com.replaymod.render.rendering;

import net.minecraft.client.Minecraft;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class RenderWindowSizeTest {
    @Test public void windowPreviewSizeIsStableAcrossCapturePassesAndRestoresCaptureOnFailure() throws Exception {
        Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Minecraft mc=(Minecraft)((Unsafe)f.get(null)).allocateInstance(Minecraft.class);
        for(int[] size:new int[][]{{1920,1080},{3840,2160},{2048,2048},{7680,4320},{1280,720}}) {
            mc.displayWidth=size[0]; mc.displayHeight=size[1];
            assertTrue(VideoRenderer.drawAtWindowSize(mc,2552,1390,() -> {
                assertEquals(2552,mc.displayWidth); assertEquals(1390,mc.displayHeight); return true;
            }));
            assertEquals(size[0],mc.displayWidth); assertEquals(size[1],mc.displayHeight);
            try {
                VideoRenderer.drawAtWindowSize(mc,2552,1390,() -> { throw new IllegalStateException("cancelled"); }); fail();
            } catch(IllegalStateException expected) { }
            assertEquals(size[0],mc.displayWidth); assertEquals(size[1],mc.displayHeight);
        }
    }
}
