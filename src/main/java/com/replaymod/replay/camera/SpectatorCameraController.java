package com.replaymod.replay.camera;

import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.mixin.EntityPlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Mouse;

import java.util.Arrays;

import static com.replaymod.core.versions.MCVer.*;

public class SpectatorCameraController implements CameraController {
    private final CameraEntity camera;

    public SpectatorCameraController(CameraEntity camera) {
        this.camera = camera;
    }

    @Override
    public void update(float partialTicksPassed) {
        Minecraft mc = getMinecraft();
        if (mc.gameSettings.keyBindSneak.isPressed()) {
            ReplayModReplay.instance.getReplayHandler().spectateCamera();
        }

        // Soak up all remaining key presses
        for (KeyBinding binding : Arrays.asList(mc.gameSettings.keyBindAttack, mc.gameSettings.keyBindUseItem,
                mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak, mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack, mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight)) {
            //noinspection StatementWithEmptyBody
            while (binding.isPressed());
        }

        Mouse.updateCursor();

        // Always make sure the camera is in the exact same spot as the spectated entity
        // This is necessary as some rendering code for the hand doesn't respect the view entity
        // and always uses mc.thePlayer
        Entity view = mc.renderViewEntity;
        if (view != null && view != camera) {
            camera.setCameraPosRot(mc.renderViewEntity);
            // If it's a player, also 'steal' its inventory so the rendering code knows what item to render
            if (view instanceof EntityPlayer) {
                EntityPlayer viewPlayer = (EntityPlayer) view;
                camera.inventory = viewPlayer.inventory;
                EntityPlayerAccessor cameraA = (EntityPlayerAccessor) camera;
                EntityPlayerAccessor viewPlayerA = (EntityPlayerAccessor) viewPlayer;
                cameraA.setItemInUse(viewPlayerA.getItemInUse());
                cameraA.setItemInUseCount(viewPlayerA.getItemInUseCount());
            }
        }
    }

    @Override
    public void increaseSpeed() {

    }

    @Override
    public void decreaseSpeed() {

    }
}
