package com.replaymod.replay.mixin;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderer.class)
public interface FirstPersonRendererAccessor {
    @Accessor
    void setItemToRender(ItemStack value);
    @Accessor
    void setEquippedItemSlot(int value);
    @Accessor
    void setEquippedProgress(float value);
    @Accessor
    void setPrevEquippedProgress(float value);
}
