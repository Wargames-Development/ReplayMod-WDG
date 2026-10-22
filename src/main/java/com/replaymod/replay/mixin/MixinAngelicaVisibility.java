package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayShaderClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Offline frames cannot use a visibility result from an earlier camera view. */
@Pseudo
@Mixin(targets="org.embeddedt.embeddium.impl.render.chunk.lists.RenderListManager",remap=false)
public abstract class MixinAngelicaVisibility {
    @Shadow public abstract void finishPreviousGraphUpdate();
    @Inject(method="submitSearch",at=@At("RETURN"),require=1)
    private void replayMod$joinCurrentView(CallbackInfo ci) {
        if (ReplayShaderClock.export()!=null) finishPreviousGraphUpdate();
    }
}
