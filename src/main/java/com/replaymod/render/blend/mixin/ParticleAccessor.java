package com.replaymod.render.blend.mixin;

import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityFX.class)
public interface ParticleAccessor extends EntityAccessor {

}
