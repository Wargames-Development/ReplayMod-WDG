package com.replaymod.replay;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.WrappedTimer;
import com.replaymod.core.versions.MCVer;
import com.replaymod.replay.camera.CameraController;
import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.input.Mouse;
import cpw.mods.fml.common.FMLCommonHandler;
import com.replaymod.replay.gui.screen.GuiOpeningReplay;
import net.minecraft.client.renderer.entity.RenderManager;

import net.minecraft.client.multiplayer.WorldClient;

public class InputReplayTimer extends WrappedTimer {
    private final ReplayModReplay mod;
    private final Minecraft mc;

    public InputReplayTimer(Timer wrapped, ReplayModReplay mod) {
        super(wrapped);
        this.mod = mod;
        this.mc = mod.getCore().getMinecraft();
    }

    @Override
    public void updateTimer() {
        super.updateTimer();

        ReplayMod.instance.runTasks();

        // Code below only updates the current screen when a world and player is loaded. This may not be the case for
        // the GuiOpeningReplay screen resulting in a livelock.
        // To counteract that, we always update that screen (doesn't matter if we do it twice).
        if (mc.currentScreen instanceof GuiOpeningReplay) {
            mc.currentScreen.handleInput();
        }

        // If we are in a replay, we have to manually process key and mouse events as the
        // tick speed may vary or there may not be any ticks at all (when the replay is paused)
        if (mod.getReplayHandler() != null && mc.theWorld != null && mc.thePlayer != null) {
            if (mc.currentScreen != null) {
                mc.currentScreen.handleInput();
            }
            if (mc.currentScreen == null || mc.currentScreen.allowUserInput) {
                // 1.8.9 and below has one giant tick function, so we try to only do keyboard & mouse as far as possible
                ((MCVer.MinecraftMethodAccessor) mc).replayModSetEarlyReturnFromRunTick(true);
                mc.runTick();
                ((MCVer.MinecraftMethodAccessor) mc).replayModSetEarlyReturnFromRunTick(false);
            }
        }
    }

    public static void handleScroll(int wheel) {
        if (wheel != 0) {
            ReplayHandler replayHandler = ReplayModReplay.instance.getReplayHandler();
            if (replayHandler != null) {
                CameraEntity cameraEntity = replayHandler.getCameraEntity();
                if (cameraEntity != null) {
                    CameraController controller = cameraEntity.getCameraController();
                    while (wheel > 0) {
                        controller.increaseSpeed();
                        wheel--;
                    }
                    while (wheel < 0) {
                        controller.decreaseSpeed();
                        wheel++;
                    }
                }
            }
        }
    }
}
