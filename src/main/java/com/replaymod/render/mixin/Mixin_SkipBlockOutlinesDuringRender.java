package com.replaymod.render.mixin;

import com.replaymod.render.hooks.EntityRendererHandler;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class Mixin_SkipBlockOutlinesDuringRender {
    @Redirect(method = "renderWorld", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/RenderGlobal;drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)V"))
    private void replayModRender_drawSelectionBox(RenderGlobal renderer, EntityPlayer player,
                                                  MovingObjectPosition target, int pass, float partialTicks) {
        // Skip the entire invocation. Cancelling inside drawSelectionBox can
        // run Angelica's HEAD hook without its RETURN hook, leaving outline=true.
        if (((EntityRendererHandler.IEntityRenderer) this).replayModRender_getHandler() == null) {
            renderer.drawSelectionBox(player, target, pass, partialTicks);
        }
    }
}
