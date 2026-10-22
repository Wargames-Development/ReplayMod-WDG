package com.replaymod.render.rendering;

import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.utils.WrappedTimer;
import com.replaymod.core.versions.MCVer;
import com.replaymod.pathing.player.AbstractTimelinePlayer;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.render.CameraPathExporter;
import com.replaymod.render.PNGWriter;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.FFmpegWriter;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.events.ReplayRenderCallback;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.gui.GuiRenderingDone;
import com.replaymod.render.gui.GuiVideoRenderer;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.render.metadata.MetadataInjector;
import com.replaymod.render.mixin.MainWindowAccessor;
import com.replaymod.render.mixin.WorldRendererAccessor;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;
import de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.audio.PositionedSoundRecord;
import com.replaymod.core.versions.Window;
import com.replaymod.core.versions.MCVer.SoundEvent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ReportedException;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.util.Timer;
import com.replaymod.core.versions.GLFW;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import com.replaymod.replay.gui.screen.GuiOpeningReplay;
import static com.replaymod.core.versions.MCVer.GlStateManager.*;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import static com.google.common.collect.Iterables.getLast;
import static com.replaymod.core.versions.MCVer.*;
import static com.replaymod.render.ReplayModRender.LOGGER;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;

public class VideoRenderer implements RenderInfo {
    private static final ResourceLocation SOUND_RENDER_SUCCESS = new ResourceLocation("replaymod", "render_success");
    private final Minecraft mc = MCVer.getMinecraft();
    private final RenderSettings settings;
    private final ReplayHandler replayHandler;
    private final Timeline timeline;
    private final Pipeline renderingPipeline;
    private final FFmpegWriter ffmpegWriter;
    private final CameraPathExporter cameraPathExporter;

    private int fps;
    private boolean mouseWasGrabbed;
    private boolean debugInfoWasShown;
    private Map<SoundCategory, Float> originalSoundLevels;
    private com.replaymod.render.audio.ExportAudio audio;
    private String stage = "";
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    private TimelinePlayer timelinePlayer;
    private Future<Void> timelinePlayerFuture;
    private ForceChunkLoadingHook forceChunkLoadingHook;
    private GuiOpeningReplay guiOpeningReplay;

    private int framesDone;
    private int totalFrames;

    private final GuiVideoRenderer gui;
    private boolean paused;
    private boolean cancelled;
    private volatile Throwable failureCause;

    private Framebuffer guiFramebuffer;
    private int displayWidth, displayHeight;

    public VideoRenderer(RenderSettings settings, ReplayHandler replayHandler, Timeline timeline) throws IOException {
        this.settings = settings;
        this.replayHandler = replayHandler;
        this.timeline = timeline;
        com.replaymod.replay.compat.DistantHorizonsReplay.requireTerrain();
        this.gui = new GuiVideoRenderer(this);
        if (settings.getRenderMethod() == RenderSettings.RenderMethod.BLEND) {
            BlendState.setState(new BlendState(settings.getOutputFile()));

            this.renderingPipeline = Pipelines.newBlendPipeline(this);
            this.ffmpegWriter = null;
        } else {
            FrameConsumer<BitmapFrame> frameConsumer;
            if (settings.getEncodingPreset() == RenderSettings.EncodingPreset.EXR) {
                throw new UnsupportedOperationException("EXR requires LWJGL3");
            } else if (settings.getEncodingPreset() == RenderSettings.EncodingPreset.PNG) {
                frameConsumer = new PNGWriter(settings.getOutputFile().toPath());
            } else {
                frameConsumer = new FFmpegWriter(this);
            }
            ffmpegWriter = frameConsumer instanceof FFmpegWriter ? (FFmpegWriter) frameConsumer : null;
            FrameConsumer<BitmapFrame> previewingFrameConsumer = new FrameConsumer<BitmapFrame>() {
                @Override
                public void consume(Map<Channel, BitmapFrame> channels) {
                    BitmapFrame bgra = channels.get(Channel.BRGA);
                    if (bgra != null) {
                        gui.updatePreview(bgra.getByteBuffer(), bgra.getSize());
                    }
                    frameConsumer.consume(channels);
                }

                @Override
                public void close() throws IOException {
                    frameConsumer.close();
                }
            };
            this.renderingPipeline = Pipelines.newPipeline(settings.getRenderMethod(), this, previewingFrameConsumer);
        }

        if (settings.isCameraPathExport()) {
            this.cameraPathExporter = new CameraPathExporter(settings);
        } else {
            this.cameraPathExporter = null;
        }
    }

