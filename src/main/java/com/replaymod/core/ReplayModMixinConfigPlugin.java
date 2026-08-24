package com.replaymod.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;

import org.spongepowered.asm.lib.tree.ClassNode;

public class ReplayModMixinConfigPlugin implements IMixinConfigPlugin {
    static boolean hasClass(String name) throws IOException {
        return Launch.classLoader.getClassBytes(name) != null;
    }

    private final Logger logger = LogManager.getLogger("replaymod/mixin");
    private final boolean hasOF = hasClass("optifine.OptiFineForgeTweaker");
    private final boolean hasMCHeli = hasClass("mcheli.MCH_ClientProxy");

    {
        logger.debug("hasOF: " + hasOF);
        logger.debug("hasMCHeli: " + hasMCHeli);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("_OF")) return hasOF;
        if (mixinClassName.endsWith("_NoOF")) return !hasOF;
        if (mixinClassName.endsWith("_MCHeli")) return hasMCHeli;
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    public ReplayModMixinConfigPlugin() throws IOException {
    }
}
