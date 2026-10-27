package com.replaymod.core;

import com.google.gson.*;
import com.replaymod.replay.compat.ReplayAircraftExport;
import com.replaymod.replay.mixin.MixinMCHExportPose_MCHeli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReplayModLateMixinsTest {
    @Test public void mchrHooksAreSelectedAfterForgeDiscoversTheModWithoutProbingEarlyClassVisibility() throws Exception {
        assertTrue(ReplayModLateMixins.aircraftMixins(Collections.emptySet()).isEmpty());
        List<String> hooks=ReplayModLateMixins.aircraftMixins(Collections.singleton("mcheli"));
        assertEquals(11,hooks.size()); assertTrue(hooks.contains("MixinMCHExportPose_MCHeli"));
        assertTrue(hooks.contains("MixinMCHTerrainOcclusion_MCHeli"));
        assertTrue(hooks.contains("MixinMCHRiders_MCHeli"));
        assertTrue(hooks.contains("MixinMCHSoundUpdater_MCHeli"));
        assertTrue(hooks.contains("MixinMCHTargetMarker_MCHeli"));
        JsonObject early=config("mixins.replay.replaymod.json");
        for(JsonElement mixin:early.getAsJsonArray("client")) assertFalse("MCHR must not be silently disabled by an early class probe",mixin.getAsString().endsWith("_MCHeli"));
        JsonObject late=config(new ReplayModLateMixins().getMixinConfig());
        assertTrue(late.get("required").getAsBoolean()); assertFalse(late.has("plugin"));
        assertTrue(ReplayAircraftExport.PoseHook.class.isAssignableFrom(MixinMCHExportPose_MCHeli.class));
        assertTrue("Forge's late-loader annotation must be present",ReplayModLateMixins.class.isAnnotationPresent(com.gtnewhorizon.gtnhmixins.LateMixin.class));
    }
    private JsonObject config(String name) throws IOException {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name,input);
            return new JsonParser().parse(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test public void explosionSoundClockUsesLateModDiscoveryToo() throws Exception {
        assertEquals(Collections.singletonList("MixinFMURSoundClock_FMUR"),ReplayModLateMixins.clientMixins(Collections.singleton("flansmod")));
        assertFalse(ReplayModLateMixins.clientMixins(Collections.emptySet()).contains("MixinFMURSoundClock_FMUR"));
        for(JsonElement mixin:config("mixins.replay.replaymod.json").getAsJsonArray("client"))
            assertNotEquals("MixinFMURSoundClock_FMUR",mixin.getAsString());
    }
}