    /**
     * Render this video.
     * @return {@code true} if rendering was successful, {@code false} if the user aborted rendering (or the window was closed)
     */
    public boolean renderVideo() throws Throwable {
        boolean finished = false;
        try {
        ReplayRenderCallback.Pre.EVENT.invoker().beforeRendering(this);

        setup();
        com.replaymod.replay.compat.DistantHorizonsReplay.beginExport();
        if (settings.isRenderAudio() && settings.getRenderMethod() != RenderSettings.RenderMethod.BLEND) audio = new com.replaymod.render.audio.ExportAudio(this);

        // Because this might take some time to prepare we'll render the GUI at least once to not confuse the user
        drawGui();

        stage = "Reading recorded aircraft poses...";
        drawGui();
        com.replaymod.replay.compat.ReplayAircraftExport.begin(replayHandler);
        stage = "Reading recorded terrain...";
        drawGui();
        com.replaymod.replay.compat.ReplayTerrainExport.begin(replayHandler,settings.isRecoverRecordedTerrain());
        stage = "";

        Timer timer = ((MinecraftAccessor) mc).getTimer();

        // Play up to one second before starting to render
        // This is necessary in order to ensure that all entities have at least two position packets
        // and their first position in the recording is correct.
        // Note that it is impossible to also get the interpolation between their latest position
        // and the one in the recording correct as there's no reliable way to tell when the server ticks
        // or when we should be done with the interpolation of the entity
        Optional<Integer> optionalVideoStartTime = timeline.getValue(TimestampProperty.PROPERTY, 0);
        if (optionalVideoStartTime.isPresent()) {
            int videoStart = optionalVideoStartTime.get();

            if (videoStart > 0) {
                int replayTime = Math.max(0, videoStart - 1000);
                timer.elapsedPartialTicks = timer.renderPartialTicks = 0;
                timer.timerSpeed = 1;
                // Position first, so sounds from the skipped part are not mistaken for current sounds.
                replayHandler.getReplaySender().sendPacketsTill(replayTime);
                if (audio != null) audio.beginWarmup();
                while (replayTime < videoStart) {
                    timer.elapsedTicks = 1;
                    int step = Math.min(50, videoStart - replayTime);
                    replayTime += step;
                    replayHandler.getReplaySender().sendPacketsTill(replayTime);
                    tick();
                    if (audio != null) audio.advanceWarmup(step);
                }
                if (audio != null) audio.endWarmup();
            }
        }

        ((WorldRendererAccessor) mc.renderGlobal).setRenderEntitiesStartupCounter(0);


        renderingPipeline.run();

        if (audio != null) {
            java.io.File wave = audio.getWaveFile(); audio.close(); audio = null;
            if (!cancelled && !hasFailed() && ffmpegWriter != null) {
                stage = "Finishing game audio...";
                drawGui();
                com.replaymod.render.audio.AudioMuxer.mux(this, ffmpegWriter.getVideoFile(), wave);
                stage = "";
            }
        }

        if (((MinecraftAccessor) mc).getCrashReporter() != null) {
            throw new ReportedException(((MinecraftAccessor) mc).getCrashReporter());
        }

        if (settings.isInjectSphericalMetadata()) {
            MetadataInjector.injectMetadata(settings.getRenderMethod(), settings.getOutputFile(),
                    settings.getTargetVideoWidth(), settings.getTargetVideoHeight(),
                    settings.getSphericalFovX(), settings.getSphericalFovY());
        }

        finish();
        finished = true;

        ReplayRenderCallback.Post.EVENT.invoker().afterRendering(this);

        if (failureCause != null) {
            throw failureCause;
        }

        return !cancelled;
        } finally {
            com.replaymod.replay.compat.ReplayAircraftExport.end();
            com.replaymod.replay.compat.ReplayTerrainExport.end();
            if (!finished) {
                cancel();
                try { renderingPipeline.close(); }
                finally { if (timelinePlayerFuture != null) finish(); }
            }
        }
    }

