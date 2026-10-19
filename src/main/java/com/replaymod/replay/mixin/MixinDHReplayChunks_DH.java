package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.DistantHorizonsReplay;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.forge17.ForgeClientProxy", remap = false)
public abstract class MixinDHReplayChunks_DH {
    @Inject(method = "onClientChunkFilled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void preserveRecordedTerrain(Chunk chunk, CallbackInfo ci) {
        if (DistantHorizonsReplay.isActive()) { DistantHorizonsReplay.protectTerrain(); ci.cancel(); }
    }
}
