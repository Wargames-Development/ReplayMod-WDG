package com.replaymod.replay.compat;

import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.hooks.EntityRendererHandler;
import net.minecraft.client.Minecraft;

/** Shader animation follows source time; temporal filters follow output frames. */
public final class ReplayShaderClock {
    private ReplayShaderClock() { }

    public static RenderInfo export() {
        if (!ReplayVehicleClock.active()) return null;
        Minecraft mc = Minecraft.getMinecraft();
        EntityRendererHandler handler = ((EntityRendererHandler.IEntityRenderer) mc.entityRenderer)
                .replayModRender_getHandler();
        return handler == null ? null : handler.getRenderInfo();
    }

    public static float animationSeconds(long replayMillis) {
        return Math.floorMod(replayMillis, 3600000L) / 1000.0F;
    }

    public static float frameSeconds(RenderInfo export, long replayMillis, long previousMillis) {
        if (export != null) return 1.0F / export.getRenderSettings().getFramesPerSecond();
        // Seeking is not a long rendered frame. Paused previews must not feed
        // zero into shader expressions which divide by smoothed frameTime.
        long delta = previousMillis < 0 ? 0 : replayMillis - previousMillis;
        return delta > 0 && delta <= 100 ? delta / 1000.0F : 1.0F / 60;
    }

    public static int frameCounter(RenderInfo export) {
        // updateForNextFrame advances framesDone before the world is drawn.
        // Terrain retries and additional views must retain this frame's jitter.
        return Math.floorMod(Math.max(0, export.getFramesDone() - 1), 720720);
    }
}