    @Override
    public float updateForNextFrame() {
        // because the jGui lib uses Minecraft's displayWidth and displayHeight values, update these temporarily
        MainWindowAccessor acc = (MainWindowAccessor) (Object) new com.replaymod.core.versions.Window(mc);
        int displayWidthBefore = acc.getFramebufferWidth();
        int displayHeightBefore = acc.getFramebufferHeight();
        acc.setFramebufferWidth(displayWidth);
        acc.setFramebufferHeight(displayHeight);

        if (!settings.isHighPerformance() || framesDone % fps == 0) {
            while (drawGui() && paused) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Updating the timer will cause the timeline player to update the game state
        if (audio != null) audio.beginFrame(framesDone);
        Timer timer = ((MinecraftAccessor) mc).getTimer();
        timer.updateTimer();
        int elapsedTicks = timer.elapsedTicks;

        executeTaskQueue();

        if (guiOpeningReplay != null) {
            guiOpeningReplay.handleInput();
        }

        while (elapsedTicks-- > 0) {
            tick();
        }
        com.replaymod.replay.compat.ReplayEffects.flush();
        if (audio != null) audio.endFrame();

        // change Minecraft's display size back
        acc.setFramebufferWidth(displayWidthBefore);
        acc.setFramebufferHeight(displayHeightBefore);

        if (cameraPathExporter != null) {
            cameraPathExporter.recordFrame(timer.renderPartialTicks);
        }

        framesDone++;
        return timer.renderPartialTicks;
    }

    @Override
    public RenderSettings getRenderSettings() {
        return settings;
    }

    private void setup() {
        timelinePlayer = new TimelinePlayer(replayHandler);
        timelinePlayerFuture = timelinePlayer.start(timeline);

        // FBOs are always used in 1.14+
        if (!OpenGlHelper.isFramebufferEnabled()) {
            Display.setResizable(false);
        }
        if (mc.gameSettings.showDebugInfo) {
            debugInfoWasShown = true;
            mc.gameSettings.showDebugInfo = false;
        }
        if (Mouse.isGrabbed()) {
            mouseWasGrabbed = true;
        }
        Mouse.setGrabbed(false);

        // The offline audio hook suppresses real-time output while retaining category levels.
        // Silent exports keep the legacy mute path.
        originalSoundLevels = new EnumMap<>(SoundCategory.class);
        for (SoundCategory category : SoundCategory.values()) {
            if (!settings.isRenderAudio() && category != SoundCategory.MASTER) {
                originalSoundLevels.put(category, mc.gameSettings.getSoundLevel(category));
                mc.gameSettings.setSoundLevel(category, 0);
            }
        }

        fps = settings.getFramesPerSecond();

        long duration = 0;
        for (Path path : timeline.getPaths()) {
            if (!path.isActive()) continue;

            // Prepare path interpolations
            path.updateAll();
            // Find end time
            Collection<Keyframe> keyframes = path.getKeyframes();
            if (keyframes.size() > 0) {
                duration = Math.max(duration, getLast(keyframes).getTime());
            }
        }

        totalFrames = (int) (duration*fps/1000);

        if (cameraPathExporter != null) {
            cameraPathExporter.setup(totalFrames);
        }

        updateDisplaySize();

        gui.toMinecraft().setWorldAndResolution(mc, new com.replaymod.core.versions.Window(mc).getScaledWidth(), new com.replaymod.core.versions.Window(mc).getScaledHeight());

        forceChunkLoadingHook = new ForceChunkLoadingHook(mc.renderGlobal);

        // Set up our own framebuffer to render the GUI to
        guiFramebuffer = new Framebuffer(displayWidth, displayHeight, true);
    }

    private void finish() {
        if (audio != null) {
            try { audio.close(); } catch (IOException error) { setFailure(error); }
            audio = null;
        }
        if (timelinePlayerFuture != null && !timelinePlayerFuture.isDone()) {
            timelinePlayerFuture.cancel(false);
        }
        // Tear down of the timeline player might only happen the next tick after it was cancelled
        if (timelinePlayer != null) timelinePlayer.onTick();

        // FBOs are always used in 1.14+
        if (!OpenGlHelper.isFramebufferEnabled()) {
            Display.setResizable(true);
        }
        mc.gameSettings.showDebugInfo = debugInfoWasShown;
        if (mouseWasGrabbed) {
            mc.mouseHelper.grabMouseCursor();
        }
        if (originalSoundLevels != null) {
            for (Map.Entry<SoundCategory, Float> entry : originalSoundLevels.entrySet()) {
                mc.gameSettings.setSoundLevel(entry.getKey(), entry.getValue());
            }
        }
        mc.displayGuiScreen(null);
        if (forceChunkLoadingHook != null) forceChunkLoadingHook.uninstall();

        if (!cancelled && !hasFailed() && cameraPathExporter != null) {
            try {
                cameraPathExporter.finish();
            } catch (IOException e) {
                setFailure(e);
            }
        }

        if (!cancelled && !hasFailed()) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.createPositionedSoundRecord(SOUND_RENDER_SUCCESS, 1));
        }

        try {
            if (!cancelled && !hasFailed()) {
                mc.displayGuiScreen(new com.replaymod.studio.StudioRenderDone(ffmpegWriter == null ? settings.getOutputFile() : ffmpegWriter.getVideoFile(), totalFrames));
            }
        } catch (FFmpegWriter.FFmpegStartupException e) {
            setFailure(e);
        }

