package com.replaymod.core.mixin;

import com.replaymod.core.versions.MCVer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.Redirect;
import com.replaymod.replay.InputReplayTimer;
import org.lwjgl.input.Mouse;

import java.io.IOException;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements MCVer.MinecraftMethodAccessor {
    private boolean earlyReturn;

    @Override
    public void replayModSetEarlyReturnFromRunTick(boolean earlyReturn) {
        this.earlyReturn = earlyReturn;
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;sendClickBlockToController(Z)V"), cancellable = true)
    private void doEarlyReturnFromRunTick(CallbackInfo ci) {
        if (earlyReturn) ci.cancel();
    }
    @Redirect(
            method = "runTick",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventDWheel()I", remap = false)
    )
    private int scroll() {
        int wheel = Mouse.getEventDWheel();
        InputReplayTimer.handleScroll(wheel);
        return wheel;
    }
}
