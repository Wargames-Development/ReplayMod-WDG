package com.replaymod.render.hooks;

import com.replaymod.core.events.PreRenderHandCallback;
import com.replaymod.core.versions.MCVer;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.capturer.CaptureData;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.capturer.WorldRenderer;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import net.minecraft.client.Minecraft;


import cpw.mods.fml.common.FMLCommonHandler;

import java.io.IOException;

public class EntityRendererHandler extends EventRegistrations implements WorldRenderer {
    public final Minecraft mc = MCVer.getMinecraft();

    protected final RenderSettings settings;

    private final RenderInfo renderInfo;

    public CaptureData data;

    public boolean omnidirectional;

    public EntityRendererHandler(RenderSettings settings, RenderInfo renderInfo) {
        this.settings = settings;
        this.renderInfo = renderInfo;

        on(PreRenderHandCallback.EVENT, () -> omnidirectional);

        ((IEntityRenderer) mc.entityRenderer).replayModRender_setHandler(this);
        register();
    }

    @Override
    public void renderWorld(final float partialTicks, CaptureData data) {
        this.data = data;
        renderWorld(partialTicks, 0);
    }

    public void renderWorld(float partialTicks, long finishTimeNano) {
        FMLCommonHandler.instance().onRenderTickStart(partialTicks);

        if (mc.theWorld != null && mc.thePlayer != null) {
            mc.entityRenderer.renderWorld(partialTicks, finishTimeNano);
        }

        FMLCommonHandler.instance().onRenderTickEnd(partialTicks);
    }

    @Override
    public void close() throws IOException {
        ((IEntityRenderer) mc.entityRenderer).replayModRender_setHandler(null);
        unregister();
    }

    @Override
    public void setOmnidirectional(boolean omnidirectional) {
        this.omnidirectional = omnidirectional;
    }

    public RenderSettings getSettings() {
        return this.settings;
    }

    public RenderInfo getRenderInfo() {
        return this.renderInfo;
    }

    public interface IEntityRenderer {
        void replayModRender_setHandler(EntityRendererHandler handler);
        EntityRendererHandler replayModRender_getHandler();
    }
}
