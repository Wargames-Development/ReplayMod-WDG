package com.replaymod.render.mixin;

import com.replaymod.replay.compat.ReplayCloudClock;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderGlobal.class)
public abstract class MixinCloudClock {
    // The first double local is cloudTickCounter + partialTicks, before camera
    // translation. Leave partialTicks itself alone: it also interpolates camera/color.
    // The config plugin excludes this entire mixin when Angelica is present:
    // require=0 cannot bypass Mixin's prohibition on injecting into its overwrite.
    @ModifyVariable(method={"renderClouds", "renderCloudsFancy"}, at=@At("STORE"), ordinal=0, require=0)
    private double replayMod$cloudTime(double nativeTicks) { return ReplayCloudClock.ticks(nativeTicks); }
}
