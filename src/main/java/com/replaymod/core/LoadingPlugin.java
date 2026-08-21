package com.replaymod.core;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import cpw.mods.fml.relauncher.CoreModManager;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import com.replaymod.core.asm.GLErrorTransformer;
import com.replaymod.core.asm.GLStateTrackerTransformer;

import java.util.ArrayList;
import java.util.List;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions("com.replaymod.core.asm.")
public class LoadingPlugin implements IFMLLoadingPlugin {

    public LoadingPlugin() {
        boolean deobfuscatedEnvironment = Launch.blackboard.get("fml.deobfuscatedEnvironment") != Boolean.FALSE;

        if (deobfuscatedEnvironment) {
            // Outside of the dev env, this is the job of the tweaker.
            MixinBootstrap.init();
        }

        Mixins.addConfiguration("mixins.core.replaymod.json");
        Mixins.addConfiguration("mixins.recording.replaymod.json");
        Mixins.addConfiguration("mixins.render.replaymod.json");
        Mixins.addConfiguration("mixins.render.blend.replaymod.json");
        Mixins.addConfiguration("mixins.replay.replaymod.json");
        Mixins.addConfiguration("mixins.compat.shaders.replaymod.json");
        Mixins.addConfiguration("mixins.extras.playeroverview.replaymod.json");

        /*
         * Packaged runtime:
         * ReplayMod is both a coremod and a normal @Mod, so Forge must re-examine
         * the jar after the coremod phase.
         *
         * ForgeGradle 1.2 dev runtime:
         * The raw project jar itself is placed on the launch classpath. Marking
         * that jar as a reparseable coremod makes FML's classpath ModDiscoverer
         * deliberately skip it, which prevents ReplayModBackend's @Mod
         * annotation from ever being discovered. Leave the raw jar alone in a
         * deobfuscated dev environment so normal classpath discovery can see it.
         */
        if (!deobfuscatedEnvironment) {
            CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                URL location = codeSource.getLocation();
                try {
                    File file = new File(location.toURI());
                    if (file.isFile()) {
                        CoreModManager.getLoadedCoremods().remove(file.getName());
                        if (!CoreModManager.getReparseableCoremods().contains(file.getName())) {
                            CoreModManager.getReparseableCoremods().add(file.getName());
                        }
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            } else {
                LogManager.getLogger().warn("No CodeSource, if this is not a development environment we might run into problems!");
                LogManager.getLogger().warn(getClass().getProtectionDomain());
            }
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        List<String> transformers = new ArrayList<>();
        if ("true".equals(System.getProperty("replaymod.glerrors", "false"))) {
            transformers.add(GLErrorTransformer.class.getName());
        }
        transformers.add(GLStateTrackerTransformer.class.getName());
        return transformers.stream().toArray(String[]::new);
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

}
