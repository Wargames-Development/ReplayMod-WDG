package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayRenderViewContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCHeli-R asks its client proxy for the "client player" while rendering
 * aircraft, seats and first-person-sensitive parts.  In a replay that accessor
 * normally returns ReplayMod's CameraEntity, so the recorded pilot/passenger is
 * treated as a remote player and first-person model decisions are wrong.
 *
 * Only substitute the recorded spectated player inside RenderManager's entity
 * render scope.  Outside rendering MCHeli keeps seeing the real CameraEntity,
 * preventing replayed players from entering MCHeli's local control/physics
 * branches.
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
    private void replayModCompat$useSpectatedPlayerForRendering(CallbackInfoReturnable<Entity> cir) {
        EntityPlayer spectated = ReplayRenderViewContext.getSpectatedPlayerForEntityRender();
        if (spectated != null) {
            cir.setReturnValue(spectated);
        }
    }
}
