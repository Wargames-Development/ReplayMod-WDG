package com.replaymod.render.gui;

import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.studio.StudioScreen;
import com.replaymod.studio.StudioViewport;
import com.replaymod.studio.StudioRenderLayout;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import java.nio.ByteBuffer;

/** Native render progress screen. Worker threads only copy pixels; GL stays on the render thread. */
public final class GuiVideoRenderer extends StudioScreen {
    private final VideoRenderer renderer;
    private final long started = System.currentTimeMillis();
    private volatile boolean previewEnabled = true;
    private boolean confirmCancel, dirty;
    private int[] pixels;
    private int previewWidth, previewHeight, textureWidth, textureHeight;
    private DynamicTexture texture;
    private StudioRenderLayout layout;
    public GuiVideoRenderer(VideoRenderer renderer) { this.renderer = renderer; }
    public GuiScreen toMinecraft() { return this; }
    @Override protected StudioViewport createViewport() { return new StudioViewport(width, height, 960, 540, true); }
    @Override protected void build() {
        layout = new StudioRenderLayout(canvasWidth, canvasHeight);
        int left = (canvasWidth - 620) / 2;
        button(1, left, layout.buttonTop, 200, renderer.isPaused() ? "Resume render" : "Pause render");
        button(2, left + 210, layout.buttonTop, 200, "Preview: " + (previewEnabled ? "On" : "Off"));
        button(3, left + 420, layout.buttonTop, 200, confirmCancel ? "Confirm cancel" : "Cancel render");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        // Overdraw a logical pixel to cover rounding at the window edges.
        drawRect(-1, -1, canvasWidth + 1, canvasHeight + 1, 0xFF101720);
        panel(16, 16, canvasWidth - 32, canvasHeight - 32, "RENDERING CINEMATIC");
        int done = renderer.getFramesDone(), total = renderer.getTotalFrames();
        label("Frame " + Math.min(done, total) + " / " + total, 32, 54, TEXT);
        if (!renderer.getStage().isEmpty()) label(renderer.getStage(), 32, 70, ACCENT);
        long elapsed = Math.max(0, System.currentTimeMillis() - started);
        long remaining = done > 0 ? elapsed * Math.max(0, total - done) / done : -1;
        label("Elapsed " + elapsed / 1000 + "s   |   " + (remaining < 0 ? "Preparing first frame..." : "About " + remaining / 1000 + "s remaining"), canvasWidth - 460, 54, MUTED);
        drawRect(32, layout.progressTop, canvasWidth - 32, layout.progressTop + 20, EDGE);
        drawRect(33, layout.progressTop + 1, 33 + (int) ((canvasWidth - 66L) * Math.min(done, total) / Math.max(1, total)), layout.progressTop + 19, ACCENT);
        if (previewEnabled) drawPreview();
        else previewMessage("Preview is off. Frames are still being exported.");
    }
    private void previewMessage(String text) { label(text, (canvasWidth - fontRendererObj.getStringWidth(text)) / 2, layout.previewTop + layout.previewHeight / 2, MUTED); }
    private synchronized void drawPreview() {
        if (pixels == null) { previewMessage("Preparing the scene and loading visible chunks..."); return; }
        int w = previewWidth, h = previewHeight;
        if (texture != null && (textureWidth != w || textureHeight != h)) { texture.deleteGlTexture(); texture = null; }
        if (texture == null) { texture = new DynamicTexture(w, h); textureWidth = w; textureHeight = h; }
        if (dirty) {
            System.arraycopy(pixels, 0, texture.getTextureData(), 0, pixels.length);
            texture.updateDynamicTexture(); dirty = false;
        }
        int[] bounds = layout.preview(w, h);
        int left = bounds[0], top = bounds[1], previewW = bounds[2], previewH = bounds[3];
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlTextureId());
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(left, top + previewH, 0, 0, 1);
        t.addVertexWithUV(left + previewW, top + previewH, 0, 1, 1);
        t.addVertexWithUV(left + previewW, top, 0, 1, 0);
        t.addVertexWithUV(left, top, 0, 0, 0);
        t.draw();
    }
    public synchronized void updatePreview(ByteBuffer buffer, ReadableDimension size) {
        if (!previewEnabled) return;
        int count = size.getWidth() * size.getHeight();
        previewWidth = size.getWidth(); previewHeight = size.getHeight();
        if (pixels == null || pixels.length != count) pixels = new int[count];
        ByteBuffer data = buffer.duplicate();
        for (int i = 0; i < count; i++) {
            int b = data.get() & 255, g = data.get() & 255, r = data.get() & 255;
            data.get(); pixels[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        dirty = true;
    }
    @Override protected void activate(int id) {
        if (id == 1) renderer.setPaused(!renderer.isPaused());
        if (id == 2) previewEnabled = !previewEnabled;
        if (id == 3) { if (confirmCancel) renderer.cancel(); else confirmCancel = true; }
        initGui();
    }
    @Override protected void escape() { confirmCancel = true; initGui(); }
    public synchronized void releasePreview() {
        if (texture != null) { texture.deleteGlTexture(); texture = null; }
        pixels = null;
    }
    @Override public void onGuiClosed() {
        releasePreview();
        super.onGuiClosed();
    }
}
