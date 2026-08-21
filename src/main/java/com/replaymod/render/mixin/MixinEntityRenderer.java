package com.replaymod.render.mixin;

import com.replaymod.render.hooks.EntityRendererHandler;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements EntityRendererHandler.IEntityRenderer {
    private EntityRendererHandler replayModRender_handler;

    @Override
    public void replayModRender_setHandler(EntityRendererHandler handler) {
        this.replayModRender_handler = handler;
    }

    @Override
    public EntityRendererHandler replayModRender_getHandler() {
        return replayModRender_handler;
    }
}
