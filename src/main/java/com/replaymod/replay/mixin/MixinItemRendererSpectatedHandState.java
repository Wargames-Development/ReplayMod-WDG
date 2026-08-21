package com.replaymod.replay.mixin;

import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the 1.7.10 first-person hand use the actual spectated player's render
 * state instead of the synthetic ReplayMod camera player.
 *
 * In 1.7.10 ItemRenderer renders first-person arms using mc.thePlayer. During a
 * replay mc.thePlayer is CameraEntity while mc.renderViewEntity is the recorded
 * player being spectated. Third-person/F5 rendering uses that real player and
 * remains correctly lit, so the first-person arm should use the same player's
 * packed light value and skin immediately before RenderPlayer draws the arm.
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererSpectatedHandState {
    @Shadow private Minecraft mc;

    @Redirect(
            method = "renderItemInFirstPerson",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/RenderPlayer;renderFirstPersonArm(Lnet/minecraft/entity/player/EntityPlayer;)V"
            )
    )
    private void replayModReplay_renderSpectatedPlayerArm(RenderPlayer renderer, EntityPlayer originalPlayer) {
        Entity view = this.mc.renderViewEntity;

        if (this.mc.thePlayer instanceof CameraEntity
                && view instanceof EntityPlayer
                && view != this.mc.thePlayer) {
            EntityPlayer spectatedPlayer = (EntityPlayer) view;

            // Match the packed light source used by the normal/F5 entity renderer.
            int packedLight = spectatedPlayer.getBrightnessForRender(0.0F);
            int lightX = packedLight % 65536;
            int lightY = packedLight / 65536;
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit,
                    (float) lightX,
                    (float) lightY
            );

            // Ensure the skin bind occurs on the normal texture unit immediately
            // before the arm display list is rendered.
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            if (spectatedPlayer instanceof AbstractClientPlayer) {
                this.mc.getTextureManager().bindTexture(
                        ((AbstractClientPlayer) spectatedPlayer).getLocationSkin()
                );
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            renderer.renderFirstPersonArm(spectatedPlayer);
            return;
        }

        renderer.renderFirstPersonArm(originalPlayer);
    }
}
