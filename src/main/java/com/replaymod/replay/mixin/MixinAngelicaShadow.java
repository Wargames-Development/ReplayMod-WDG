package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayShaderClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real-time shadow snapshot throttling is unsuitable for offline camera motion. */
@Pseudo
@Mixin(targets="net.coderbot.iris.pipeline.ShadowRenderer",remap=false)
public abstract class MixinAngelicaShadow {
    @Inject(method="shadowTerrainAlwaysRelays()Z",at=@At("RETURN"),cancellable=true,require=1)
    private void replayMod$redrawEveryView(CallbackInfoReturnable<Boolean> ci) {
        if (ReplayShaderClock.export()!=null) ci.setReturnValue(true);
    }
}
