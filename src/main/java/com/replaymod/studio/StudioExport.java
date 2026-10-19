package com.replaymod.studio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.gui.GuiPathing;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class StudioExport extends StudioScreen {
    private final GuiScreen parent;
    private final ReplayHandler replay;
    private final GuiPathing pathing;
    private GuiTextField widthField, heightField, fps, bitrate, output, executable, arguments, chroma, fovX, fovY;
    private String widthText = "1920", heightText = "1080", fpsText = "60", bitrateText = "12", fileText, exeText = "", argsText = "";
    private boolean advanced, nametags = true, highPerformance, cameraExport, sphericalMetadata;
    private boolean lockYaw, lockPitch, lockRoll;
    private boolean audio = true;
    private String chromaText = "", fovXText = "360", fovYText = "180";
    private RenderSettings.RenderMethod method = RenderSettings.RenderMethod.DEFAULT;
    private RenderSettings.EncodingPreset preset = RenderSettings.EncodingPreset.MP4_DEFAULT;
    private RenderSettings.AntiAliasing aa = RenderSettings.AntiAliasing.NONE;
    private boolean rendering;

    public StudioExport(GuiScreen parent, ReplayHandler replay, GuiPathing pathing) {
        this.parent = parent; this.replay = replay; this.pathing = pathing;
        fileText = new File(ReplayModRender.instance.getVideoFolder(), new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".mp4").getAbsolutePath();
        try {
            if (Files.exists(ReplayModRender.instance.getRenderSettingsPath())) {
                JsonObject saved = new JsonParser().parse(new String(Files.readAllBytes(ReplayModRender.instance.getRenderSettingsPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                if (saved.has("renderAudio") && !saved.get("renderAudio").isJsonNull()) audio = saved.get("renderAudio").getAsBoolean();
                if (saved.has("videoWidth")) widthText = saved.get("videoWidth").getAsString();
                if (saved.has("videoHeight")) heightText = saved.get("videoHeight").getAsString();
                if (saved.has("framesPerSecond")) fpsText = saved.get("framesPerSecond").getAsString();
                if (saved.has("exportCommand")) exeText = saved.get("exportCommand").getAsString();
                if (saved.has("exportArgumentsBgra")) argsText = saved.get("exportArgumentsBgra").getAsString();
                if (saved.has("bitRate")) bitrateText = String.valueOf(Math.max(1, saved.get("bitRate").getAsInt() / 1000000));
                if (saved.has("renderMethod")) method = RenderSettings.RenderMethod.valueOf(saved.get("renderMethod").getAsString());
                if (!method.isSupported()) method = RenderSettings.RenderMethod.DEFAULT;
                if (saved.has("encodingPreset")) preset = RenderSettings.EncodingPreset.valueOf(saved.get("encodingPreset").getAsString());
                if (!preset.isSupported()) preset = RenderSettings.EncodingPreset.MP4_DEFAULT;
                if (saved.has("antiAliasing")) aa = RenderSettings.AntiAliasing.valueOf(saved.get("antiAliasing").getAsString());
                if (saved.has("renderNameTags")) nametags = saved.get("renderNameTags").getAsBoolean();
                if (saved.has("highPerformance")) highPerformance = saved.get("highPerformance").getAsBoolean();
                if (saved.has("cameraPathExport")) cameraExport = saved.get("cameraPathExport").getAsBoolean();
                if (saved.has("stabilizeYaw")) lockYaw = saved.get("stabilizeYaw").getAsBoolean();
                if (saved.has("stabilizePitch")) lockPitch = saved.get("stabilizePitch").getAsBoolean();
                if (saved.has("stabilizeRoll")) lockRoll = saved.get("stabilizeRoll").getAsBoolean();
                if (saved.has("sphericalFovX")) fovXText = saved.get("sphericalFovX").getAsString();
                if (saved.has("sphericalFovY")) fovYText = saved.get("sphericalFovY").getAsString();
                if (saved.has("injectSphericalMetadata")) sphericalMetadata = saved.get("injectSphericalMetadata").getAsBoolean();
                if (saved.has("chromaKeyingColor") && saved.get("chromaKeyingColor").isJsonObject()) {
                    JsonObject color = saved.getAsJsonObject("chromaKeyingColor");
                    chromaText = String.format(java.util.Locale.ROOT, "%02X%02X%02X", color.get("red").getAsInt() & 255, color.get("green").getAsInt() & 255, color.get("blue").getAsInt() & 255);
                }
                if (saved.has("outputFile")) {
                    File previous = new File(saved.getAsJsonObject("outputFile").get("path").getAsString());
                    File directory = previous.isDirectory() ? previous : previous.getAbsoluteFile().getParentFile();
                    fileText = new File(directory, new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + "." + preset.getFileExtension()).getAbsolutePath();
                }
            }
        } catch (Exception ignored) { /* A damaged prior preference file must not block export. */ }
    }
    @Override protected void build() {
        button(1, 90, 66, 230, "Video settings"); button(2, 330, 66, 230, "Advanced settings");
        if (!advanced) {
            widthField = field(100, 132, 100, widthText); heightField = field(220, 132, 100, heightText);
            fps = field(350, 132, 100, fpsText); bitrate = field(480, 132, 100, bitrateText);
            output = field(100, 206, 640, fileText); button(3, 752, 205, 108, "Browse...");
            button(4, 100, 260, 350, "Format: " + preset.toString());
            button(5, 470, 260, 390, "Rendering: " + method.toString());
            button(6, 100, 298, 230, "Name tags: " + (nametags ? "On" : "Off"));
            button(7, 340, 298, 250, "Antialiasing: " + aa.toString()).enabled = preset != RenderSettings.EncodingPreset.PNG && method != RenderSettings.RenderMethod.BLEND;
            button(8, 600, 298, 260, "Live preview: " + (!highPerformance ? "On" : "Once per second"));
            button(32, 600, 350, 260, "Game audio: " + (audio ? "On" : "Off"));
            button(30, 610, 132, 120, "1080p preset"); button(31, 740, 132, 120, "4K preset");
        } else {
            executable = field(100, 134, 640, exeText); button(9, 752, 133, 108, "Browse...");
            arguments = field(100, 206, 760, argsText); arguments.setMaxStringLength(4096);
            button(10, 100, 254, 230, "Reset encoder arguments");
            button(11, 100, 304, 350, "Also save camera path: " + (cameraExport ? "On" : "Off"));
            button(12, 470, 304, 390, "360 video metadata: " + (sphericalMetadata ? "On" : "Off"));
            button(18, 100, 342, 140, "Lock yaw: " + (lockYaw ? "On" : "Off"));
            button(19, 250, 342, 140, "Lock pitch: " + (lockPitch ? "On" : "Off"));
            button(20, 400, 342, 140, "Lock roll: " + (lockRoll ? "On" : "Off"));
            chroma = field(610, 343, 150, chromaText);
            fovX = field(100, 378, 100, fovXText); fovY = field(220, 378, 100, fovYText);
        }
        button(13, 100, 458, 180, "Back to editor");
        button(14, 300, 458, 220, "Open output folder");
        button(15, 620, 458, 240, "Render video");
        button(16, 100, 408, 230, "Add this video to queue");
        button(17, 340, 408, 250, "Open render queue");
    }
    private void remember() {
        if (!advanced) {
            widthText = widthField.getText(); heightText = heightField.getText(); fpsText = fps.getText(); bitrateText = bitrate.getText(); fileText = output.getText();
        } else {
            exeText = executable.getText(); argsText = arguments.getText(); chromaText = chroma.getText(); fovXText = fovX.getText(); fovYText = fovY.getText();
        }
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xC0101720); panel(70, 24, 820, 484, "EXPORT CINEMATIC");
        if (!advanced) {
            label("Width", 100, 114, MUTED); label("Height", 220, 114, MUTED); label("Frames / second", 350, 114, MUTED); label("Bitrate (Mbps)", 480, 114, MUTED);
            label(preset == RenderSettings.EncodingPreset.PNG ? "Image sequence folder (created automatically)" : "Full output filename", 100, 186, MUTED);
            label(preset == RenderSettings.EncodingPreset.PNG ? "Game audio saves as a WAV beside the images." : "Game audio follows the exported video timeline.", 100, 356, MUTED);
            label("Start with 1920 x 1080 at 30 or 60 fps. Export does not change your recording.", 100, 376, MUTED);
        } else {
            label("FFmpeg executable (blank = detect automatically)", 100, 114, MUTED);
            label("Custom encoder arguments (blank = selected preset)", 100, 186, MUTED);
            label("Use %WIDTH%, %HEIGHT%, %FPS%, %FILENAME%, %BITRATE%, %FILTERS%.", 100, 232, MUTED);
            label("Chroma RGB hex (blank = off)", 610, 328, MUTED);
            label("Horizontal FOV", 100, 365, MUTED); label("Vertical FOV", 220, 365, MUTED);
            label("Spherical export: crop the 360-degree view.", 350, 383, MUTED);
        }
    }
    @Override protected void activate(int id) {
        remember();
        switch (id) {
            case 1: advanced = false; initGui(); break;
            case 2: advanced = true; initGui(); break;
            case 3: mc.displayGuiScreen(new StudioFilePicker(this, new File(fileText), file -> { fileText = file.getAbsolutePath(); output.setText(fileText); })); break;
            case 4:
                RenderSettings.EncodingPreset[] formats = { RenderSettings.EncodingPreset.MP4_DEFAULT, RenderSettings.EncodingPreset.MP4_CUSTOM, RenderSettings.EncodingPreset.MKV_LOSSLESS, RenderSettings.EncodingPreset.WEBM_CUSTOM, RenderSettings.EncodingPreset.PNG };
                int index = java.util.Arrays.asList(formats).indexOf(preset); preset = formats[(index + 1) % formats.length];
                if (method == RenderSettings.RenderMethod.BLEND) method = RenderSettings.RenderMethod.DEFAULT;
                if (preset == RenderSettings.EncodingPreset.PNG) aa = RenderSettings.AntiAliasing.NONE;
                argsText = "";
                int dot = fileText.lastIndexOf('.'); if (dot > fileText.lastIndexOf(File.separatorChar)) fileText = fileText.substring(0, dot);
                fileText += "." + preset.getFileExtension(); initGui(); break;
            case 5:
                RenderSettings.RenderMethod previousMethod = method;
                RenderSettings.RenderMethod[] methods = RenderSettings.RenderMethod.getSupported();
                method = methods[(java.util.Arrays.asList(methods).indexOf(method) + 1) % methods.length];
                if (method == RenderSettings.RenderMethod.BLEND || previousMethod == RenderSettings.RenderMethod.BLEND) {
                    preset = method == RenderSettings.RenderMethod.BLEND ? RenderSettings.EncodingPreset.BLEND : RenderSettings.EncodingPreset.MP4_DEFAULT;
                    int extension = fileText.lastIndexOf('.'); if (extension > fileText.lastIndexOf(File.separatorChar)) fileText = fileText.substring(0, extension);
                    fileText += "." + preset.getFileExtension(); argsText = "";
                }
                initGui(); break;
            case 6: nametags = !nametags; initGui(); break;
            case 7: aa = RenderSettings.AntiAliasing.values()[(aa.ordinal() + 1) % RenderSettings.AntiAliasing.values().length]; initGui(); break;
            case 8: highPerformance = !highPerformance; initGui(); break;
            case 9: mc.displayGuiScreen(new StudioFilePicker(this, new File(exeText.isEmpty() ? new File(mc.mcDataDir, "ffmpeg.exe").getPath() : exeText), file -> { exeText = file.getAbsolutePath(); executable.setText(exeText); })); break;
            case 10: argsText = ""; initGui(); break;
            case 11: cameraExport = !cameraExport; initGui(); break;
            case 12: sphericalMetadata = !sphericalMetadata; initGui(); break;
            case 13: escape(); break;
            case 14: MCVer.openFile(new File(fileText).getAbsoluteFile().getParentFile()); break;
            case 15: render(false); break;
            case 16: render(true); break;
            case 17: mc.displayGuiScreen(new StudioRenderQueue(this, replay)); break;
            case 18: lockYaw = !lockYaw; initGui(); break;
            case 19: lockPitch = !lockPitch; initGui(); break;
            case 20: lockRoll = !lockRoll; initGui(); break;
            case 30: widthText = "1920"; heightText = "1080"; initGui(); break;
            case 31: widthText = "3840"; heightText = "2160"; initGui(); break;
            case 32: audio = !audio; initGui(); break;
            default: break;
        }
    }
    private int positive(String value, String label, int max) {
        try { int number = Integer.parseInt(value.trim()); if (number <= 0 || number > max) throw new NumberFormatException(); return number; }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(label + " must be between 1 and " + max + "."); }
    }
    private void render(boolean queue) {
        if (rendering) return;
        if (ReplayModSimplePathing.instance.getCurrentTimeline().getPositionPath().getSegments().isEmpty()
                || ReplayModSimplePathing.instance.getCurrentTimeline().getTimePath().getSegments().isEmpty()) throw new IllegalArgumentException("Add at least two camera shots with replay times before exporting.");
        int w = positive(widthText, "Width", 16384), h = positive(heightText, "Height", 16384), rate = positive(fpsText, "Frame rate", 240);
        int mbps = positive(bitrateText, "Bitrate", 1000);
        if ((long) w * h * 4 * aa.getFactor() * aa.getFactor() > 256 * 1024 * 1024 || w * aa.getFactor() > 16384 || h * aa.getFactor() > 16384) throw new IllegalArgumentException("Resolution with antialiasing exceeds the 256 MB / 16384-pixel frame limit.");
        if (preset.isYuv420() && (w % 2 != 0 || h % 2 != 0)) throw new IllegalArgumentException("MP4 / WEBM width and height must both be even.");
        if (method == RenderSettings.RenderMethod.CUBIC && (h % 3 != 0 || w != h / 3 * 4)) throw new IllegalArgumentException("Cube export uses a 4:3 image, with height divisible by 3.");
        File file = new File(fileText).getAbsoluteFile();
        if (file.exists()) throw new IllegalArgumentException("That file already exists. Choose a new filename to preserve it.");
        if (sphericalMetadata && !method.isSpherical()) throw new IllegalArgumentException("360 metadata requires a spherical rendering method.");
        int horizontalFov = positive(fovXText, "Horizontal FOV", 360), verticalFov = positive(fovYText, "Vertical FOV", 180);
        if (method.isSpherical() && (horizontalFov < 120 || horizontalFov % 5 != 0 || verticalFov < 120 || verticalFov % 5 != 0)) throw new IllegalArgumentException("Spherical FOV must be at least 120 degrees and use 5-degree steps.");
        if (method.isSpherical()) {
            int uncroppedHeight = verticalFov < 180 ? Math.round(h * 180 / (float) verticalFov) : h;
            int expectedWidth = horizontalFov < 360 ? Math.round(uncroppedHeight * 2 * horizontalFov / 360f) : uncroppedHeight * 2;
            if (method == RenderSettings.RenderMethod.ODS) expectedWidth = Math.round(expectedWidth / 2f);
            if (w != expectedWidth) throw new IllegalArgumentException("With these FOV values, the output width must be " + expectedWidth + ".");
        }
        de.johni0702.minecraft.gui.utils.lwjgl.Color chromaColour = null;
        if (!chromaText.trim().isEmpty()) {
            try {
                String hex = chromaText.trim().replace("#", "");
                if (!hex.matches("[0-9a-fA-F]{6}")) throw new NumberFormatException();
                int rgb = Integer.parseInt(hex, 16);
                chromaColour = new de.johni0702.minecraft.gui.utils.lwjgl.Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255);
            } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Chroma colour must be six hex digits, such as 00FF00."); }
        }
        RenderSettings settings = new RenderSettings(method, preset, w, h, rate, mbps * 1000000, file, nametags,
                lockYaw, lockPitch, lockRoll, chromaColour, horizontalFov, verticalFov, sphericalMetadata, false, cameraExport, aa, exeText,
                argsText.trim().isEmpty() ? preset.getValue() : argsText, highPerformance);
        settings.setRenderAudio(audio);
        Timeline timeline = pathing.preparePathsForPlayback(false);
        if (timeline == null) throw new IllegalArgumentException("The path is invalid. Check that replay times always move forward.");
        if (queue) {
            for (com.replaymod.render.utils.RenderJob existing : ReplayModRender.instance.getRenderQueue()) {
                if (existing.getSettings().getOutputFile().getAbsoluteFile().equals(file)) throw new IllegalArgumentException("The queue already uses that filename. Give this video a unique name.");
            }
            com.replaymod.render.utils.RenderJob job = new com.replaymod.render.utils.RenderJob();
            job.setSettings(settings); job.setTimeline(timeline);
            ReplayModRender.instance.getRenderQueue().add(job); ReplayModRender.instance.saveRenderQueue();
            mc.displayGuiScreen(new StudioRenderQueue(this, replay)); return;
        }
        rendering = true;
        ReplayMod.instance.runLaterWithoutLock(() -> {
            try {
                File folder = file.getParentFile(); if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalArgumentException("Could not create output folder.");
                Files.write(ReplayModRender.instance.getRenderSettingsPath(), new Gson().toJson(settings).getBytes(StandardCharsets.UTF_8));
                if (!new VideoRenderer(settings, replay, timeline).renderVideo()) mc.displayGuiScreen(this);
            } catch (Throwable failure) {
                ReplayModRender.LOGGER.error("Export failed", failure);
                mc.displayGuiScreen(this);
                message = "Export failed: " + failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            } finally { rendering = false; }
        });
    }
    @Override public void onGuiClosed() { if (output != null || executable != null) remember(); super.onGuiClosed(); }
    @Override protected void beforeResize() { remember(); }
    @Override protected void escape() { remember(); mc.displayGuiScreen(parent); }
}
