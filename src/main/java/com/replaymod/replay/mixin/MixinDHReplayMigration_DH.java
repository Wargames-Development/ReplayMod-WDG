package com.replaymod.replay.mixin;

import com.replaymod.replay.compat.DistantHorizonsReplay;
import net.minecraft.client.Minecraft;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure", remap = false)
public abstract class MixinDHReplayMigration_DH {
    @Inject(method = "getPre23SaveFolder", at = @At("HEAD"), cancellable = true, require = 0)
    private void noTerrainMigration(CallbackInfoReturnable<File> ci) {
        if (DistantHorizonsReplay.isActive()) {
            File unused = new File(Minecraft.getMinecraft().mcDataDir, ".replaymod-no-lod-migration");
            if (unused.exists()) throw new IllegalStateException("Reserved replay LOD migration sentinel already exists: " + unused);
            ci.setReturnValue(unused);
        }
    }
}
