package com.replaymod.render.processor;

import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.frame.OpenGlFrame;
import de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class OpenGlToBitmapProcessorTest {
    @Test
    public void concurrentFramesKeepTheirOwnPixelsAndFlipVertically() throws Exception {
        final OpenGlToBitmapProcessor processor = new OpenGlToBitmapProcessor();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int frame = 0; frame < 64; frame++) {
                final int frameId = frame;
                tasks.add(() -> {
                    int width = 128;
                    int height = 64;
                    ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) pixels.putInt(frameId * 1000000 + y * width + x);
                    }
                    pixels.rewind();
                    BitmapFrame result = processor.process(new OpenGlFrame(frameId,
                            new Dimension(width, height), 4, pixels));
                    assertEquals(frameId, result.getFrameId());
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            assertEquals(frameId * 1000000 + (height - y - 1) * width + x,
                                    result.getByteBuffer().getInt((y * width + x) * 4));
                        }
                    }
                    return null;
                });
            }
            for (Future<Void> task : workers.invokeAll(tasks)) task.get();
        } finally {
            workers.shutdownNow();
        }
    }
}
