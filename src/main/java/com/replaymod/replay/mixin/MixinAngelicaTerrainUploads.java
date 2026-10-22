package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayShaderClock;
import com.replaymod.replay.compat.ReplayTerrainReadiness;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager",remap=false)
public abstract class MixinAngelicaTerrainUploads {
    @Inject(method="collectChunkBuildResults",at=@At("RETURN"),require=1)
    private void replayMod$uploaded(CallbackInfoReturnable<ArrayList<?>> ci) {
        if (ReplayShaderClock.export()!=null && !ci.getReturnValue().isEmpty()) ReplayTerrainReadiness.meshUploaded();
    }
}
