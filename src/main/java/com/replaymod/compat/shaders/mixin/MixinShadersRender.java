package com.replaymod.compat.shaders.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.replaymod.core.versions.MCVer;
import net.minecraftforge.client.ForgeHooksClient;

@Pseudo
@Mixin(targets = {
        "shadersmod/client/ShadersRender", // Pre Optifine 1.12.2 E1
        "net/optifine/shaders/ShadersRender" // Post Optifine 1.12.2 E1
}, remap = false)
public abstract class MixinShadersRender {

    @Inject(method = { "renderHand0", "renderHand1" }, at = @At("HEAD"), cancellable = true, remap = false)
    private static void replayModCompat_disableRenderHand0(
            EntityRenderer er,
            float partialTicks,
            int renderPass,
            CallbackInfo ci) {
        if (ForgeHooksClient.renderFirstPersonHand(MCVer.getMinecraft().renderGlobal, partialTicks, renderPass)) {
            ci.cancel();
        }
    }

}
