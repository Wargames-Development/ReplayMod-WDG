package com.replaymod.recording.gui;

import com.replaymod.core.SettingsRegistry;
import com.replaymod.recording.Setting;
import de.johni0702.minecraft.gui.GuiRenderer;
import de.johni0702.minecraft.gui.MinecraftGuiRenderer;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import de.johni0702.minecraft.gui.versions.callbacks.RenderHudCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import de.johni0702.minecraft.gui.versions.MatrixStack;

import static com.replaymod.core.ReplayMod.TEXTURE;
import static com.replaymod.core.ReplayMod.TEXTURE_SIZE;
import static com.replaymod.core.versions.MCVer.GlStateManager.*;

/**
 * Renders overlay during recording.
 */
public class GuiRecordingOverlay extends EventRegistrations {
    private final Minecraft mc;
    private final SettingsRegistry settingsRegistry;
    private final GuiRecordingControls guiControls;

    public GuiRecordingOverlay(Minecraft mc, SettingsRegistry settingsRegistry, GuiRecordingControls guiControls) {
        this.mc = mc;
        this.settingsRegistry = settingsRegistry;
        this.guiControls = guiControls;
    }

    /**
     * Render the recording icon and text in the top left corner of the screen.
     */
    { on(RenderHudCallback.EVENT, (stack, partialTicks) -> renderRecordingIndicator(stack)); }
    private void renderRecordingIndicator(MatrixStack stack) {
        if (guiControls.isStopped()) return;
        if (settingsRegistry.get(Setting.INDICATOR)) {
            FontRenderer fontRenderer = mc.fontRendererObj;
            String text = guiControls.isPaused() ? I18n.format("replaymod.gui.paused") : I18n.format("replaymod.gui.recording");
            fontRenderer.drawString(
                    text.toUpperCase(), 30, 18 - (fontRenderer.FONT_HEIGHT / 2), 0xffffffff);
            mc.renderEngine.bindTexture(TEXTURE);
            enableAlpha();
            GuiRenderer renderer = new MinecraftGuiRenderer(stack);
            renderer.drawTexturedRect(10, 10, 58, 20, 16, 16, 16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }
}
