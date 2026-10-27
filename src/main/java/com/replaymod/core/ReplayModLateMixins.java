package com.replaymod.core;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import java.util.*;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Ordinary mods are not yet visible when the early Minecraft mixins are selected. */
@LateMixin
public final class ReplayModLateMixins implements ILateMixinLoader {
    @Override public String getMixinConfig() { return "mixins.replaymod.mchr.late.json"; }
    @Override public List<String> getMixins(Set<String> loadedMods) {
        if (!FMLLaunchHandler.side().isClient()) return Collections.emptyList();
        return clientMixins(loadedMods);
    }
    public static List<String> clientMixins(Set<String> loadedMods) {
        List<String> mixins=new ArrayList<>(aircraftMixins(loadedMods));
        if (loadedMods.contains("angelica")) mixins.addAll(Arrays.asList(
            "MixinAngelicaCloudClock", "MixinAngelicaShaderTimer", "MixinAngelicaShaderFrameCounter",
            "MixinAngelicaShaderHand", "MixinAngelicaTerrain", "MixinAngelicaTerrainUploads",
            "MixinAngelicaVisibility", "MixinAngelicaShadow"));
        if (loadedMods.contains("flansmod")) mixins.add("MixinFMURSoundClock_FMUR");
        return mixins;
    }
    public static List<String> aircraftMixins(Set<String> loadedMods) {
        return loadedMods.contains("mcheli") ? Arrays.asList(
            "MixinMCHClientProxy_MCHeli", "MixinMCHClientCommonTickHandler_MCHeli",
            "MixinMCHAircraftPose_MCHeli", "MixinMCHFarVehicle_MCHeli", "MixinMCHExportPose_MCHeli", "MixinMCHTerrainOcclusion_MCHeli",
            "MixinMCHTrackerClock_MCHeli", "MixinMCHSnapshotClock_MCHeli",
            "MixinMCHTargetMarker_MCHeli", "MixinMCHSoundUpdater_MCHeli", "MixinMCHRiders_MCHeli") : Collections.emptyList();
    }
}
