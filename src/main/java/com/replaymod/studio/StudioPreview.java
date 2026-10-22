package com.replaymod.studio;

import com.replaymod.core.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** One normal game render into a viewer target, followed by the native editor composite. */
public final class StudioPreview {
    private static boolean capturing;
    private Framebuffer image;
    private boolean ready;
    private double aspect = 16.0 / 9;
    public static boolean isCapturing() { return capturing; }
    public void render(EntityRenderer renderer, float partialTicks, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.isFramebufferEnabled()) throw new IllegalStateException("Enable Minecraft framebuffers to use the cinematic editor viewer.");
        if (image == null || image.framebufferWidth != width || image.framebufferHeight != height) {
            release(); image = new Framebuffer(width, height, true);
        }
        int originalWidth = mc.displayWidth, originalHeight = mc.displayHeight;
        Framebuffer original = mc.getFramebuffer();
        MinecraftAccessor accessor = (MinecraftAccessor) mc;
        // Vanilla entity post-processing holds references to the original main FBO.
        // Keep that uncommon path intact and scale its result into the viewer.
        boolean legacyShader = renderer.getShaderGroup() != null;
        capturing = true;
        try {
            if (!legacyShader) {
                mc.displayWidth = width; mc.displayHeight = height;
                accessor.replayModSetFramebuffer(image); image.bindFramebuffer(true);
                image.framebufferClear(); image.bindFramebuffer(true);
            } else original.bindFramebuffer(true);
            renderer.updateCameraAndRender(partialTicks);
            if (legacyShader) {
                image.bindFramebuffer(true);
                renderer.setupOverlayRendering();
                GL11.glViewport(0, 0, width, height);
                GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, width, height, 0, 1000, 3000);
                GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslatef(0, 0, -2000);
                texture(original, 0, 0, width, height);
            }
            aspect = legacyShader ? originalWidth / (double) originalHeight : width / (double) height;
            ready = true;
        } finally {
            accessor.replayModSetFramebuffer(original);
            mc.displayWidth = originalWidth; mc.displayHeight = originalHeight;
            capturing = false;
            original.bindFramebuffer(true);
            renderer.setupOverlayRendering();
        }
    }
    public void draw(int[] area) {
        draw(area, false);
    }
    public void draw(int[] area, boolean fill) {
        if (!ready || image == null) return;
        if (fill) {
            textureRegion(image, area[0], area[1], area[2], area[3], StudioViewerGeometry.crop(aspect, area[2], area[3]));
            return;
        }
        int width = Math.min(area[2], (int) Math.floor(area[3] * aspect)), height = Math.min(area[3], (int) Math.floor(width / aspect));
        texture(image, area[0] + (area[2] - width) / 2, area[1] + (area[3] - height) / 2, width, height);
    }
    private static void texture(Framebuffer framebuffer, int left, int top, int width, int height) {
        textureRegion(framebuffer, left, top, width, height, new float[]{0,0,1,1});
    }
    private static void textureRegion(Framebuffer framebuffer, int left, int top, int width, int height, float[] uv) {
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_BLEND);
        // World rendering leaves the framebuffer's alpha channel unwritten.
        // Like vanilla framebufferRender, display its RGB even when alpha is zero.
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
        float u = framebuffer.framebufferWidth / (float) framebuffer.framebufferTextureWidth, v = framebuffer.framebufferHeight / (float) framebuffer.framebufferTextureHeight;
        Tessellator t = Tessellator.instance; t.startDrawingQuads();
        t.addVertexWithUV(left, top + height, 0, uv[0]*u, uv[1]*v); t.addVertexWithUV(left + width, top + height, 0, uv[2]*u, uv[1]*v);
        t.addVertexWithUV(left + width, top, 0, uv[2]*u, uv[3]*v); t.addVertexWithUV(left, top, 0, uv[0]*u, uv[3]*v); t.draw();
    }
    public void release() { if (image != null) { image.deleteFramebuffer(); image = null; } ready = false; }
    public static void drawWorkspace() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof CinematicEditor)) return;
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int x = Mouse.getX() * resolution.getScaledWidth() / Math.max(1, mc.displayWidth);
        int y = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / Math.max(1, mc.displayHeight) - 1;
        mc.getFramebuffer().bindFramebuffer(true); mc.entityRenderer.setupOverlayRendering();
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glColor4f(1, 1, 1, 1);
        float partial = ((MinecraftAccessor) mc).getTimer().renderPartialTicks;
        if (!MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.DrawScreenEvent.Pre(mc.currentScreen, x, y, partial))) mc.currentScreen.drawScreen(x, y, partial);
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.DrawScreenEvent.Post(mc.currentScreen, x, y, partial));
    }
}
