package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.ReplayCloudClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets="com.gtnewhorizons.angelica.render.CloudRenderer", remap=false)
public abstract class MixinAngelicaCloudClock {
    // Angelica 2.2.14 (including its Java 17 class) stores ticks + partialTicks
    // in the first double local of render(IF)Z. Camera interpolation is unchanged.
    @ModifyVariable(method="render(IF)Z", at=@At("STORE"), ordinal=0)
    private double replayMod$cloudTime(double nativeTicks) { return ReplayCloudClock.ticks(nativeTicks); }
}
