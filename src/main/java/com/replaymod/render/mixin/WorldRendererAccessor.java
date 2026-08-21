package com.replaymod.render.mixin;

import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(RenderGlobal.class)
public interface WorldRendererAccessor {
    @Accessor
    void setRenderEntitiesStartupCounter(int value);
}
