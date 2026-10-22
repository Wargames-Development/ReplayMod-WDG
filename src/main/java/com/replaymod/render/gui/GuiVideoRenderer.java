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
    private volatile boolean previewEnabled;
    private boolean confirmCancel, dirty;
    private int[] pixels;
    private int previewWidth, previewHeight, textureWidth, textureHeight;
    private DynamicTexture texture;
    private StudioRenderLayout layout;
    public GuiVideoRenderer(VideoRenderer renderer) {
        this.renderer = renderer;
        previewEnabled=renderer!=null && renderer.getRenderSettings().isShowPreview();
    }
    public GuiScreen toMinecraft() { return this; }
    @Override protected StudioViewport createViewport() { return new StudioViewport(width, height, 960, 540, true); }
    @Override protected void build() {
        layout = new StudioRenderLayout(canvasWidth, canvasHeight);
        int left = (canvasWidth - 620) / 2;
        if (confirmCancel) {
            button(4, canvasWidth / 2 - 170, canvasHeight / 2 + 16, 160, "\u00a7cYes, cancel render");
            button(5, canvasWidth / 2 + 10, canvasHeight / 2 + 16, 160, "Keep rendering");
        } else {
            buttonList.add(new RenderToggle(left, layout.buttonTop, renderer.isPaused()));
            button(3, left + 50, layout.buttonTop, 200, "\u00a7cCancel render");
            checkBox(2, left + 280, layout.buttonTop, 200, "Show preview", previewEnabled);
        }
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        // Overdraw a logical pixel to cover rounding at the window edges.
        drawRect(-1, -1, canvasWidth + 1, canvasHeight + 1, 0xFF101720);
        panel(16, 16, canvasWidth - 32, canvasHeight - 32, "RENDERING CINEMATIC");
        int done = renderer.getFramesDone(), total = renderer.getTotalFrames();
        String frameText = "Frame " + Math.min(done, total) + " / " + total;
        label(frameText, (canvasWidth - fontRendererObj.getStringWidth(frameText)) / 2, layout.progressTop - 14, TEXT);
        if (!renderer.getStage().isEmpty()) label(renderer.getStage(), (canvasWidth - fontRendererObj.getStringWidth(renderer.getStage())) / 2, 70, ACCENT);
        long elapsed = Math.max(0, System.currentTimeMillis() - started);
        long remaining = done > 0 ? elapsed * Math.max(0, total - done) / done : -1;
        label("Elapsed " + elapsed / 1000 + "s   |   " + (remaining < 0 ? "Preparing first frame..." : "About " + remaining / 1000 + "s remaining"), canvasWidth - 460, 54, MUTED);
        drawRect(32, layout.progressTop, canvasWidth - 32, layout.progressTop + 20, EDGE);
        drawRect(33, layout.progressTop + 1, 33 + (int) ((canvasWidth - 66L) * Math.min(done, total) / Math.max(1, total)), layout.progressTop + 19, ACCENT);
        if (previewEnabled) drawPreview();
        else previewMessage("Preview is off. Frames are still being exported.");
        if (confirmCancel) {
            drawRect(0, 0, canvasWidth, canvasHeight, 0xD0101720);
            panel(canvasWidth / 2 - 200, canvasHeight / 2 - 46, 400, 100, "CANCEL RENDER?");
            label("Stop now? The video will be incomplete.", canvasWidth / 2 - 170, canvasHeight / 2 - 12, 0xFFE46B67);
        }
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
        int[] bounds = previewBounds();
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
    private int[] previewBounds() {
        return renderer==null ? layout.preview(previewWidth,previewHeight)
            : layout.preview(renderer.getRenderSettings().getTargetVideoWidth(),renderer.getRenderSettings().getTargetVideoHeight());
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
        if (confirmCancel) {
            if (id == 4) renderer.cancel();
            if (id == 4 || id == 5) confirmCancel = false;
            initGui(); return;
        }
        if (id == 1) renderer.setPaused(!renderer.isPaused());
        if (id == 2) setPreviewEnabled(!previewEnabled);
        if (id == 3) confirmCancel = true;
        initGui();
    }
    public synchronized void setPreviewEnabled(boolean enabled) {
        previewEnabled=enabled;
        if (!enabled) releasePreview();
        if (renderer!=null) renderer.getRenderSettings().setShowPreview(enabled);
    }
    @Override protected void escape() { confirmCancel = !confirmCancel; initGui(); }
    private static final class RenderToggle extends net.minecraft.client.gui.GuiButton {
        private final boolean paused;
        RenderToggle(int x, int y, boolean paused) { super(1, x, y, 40, 20, paused ? "Resume render" : "Pause render"); this.paused = paused; }
        @Override public void drawButton(net.minecraft.client.Minecraft mc, int x, int y) {
            new StudioScreen.FlatButton(id, xPosition, yPosition, width, height, "").drawButton(mc, x, y);
            int cx = xPosition + width / 2, cy = yPosition + (height - 10) / 2;
            if (paused) {
                for (int i = 0; i < 10; i++) drawRect(cx - 5 + i, cy + i / 2, cx - 4 + i, cy + 10 - i / 2, TEXT);
            } else {
                drawRect(cx - 5, cy, cx - 2, cy + 10, TEXT);
                drawRect(cx + 1, cy, cx + 4, cy + 10, TEXT);
            }
        }
    }
    public synchronized void releasePreview() {
        if (texture != null) { texture.deleteGlTexture(); texture = null; }
        pixels = null;
    }
    @Override public void onGuiClosed() {
        releasePreview();
        super.onGuiClosed();
    }
}
