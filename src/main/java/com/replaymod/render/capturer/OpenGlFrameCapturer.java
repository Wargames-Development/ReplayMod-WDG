package com.replaymod.render.capturer;

import com.replaymod.core.versions.MCVer;
import com.replaymod.render.frame.OpenGlFrame;
import com.replaymod.render.rendering.Frame;
import com.replaymod.render.rendering.FrameCapturer;
import com.replaymod.render.utils.ByteBufferPool;
import de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import de.johni0702.minecraft.gui.utils.lwjgl.WritableDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.replaymod.core.versions.MCVer.GlStateManager.*;

import static com.replaymod.core.versions.MCVer.resizeMainWindow;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;

public abstract class OpenGlFrameCapturer<F extends Frame, D extends CaptureData> implements FrameCapturer<F> {
    protected final WorldRenderer worldRenderer;
    protected final RenderInfo renderInfo;
    protected int framesDone;
    private Framebuffer frameBuffer;

    protected final Minecraft mc = MCVer.getMinecraft();

    public OpenGlFrameCapturer(WorldRenderer worldRenderer, RenderInfo renderInfo) {
        this.worldRenderer = worldRenderer;
        this.renderInfo = renderInfo;
    }

    protected final ReadableDimension frameSize = new ReadableDimension() {
        @Override
        public int getWidth() {
            return getFrameWidth();
        }

        @Override
        public int getHeight() {
            return getFrameHeight();
        }

        @Override
        public void getSize(WritableDimension dest) {
            dest.setSize(getWidth(), getHeight());
        }
    };

    protected int getFrameWidth() {
        return renderInfo.getFrameSize().getWidth();
    }

    protected int getFrameHeight() {
        return renderInfo.getFrameSize().getHeight();
    }

    protected Framebuffer frameBuffer() {
        if (frameBuffer == null) {
            frameBuffer = mc.getFramebuffer();
        }
        return frameBuffer;
    }

    @Override
    public boolean isDone() {
        return framesDone >= renderInfo.getTotalFrames();
    }

    protected OpenGlFrame renderFrame(int frameId, float partialTicks) {
        return renderFrame(frameId, partialTicks, null);
    }

    protected OpenGlFrame renderFrame(int frameId, float partialTicks, D captureData) {
        com.replaymod.render.rendering.VideoRenderer renderer = renderInfo instanceof com.replaymod.render.rendering.VideoRenderer ? (com.replaymod.render.rendering.VideoRenderer) renderInfo : null;
        long nextProgress = 0;
        do {
            resizeMainWindow(mc, getFrameWidth(), getFrameHeight());
            pushMatrix();
            try {
                frameBuffer().bindFramebuffer(true);
                try {
                    clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    enableTexture2D();
                    worldRenderer.renderWorld(partialTicks, captureData);
                } finally {
                    frameBuffer().unbindFramebuffer();
                }
            } finally {
                popMatrix();
            }

            if (renderer == null || com.replaymod.replay.compat.DistantHorizonsReplay.renderReady()) break;
            renderer.setStage("Loading Distant Horizons terrain for this camera view...");
            if (System.currentTimeMillis() >= nextProgress) {
                if (!renderer.drawGui()) throw new IllegalStateException("Render cancelled while loading LOD terrain.");
                nextProgress = System.currentTimeMillis() + 200;
            }
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while loading LOD terrain", interrupted); }
        } while (true);
        if (renderer != null) renderer.setStage("");

        return captureFrame(frameId, captureData);
    }

    protected OpenGlFrame captureFrame(int frameId, D captureData) {
        ByteBuffer buffer = ByteBufferPool.allocate(getFrameWidth() * getFrameHeight() * 4);
        frameBuffer().bindFramebuffer(true);
        GL11.glReadPixels(0, 0, getFrameWidth(), getFrameHeight(), GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);
        frameBuffer().unbindFramebuffer();
        buffer.rewind();

        return new OpenGlFrame(frameId, new Dimension(getFrameWidth(), getFrameHeight()), 4, buffer);
    }

    @Override
    public void close() throws IOException {
    }
}
