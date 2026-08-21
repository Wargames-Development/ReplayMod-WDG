package com.replaymod.compat;

import com.replaymod.compat.optifine.DisableFastRender;
import com.replaymod.core.Module;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.replaymod.compat.oranges17animations.HideInvisibleEntities;
import com.replaymod.compat.bettersprinting.DisableBetterSprinting;

public class ReplayModCompat implements Module {
    public static Logger LOGGER = LogManager.getLogger();

    @Override
    public void initClient() {
        new DisableFastRender().register();
        new HideInvisibleEntities().register();
        DisableBetterSprinting.register();
    }

}
