package com.replaymod.replay.mixin;

import com.google.gson.JsonObject;
import com.replaymod.replaystudio.us.myles.ViaVersion.api.data.MappingDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ReplayStudio's embedded ViaVersion was compiled against Gson 2.3.1 and calls
 * JsonObject#size(), while Minecraft 1.7.10 supplies Gson 2.2.4.
 *
 * entrySet().size() is equivalent here and is available in the Minecraft
 * runtime Gson, so redirect that single incompatible call instead of replacing
 * Minecraft/Forge's Gson globally.
 */
@Mixin(value = MappingDataLoader.class, remap = false)
public abstract class MixinReplayStudioGson224 {

    @Redirect(
            method = "indexedObjectToMap",
            at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonObject;size()I"),
            remap = false
    )
    private static int replaymod$gson224ObjectSize(JsonObject object) {
        return object.entrySet().size();
    }
}
