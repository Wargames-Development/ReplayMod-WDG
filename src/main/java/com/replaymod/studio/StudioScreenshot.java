package com.replaymod.studio;

import com.replaymod.core.ReplayMod;
import com.replaymod.extras.advancedscreenshots.ScreenshotRenderer;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.ReplayModRender;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.gui.GuiTextField;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class StudioScreenshot extends StudioScreen {
    private GuiTextField resolutionX, resolutionY, output;
    private String widthValue = "1920", heightValue = "1080", outputValue;
    private boolean tags = true;
    private RenderSettings.RenderMethod method = RenderSettings.RenderMethod.DEFAULT;
    public StudioScreenshot() {
        outputValue = new File(new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "screenshots"), new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS").format(new Date()) + ".png").getAbsolutePath();
        ReplayModReplay.instance.getReplayHandler().getReplaySender().setReplaySpeed(0);
    }
    @Override protected void build() {
        resolutionX = field(170, 156, 140, widthValue); resolutionY = field(330, 156, 140, heightValue);
        output = field(170, 226, 570, outputValue);
        button(1, 750, 225, 100, "Browse...");
        button(2, 170, 280, 400, "Rendering: " + method);
        button(3, 590, 280, 260, "Name tags: " + (tags ? "On" : "Off"));
        button(4, 170, 378, 240, "Back to editor"); button(5, 610, 378, 240, "Save screenshot");
    }
    private void remember() { widthValue = resolutionX.getText(); heightValue = resolutionY.getText(); outputValue = output.getText(); }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xB0101720); panel(140, 74, 740, 370, "EXPORT STILL IMAGE");
        label("Width", 170, 136, MUTED); label("Height", 330, 136, MUTED); label("PNG output filename", 170, 206, MUTED);
        label("Capture the current camera and replay moment without a camera path.", 170, 330, MUTED);
    }
    @Override protected void activate(int id) {
        remember();
        if (id == 1) mc.displayGuiScreen(new StudioFilePicker(this, new File(outputValue), file -> { outputValue = file.getAbsolutePath(); output.setText(outputValue); }));
        if (id == 2) { RenderSettings.RenderMethod[] methods = RenderSettings.RenderMethod.getSupported(); method = methods[(java.util.Arrays.asList(methods).indexOf(method) + 1) % methods.length]; initGui(); }
        if (id == 3) { tags = !tags; initGui(); }
        if (id == 4) escape();
        if (id == 5) {
            int w, h;
            try { w = Integer.parseInt(widthValue); h = Integer.parseInt(heightValue); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Width and height must be whole numbers."); }
            if (w <= 0 || h <= 0 || w > 16384 || h > 16384 || (long) w * h * 4 > 256 * 1024 * 1024) throw new IllegalArgumentException("Choose a positive resolution under 16384 pixels / 256 MB per frame.");
            if (method == RenderSettings.RenderMethod.CUBIC && (h % 3 != 0 || w != h / 3 * 4)) throw new IllegalArgumentException("Cube screenshots use a 4:3 resolution, with height divisible by 3.");
            if (method == RenderSettings.RenderMethod.EQUIRECTANGULAR && w != h * 2) throw new IllegalArgumentException("360 screenshots use a 2:1 resolution.");
            if (method == RenderSettings.RenderMethod.ODS && w != h) throw new IllegalArgumentException("ODS screenshots use a 1:1 resolution.");
            File file = new File(outputValue).getAbsoluteFile();
            if (file.exists()) throw new IllegalArgumentException("That output already exists. Choose a new filename.");
            RenderSettings settings = new RenderSettings(method, RenderSettings.EncodingPreset.PNG, w, h, 30, 0, file, tags,
                    false, false, false, null, 360, 180, false, false, false, RenderSettings.AntiAliasing.NONE, "", "", false);
            ReplayMod.instance.runLater(() -> {
                try {
                    File folder = file.getParentFile(); if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalArgumentException("Could not create output folder.");
                    if (new ScreenshotRenderer(settings).renderScreenshot()) mc.displayGuiScreen(new StudioRenderDone(file, 1));
                } catch (Throwable error) {
                    ReplayModRender.LOGGER.error("Screenshot failed", error); mc.displayGuiScreen(this); message = "Screenshot failed: " + error.getMessage();
                }
            });
        }
    }
    @Override public void onGuiClosed() { if (output != null) remember(); super.onGuiClosed(); }
    @Override protected void beforeResize() { remember(); }
    @Override protected void escape() { mc.displayGuiScreen(ReplayModReplay.instance.getReplayHandler().getOverlay().getStudioEditor()); }
}
