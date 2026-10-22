package com.replaymod.core;

import com.google.gson.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.launchwrapper.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class CloudMixinSelectionTest {
    private static final String CLOUD="com.replaymod.render.mixin.MixinCloudClock";
    @Test public void vanillaCloudHookIsEnabledWithoutAngelicaAndItsGateIsRegistered() throws Exception {
        assertTrue(selected(new URL[0]));
        try(InputStream input=getClass().getClassLoader().getResourceAsStream("mixins.render.replaymod.json")) {
            assertNotNull(input);
            JsonObject config=new JsonParser().parse(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(ReplayModMixinConfigPlugin.class.getName(),config.get("plugin").getAsString());
            assertTrue(config.getAsJsonArray("client").toString().contains("MixinCloudClock"));
        }
    }
    @Test public void installedAngelicaCoremodDisablesVanillaHookBeforeForgeDiscoversOrdinaryMods() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_ANGELICA"); assumeNotNull(file);
        try(ZipFile zip=new ZipFile(file)) {
            assertNotNull(zip.getEntry("com/gtnewhorizons/angelica/loading/AngelicaTweaker.class"));
            assertNotNull(zip.getEntry("com/gtnewhorizons/angelica/mixins/early/angelica/vbo/MixinRenderGlobal.class"));
            String manifest=new String(read(zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))),StandardCharsets.UTF_8);
            assertTrue("The probe must be the early core plugin, not a late mod class",manifest.contains("FMLCorePlugin: com.gtnewhorizons.angelica.loading.AngelicaTweaker"));
        }
        assertFalse("Never prepare vanilla local-variable injections on Angelica's overwritten methods",selected(new URL[]{new File(file).toURI().toURL()}));
        assertTrue("Angelica keeps its dedicated cloud clock",ReplayModLateMixins.clientMixins(Collections.singleton("angelica")).contains("MixinAngelicaCloudClock"));
    }
    private static boolean selected(URL[] urls) throws Exception {
        LaunchClassLoader previous=Launch.classLoader;
        try(LaunchClassLoader loader=new LaunchClassLoader(urls)) {
            Launch.classLoader=loader;
            ReplayModMixinConfigPlugin plugin=new ReplayModMixinConfigPlugin();
            assertTrue("Audio fixes must stay enabled",plugin.shouldApplyMixin("net.minecraft.client.audio.SoundManager","com.replaymod.render.mixin.MixinExportSound"));
            return plugin.shouldApplyMixin("net.minecraft.client.renderer.RenderGlobal",CLOUD);
        } finally { Launch.classLoader=previous; }
    }
    private static byte[] read(InputStream input) throws IOException {
        try(InputStream stream=input; ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count; while((count=stream.read(buffer))!=-1) output.write(buffer,0,count); return output.toByteArray();
        }
    }
}
