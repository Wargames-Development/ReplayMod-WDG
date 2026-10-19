package com.replaymod.replay.mixin;

import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recorded pilots must remain remote players to MCHR, even while spectated.
 * Its local-player identity checks also select client control/physics branches;
 * substituting a recorded pilot couples vehicle state to observer input.
 */
@Pseudo
@Mixin(targets = "mcheli.MCH_ClientProxy", remap = false)
public abstract class MixinMCHClientProxy_MCHeli {
    @Inject(
            method = "getClientPlayer",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void replayModCompat$keepObserverIdentity(CallbackInfoReturnable<Entity> cir) {
        Entity player = Minecraft.getMinecraft().thePlayer;
        if (player instanceof CameraEntity) {
            cir.setReturnValue(player);
        }
    }
}
