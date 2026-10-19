package com.replaymod.render.rendering;

import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.versions.MCVer;
import com.replaymod.render.capturer.WorldRenderer;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.processor.GlToAbsoluteDepthProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ReportedException;
import net.minecraft.crash.CrashReport;
import com.replaymod.core.versions.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.replaymod.core.versions.MCVer.getMinecraft;

public class Pipeline<R extends Frame, P extends Frame> implements Runnable {

    private final WorldRenderer worldRenderer;
    private final FrameCapturer<R> capturer;
    private final FrameProcessor<R, P> processor;
    private final GlToAbsoluteDepthProcessor depthProcessor;
    private int consumerNextFrame;
    private final Object consumerLock = new Object();
    private final FrameConsumer<P> consumer;

    private volatile boolean abort;
    private boolean closed;

    public Pipeline(WorldRenderer worldRenderer, FrameCapturer<R> capturer, FrameProcessor<R, P> processor, FrameConsumer<P> consumer) {
        this.worldRenderer = worldRenderer;
        this.capturer = capturer;
        this.processor = processor;
        this.consumer = consumer;

        float near = 0.05f;
        float far = getMinecraft().gameSettings.renderDistanceChunks * 16 * 4;
        this.depthProcessor = new GlToAbsoluteDepthProcessor(near, far);
    }

    @Override
    public synchronized void run() {
        consumerNextFrame = 0;
        int processors = Runtime.getRuntime().availableProcessors();
        int processThreads = Math.max(1, Math.min(4, processors - 2));
        ExecutorService processService = new ThreadPoolExecutor(processThreads, processThreads,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(2) {
                    @Override
                    public boolean offer(Runnable runnable) {
                        try {
                            put(runnable);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        return true;
                    }
                }, new ThreadPoolExecutor.DiscardPolicy());

        try {
            Minecraft mc = MCVer.getMinecraft();
            while (!capturer.isDone() && !abort) {
                if (GLFW.glfwWindowShouldClose(new com.replaymod.core.versions.Window(mc).getHandle()) || ((MinecraftAccessor) mc).getCrashReporter() != null) {
                    break;
                }
                Map<Channel, R> rawFrame = capturer.process();
                if (rawFrame != null) {
                    processService.submit(new ProcessTask(rawFrame));
                }
            }
        } catch (RuntimeException | Error failure) {
            cancel();
            throw failure;
        } finally {
            processService.shutdown();
            try {
                processService.awaitTermination(30, TimeUnit.SECONDS);
                if (!processService.isTerminated()) {
                    cancel();
                    processService.shutdownNow();
                    processService.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                processService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            close();
        }
    }

    /** Also used when setup fails before run() takes ownership of the pipeline. */
    public synchronized void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        for (AutoCloseable resource : new AutoCloseable[]{worldRenderer, capturer, processor, consumer}) {
            try { resource.close(); }
            catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw new ReportedException(CrashReport.makeCrashReport(failure, "Cleaning up rendering pipeline"));
    }

    public void cancel() {
        abort = true;
        synchronized (consumerLock) {
            consumerLock.notifyAll();
        }
    }

    private class ProcessTask implements Runnable {
        private final Map<Channel, R> rawChannels;

        public ProcessTask(Map<Channel, R> rawChannels) {
            this.rawChannels = rawChannels;
        }

        @Override
        public void run() {
            try {
                Integer frameId = null;
                Map<Channel, P> processedChannels = new HashMap<>();
                for (Map.Entry<Channel, R> entry : rawChannels.entrySet()) {
                    P processedFrame = processor.process(entry.getValue());
                    if (entry.getKey() == Channel.DEPTH && processedFrame instanceof BitmapFrame) {
                        depthProcessor.process((BitmapFrame) processedFrame);
                    }
                    processedChannels.put(entry.getKey(), processedFrame);
                    frameId = processedFrame.getFrameId();
                }
                if (frameId == null) {
                    return;
                }
                synchronized (consumerLock) {
                    while (consumerNextFrame != frameId && !abort) {
                        try {
                            consumerLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (abort) return;
                    consumer.consume(processedChannels);
                    consumerNextFrame++;
                    consumerLock.notifyAll();
                }
            } catch (Throwable t) {
                cancel();
                CrashReport crashReport = CrashReport.makeCrashReport(t, "Processing frame");
                MCVer.getMinecraft().crashed(crashReport);
            }
        }
    }
}