        // Finally, resize the Minecraft framebuffer to the actual width/height of the window
        if (displayWidth > 0 && displayHeight > 0) resizeMainWindow(mc, displayWidth, displayHeight);
        if (guiFramebuffer != null) guiFramebuffer.deleteFramebuffer();
        gui.releasePreview();
        mc.getFramebuffer().bindFramebuffer(true);
        resetColor();
    }

    private void executeTaskQueue() {
        Queue<FutureTask<?>> scheduledTasks = ((MinecraftAccessor) mc).getScheduledTasks();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (scheduledTasks) {
            while (!scheduledTasks.isEmpty()) {
                scheduledTasks.poll().run();
            }
        }

        if (mc.currentScreen instanceof GuiOpeningReplay) {
            guiOpeningReplay = (GuiOpeningReplay) mc.currentScreen;
        }

        mc.currentScreen = gui.toMinecraft();
    }

    private void tick() {
        mc.runTick();
        com.replaymod.render.audio.ExportAudio.tick();
    }

    public boolean drawGui() {
        // Capture resizes Minecraft to the output frame. Progress/input must
        // always use the physical window, including terrain-wait draws made
        // between capture passes. Restore capture dimensions even on failure.
        if (displaySizeChanged()) {
            updateDisplaySize();
            guiFramebuffer.createBindFramebuffer(displayWidth,displayHeight);
        }
        return drawAtWindowSize(mc,displayWidth,displayHeight,this::drawWindowGui);
    }
    static boolean drawAtWindowSize(Minecraft mc,int width,int height,java.util.function.BooleanSupplier draw) {
        int captureWidth=mc.displayWidth, captureHeight=mc.displayHeight;
        mc.displayWidth=width; mc.displayHeight=height;
        try {
            return draw.getAsBoolean();
        } finally { mc.displayWidth=captureWidth; mc.displayHeight=captureHeight; }
    }
    private boolean drawWindowGui() {
        Window window = new com.replaymod.core.versions.Window(mc);
        do {
            if (GLFW.glfwWindowShouldClose(window.getHandle()) || ((MinecraftAccessor) mc).getCrashReporter() != null) {
                return false;
            }

            pushMatrix();
            guiFramebuffer.bindFramebuffer(true);
            clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            enableTexture2D();
            resetColor();

            mc.entityRenderer.setupOverlayRendering();

            if (gui.width != window.getScaledWidth() || gui.height != window.getScaledHeight()) {
                gui.setWorldAndResolution(mc, window.getScaledWidth(), window.getScaledHeight());
            }

            // Events are polled on 1.13+ in mainWindow.update which is called later
            gui.toMinecraft().handleInput();

            int mouseX = Mouse.getX() * window.getScaledWidth() / mc.displayWidth;
            int mouseY = window.getScaledHeight() - Mouse.getY() * window.getScaledHeight() / mc.displayHeight - 1;

            gui.toMinecraft().updateScreen();
            gui.toMinecraft().drawScreen(mouseX, mouseY, 0);

            guiFramebuffer.unbindFramebuffer();
            popMatrix();
            pushMatrix();
            guiFramebuffer.framebufferRender(displayWidth, displayHeight);
            popMatrix();

            // Do not reset/resize the capture framebuffer or GUI between frames.
            // High performance mode controls draw frequency in updateForNextFrame.
            Display.update();
            if (Mouse.isGrabbed()) {
                Mouse.setGrabbed(false);
            }

            return !hasFailed() && !cancelled;
        } while (true);
    }

    private boolean displaySizeChanged() {
        int realWidth = new com.replaymod.core.versions.Window(mc).getWidth();
        int realHeight = new com.replaymod.core.versions.Window(mc).getHeight();
        if (realWidth == 0 || realHeight == 0) {
            // These can be zero on Windows if minimized.
            // Creating zero-sized framebuffers however will throw an error, so we never want to switch to zero values.
            return false;
        }
        return displayWidth != realWidth || displayHeight != realHeight;
    }

    private void updateDisplaySize() {
        displayWidth = new com.replaymod.core.versions.Window(mc).getWidth();
        displayHeight = new com.replaymod.core.versions.Window(mc).getHeight();
    }

    public int getFramesDone() {
        return framesDone;
    }

    @Override
    public ReadableDimension getFrameSize() {
        return new Dimension(settings.getVideoWidth(), settings.getVideoHeight());
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public int getVideoTime() { return framesDone * 1000 / fps; }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public void cancel() {
        if (ffmpegWriter != null) {
            ffmpegWriter.abort();
        }
        this.cancelled = true;
        renderingPipeline.cancel();
    }

    public boolean hasFailed() {
        return failureCause != null;
    }

    public synchronized void setFailure(Throwable cause) {
        if (this.failureCause != null) {
            LOGGER.error("Further failure during failed rendering: ", cause);
        } else {
            LOGGER.error("Failure during rendering: ", cause);
            this.failureCause = cause;
            cancel();
        }
    }

    private class TimelinePlayer extends AbstractTimelinePlayer {
        public TimelinePlayer(ReplayHandler replayHandler) {
            super(replayHandler);
        }

        @Override
        public long getTimePassed() {
            return getVideoTime();
        }
    }

    public static String[] checkCompat() {
        return null;
    }
}
