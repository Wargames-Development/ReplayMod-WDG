package com.replaymod.studio;

import net.minecraft.client.shader.Framebuffer;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Opt-in GPU integration test; a pbuffer creates no visible game or window. */
public class StudioViewerGlTest {
    @Test public void viewerDisplaysRgbWhenWorldAlphaIsZeroAndAlphaTestingWasEnabled() throws Exception {
        assumeNotNull(System.getenv("REPLAYMOD_TEST_GL_NATIVES"));
        Pbuffer surface=new Pbuffer(16,16,new PixelFormat(),null,null);
        int texture=0;
        try {
            surface.makeCurrent();
            GL11.glViewport(0,0,16,16);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,16,16,0,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL11.glClearColor(0,0,0,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            ByteBuffer pixels=BufferUtils.createByteBuffer(16);
            for(int i=0;i<4;i++) pixels.put((byte)220).put((byte)90).put((byte)35).put((byte)0);
            pixels.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,2,2,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            GL11.glEnable(GL11.GL_ALPHA_TEST); GL11.glAlphaFunc(GL11.GL_GREATER,.1F);
            Field allocator=Unsafe.class.getDeclaredField("theUnsafe"); allocator.setAccessible(true);
            Framebuffer frame=(Framebuffer)((Unsafe)allocator.get(null)).allocateInstance(Framebuffer.class);
            frame.framebufferTexture=texture;
            frame.framebufferWidth=frame.framebufferTextureWidth=2;
            frame.framebufferHeight=frame.framebufferTextureHeight=2;
            Method draw=StudioPreview.class.getDeclaredMethod("texture",Framebuffer.class,int.class,int.class,int.class,int.class);
            draw.setAccessible(true); draw.invoke(null,frame,0,0,16,16);
            ByteBuffer actual=BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(8,8,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);
            assertEquals(220,actual.get(0)&255); assertEquals(90,actual.get(1)&255); assertEquals(35,actual.get(2)&255);
            assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
        } finally {
            if(texture!=0) GL11.glDeleteTextures(texture);
            surface.destroy();
        }
    }
}
