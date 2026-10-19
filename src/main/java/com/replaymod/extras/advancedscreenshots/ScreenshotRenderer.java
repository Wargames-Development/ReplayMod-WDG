package com.replaymod.extras.advancedscreenshots;

import com.replaymod.core.versions.MCVer;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.render.rendering.Pipelines;
import de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import net.minecraft.client.Minecraft;
import com.replaymod.core.versions.Window;
import net.minecraft.crash.CrashReport;

import static com.replaymod.core.versions.MCVer.resizeMainWindow;

public class ScreenshotRenderer implements RenderInfo {

    private final Minecraft mc = MCVer.getMinecraft();

    private final RenderSettings settings;

    private int framesDone;

    public ScreenshotRenderer(RenderSettings settings) {
        this.settings = settings;
    }

    public boolean renderScreenshot() throws Throwable {
        Window window = new com.replaymod.core.versions.Window(mc);
        int widthBefore = window.getFramebufferWidth();
        int heightBefore = window.getFramebufferHeight();
        boolean hideGUIBefore = mc.gameSettings.hideGUI;
        ForceChunkLoadingHook clrg = null;
        try {
            mc.gameSettings.hideGUI = true;

            clrg = new ForceChunkLoadingHook(mc.renderGlobal);

            if (settings.getRenderMethod() == RenderSettings.RenderMethod.BLEND) {
                BlendState.setState(new BlendState(settings.getOutputFile()));
                Pipelines.newBlendPipeline(this).run();
            } else {
                Pipelines.newPipeline(settings.getRenderMethod(), this,
                        new ScreenshotWriter(settings.getOutputFile())).run();
            }

            return true;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            CrashReport report = CrashReport.makeCrashReport(e, "Creating Equirectangular Screenshot");
            MCVer.getMinecraft().crashed(report);
        } finally {
            if (clrg != null) clrg.uninstall();
            mc.gameSettings.hideGUI = hideGUIBefore;
            resizeMainWindow(mc, widthBefore, heightBefore);
            mc.getFramebuffer().bindFramebuffer(true);
            MCVer.GlStateManager.resetColor();
        }
        return false;
    }

    @Override
    public ReadableDimension getFrameSize() {
        return new Dimension(settings.getVideoWidth(), settings.getVideoHeight());
    }

    @Override
    public int getFramesDone() {
        return framesDone;
    }

    @Override
    public int getTotalFrames() {
        // render 2 frames, because only the second contains all frames fully loaded
        return 2;
    }

    @Override
    public float updateForNextFrame() {
        framesDone++;
        return ((com.replaymod.core.mixin.MinecraftAccessor) mc).getTimer().renderPartialTicks;
    }

    @Override
    public RenderSettings getRenderSettings() {
        return settings;
    }
}
