package com.replaymod.replay.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPlayer.class)
public interface EntityPlayerAccessor extends EntityLivingBaseAccessor {
    @Accessor
    ItemStack getItemInUse();
    @Accessor
    void setItemInUse(ItemStack value);
    @Accessor
    int getItemInUseCount();
    @Accessor
    void setItemInUseCount(int value);
}
