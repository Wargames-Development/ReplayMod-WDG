package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayShaderClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Angelica replaces vanilla updateRenderers and its force-loading flag entirely. */
@Pseudo
@Mixin(targets="org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer",remap=false)
public abstract class MixinAngelicaTerrain {
    @ModifyVariable(method="prepareFrame",at=@At("HEAD"),argsOnly=true,index=3,require=1)
    private boolean replayMod$finishTerrain(boolean immediate) {
        return immediate || ReplayShaderClock.export()!=null;
    }
}
