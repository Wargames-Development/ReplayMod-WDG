package com.replaymod.render;

import com.google.gson.annotations.SerializedName;
import com.replaymod.core.versions.MCVer;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Util;
import cpw.mods.fml.common.versioning.ComparableVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import static com.replaymod.render.ReplayModRender.LOGGER;

public class RenderSettings {
    public enum RenderMethod {
        DEFAULT, STEREOSCOPIC, CUBIC, EQUIRECTANGULAR, ODS, BLEND;

        @Override
        public String toString() {
            return I18n.format("replaymod.gui.rendersettings.renderer." + name().toLowerCase());
        }

        public String getDescription() {
            return I18n.format("replaymod.gui.rendersettings.renderer." + name().toLowerCase() + ".description");
        }

        public boolean isSpherical() {
            return this == EQUIRECTANGULAR || this == ODS;
        }

        public boolean hasFixedAspectRatio() {
            return this == EQUIRECTANGULAR || this == ODS || this == CUBIC;
        }

        @SuppressWarnings("RedundantIfStatement")
        public boolean isSupported() {
            if (this == BLEND) {
                return false;
            }
            return true;
        }

        public static RenderMethod[] getSupported() {
            return Arrays.stream(values()).filter(RenderMethod::isSupported).toArray(RenderMethod[]::new);
        }
    }

    public enum EncodingPreset {
        MP4_CUSTOM("-an -c:v libx264 -b:v %BITRATE% -pix_fmt yuv420p \"%FILENAME%\"", "mp4"),

        MP4_DEFAULT("-an -c:v libx264 -preset ultrafast -pix_fmt yuv420p \"%FILENAME%\"", "mp4"),

        MP4_POTATO("-an -c:v libx264 -preset ultrafast -crf 51 -pix_fmt yuv420p \"%FILENAME%\"", "mp4"),

        WEBM_CUSTOM("-an -c:v libvpx -b:v %BITRATE% -pix_fmt yuv420p \"%FILENAME%\"", "webm"),

        MKV_LOSSLESS("-an -c:v libx264 -preset ultrafast -qp 0 \"%FILENAME%\"", "mkv"),

        BLEND(null, "blend"),

        EXR(null, "exr"),

        PNG(null, "png");

        private final String preset;
        private final String fileExtension;

        EncodingPreset(String preset, String fileExtension) {
            this.preset = preset;
            this.fileExtension = fileExtension;
        }

        public String getValue() {
            return "-y -f rawvideo -pix_fmt bgra -s %WIDTH%x%HEIGHT% -r %FPS% -i - %FILTERS%" + preset;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public boolean hasBitrateSetting() {
            return preset != null && preset.contains("%BITRATE%");
        }

        public boolean isYuv420() { return preset != null && preset.contains("-pix_fmt yuv420p"); }

        @Override
        public String toString() {
            return I18n.format("replaymod.gui.rendersettings.presets." + name().replace('_', '.').toLowerCase());
        }

        public boolean isSupported() {
            if (this == BLEND) {
                return RenderMethod.BLEND.isSupported();
            } else if (this == EXR) {
                return false;
            } else {
                return true;
            }
        }

        public static EncodingPreset[] getSupported() {
            return Arrays.stream(values()).filter(EncodingPreset::isSupported).toArray(EncodingPreset[]::new);
        }
    }

    public enum AntiAliasing {
        NONE(1), X2(2), X4(4), X8(8);

        private final int factor;

        AntiAliasing(int factor) {
            this.factor = factor;
        }

        public int getFactor() {
            return factor;
        }

        @Override
        public String toString() {
            return I18n.format("replaymod.gui.rendersettings.antialiasing." + name().toLowerCase());
        }
    }

    private final RenderMethod renderMethod;
    private final EncodingPreset encodingPreset;
    private final int videoWidth;
    private final int videoHeight;
    private final int framesPerSecond;
    private final int bitRate;
    private final File outputFile;

    private final boolean renderNameTags;
    private final boolean stabilizeYaw;
    private final boolean stabilizePitch;
    private final boolean stabilizeRoll;
    private final ReadableColor chromaKeyingColor;
    private final int sphericalFovX;
    private final int sphericalFovY;
    private final boolean injectSphericalMetadata;
    private final boolean depthMap;
    private final boolean cameraPathExport;
    private final AntiAliasing antiAliasing;

    private final String exportCommand;
    // We switched from rgb24 to bgra for performance at one point, so for backwards compatibility we need to
    // reset the arguments if they're from an older version. Easiest way to do that is to just change the key
    // and handle the null during loading.
    @SerializedName("exportArguments")
    // using an empty string as default because old versions will realize it's not a preset and prompt the user
    private final String exportArgumentsPreBgra = "";
    @SerializedName("exportArgumentsBgra")
    private final String exportArguments;

    private final boolean highPerformance;
    // Null from older saved settings intentionally enables the new audio exporter.
    private Boolean renderAudio;
    public boolean isRenderAudio() { return renderAudio == null || renderAudio; }
    public void setRenderAudio(boolean enabled) { renderAudio = enabled; }

    public RenderSettings(
            RenderMethod renderMethod,
            EncodingPreset encodingPreset,
            int videoWidth,
            int videoHeight,
            int framesPerSecond,
            int bitRate,
            File outputFile,
            boolean renderNameTags,
            boolean stabilizeYaw,
            boolean stabilizePitch,
            boolean stabilizeRoll,
            ReadableColor chromaKeyingColor,
            int sphericalFovX,
            int sphericalFovY,
            boolean injectSphericalMetadata,
            boolean depthMap,
            boolean cameraPathExport,
            AntiAliasing antiAliasing,
            String exportCommand,
            String exportArguments,
            boolean highPerformance
    ) {
        this.renderMethod = renderMethod;
        this.encodingPreset = encodingPreset;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.framesPerSecond = framesPerSecond;
        this.bitRate = bitRate;
        this.outputFile = outputFile;
        this.renderNameTags = renderNameTags;
        this.stabilizeYaw = stabilizeYaw;
        this.stabilizePitch = stabilizePitch;
        this.stabilizeRoll = stabilizeRoll;
        this.chromaKeyingColor = chromaKeyingColor;
        this.sphericalFovX = sphericalFovX;
        this.sphericalFovY = sphericalFovY;
        this.injectSphericalMetadata = injectSphericalMetadata;
        this.depthMap = depthMap;
        this.cameraPathExport = cameraPathExport;
        this.antiAliasing = antiAliasing;
        this.exportCommand = exportCommand;
        this.exportArguments = exportArguments;
        this.highPerformance = highPerformance;
    }

    public RenderSettings withEncodingPreset(EncodingPreset encodingPreset) {
        return new RenderSettings(
                renderMethod,
                encodingPreset,
                videoWidth,
                videoHeight,
                framesPerSecond,
                bitRate,
                outputFile,
                renderNameTags,
                stabilizeYaw,
                stabilizePitch,
                stabilizeRoll,
                chromaKeyingColor,
                sphericalFovX,
                sphericalFovY,
                injectSphericalMetadata,
                depthMap,
                cameraPathExport,
                antiAliasing,
                exportCommand,
                exportArguments,
                highPerformance
        );
    }

    /**
     * @return the width of the output video during rendering, including the upscale for Anti-Aliasing.
     */
    public int getVideoWidth() {
        return videoWidth * antiAliasing.getFactor();
    }

    /**
     * @return the height of the output video during rendering, including the upscale for Anti-Aliasing.
     */
    public int getVideoHeight() {
        return videoHeight * antiAliasing.getFactor();
    }

    /**
     * @return the actual width of the output video.
     */
    public int getTargetVideoWidth() {
        return videoWidth;
    }

    /**
     * @return the actual height of the output video.
     */
    public int getTargetVideoHeight() {
        return videoHeight;
    }

    public String getVideoFilters() {
        StringBuilder filters = new StringBuilder();

        if (antiAliasing != AntiAliasing.NONE) {
            double factor = 1.0 / antiAliasing.getFactor();
            filters.append(String.format("-filter:v scale=iw*%1$s:ih*%1$s ", factor));
        }

        return filters.toString();
    }

    public String getExportCommandOrDefault() {
        return exportCommand.isEmpty() ? findFFmpeg() : exportCommand;
    }

    private static String findFFmpeg() {
        switch (Util.getOSType()) {
            case WINDOWS: {
                // Preserve ReplayMod's historical portable layout first.
                File dotMinecraft = MCVer.getMinecraft().mcDataDir;
                File inDotMinecraft = new File(dotMinecraft, "ffmpeg/bin/ffmpeg.exe");
                if (inDotMinecraft.isFile()) {
                    LOGGER.debug("FFmpeg found in .minecraft/ffmpeg: {}", inDotMinecraft);
                    return inDotMinecraft.getAbsolutePath();
                }

                // Many Wargames users will use normal Windows package managers instead of
                // unpacking FFmpeg into .minecraft. GUI launchers do not always inherit the
                // same PATH as an interactive shell, so resolve common package-manager shims.
                String localAppData = System.getenv("LOCALAPPDATA");
                String userProfile = System.getenv("USERPROFILE");
                String chocolateyInstall = System.getenv("ChocolateyInstall");

                File[] commonWindowsLocations = new File[]{
                        localAppData == null ? null
                                : new File(localAppData, "Microsoft/WinGet/Links/ffmpeg.exe"),
                        userProfile == null ? null
                                : new File(userProfile, "scoop/shims/ffmpeg.exe"),
                        chocolateyInstall == null ? null
                                : new File(chocolateyInstall, "bin/ffmpeg.exe"),
                        new File("C:/ProgramData/chocolatey/bin/ffmpeg.exe"),
                        new File("C:/ffmpeg/bin/ffmpeg.exe")
                };
                for (File candidate : commonWindowsLocations) {
                    if (candidate != null && candidate.isFile()) {
                        LOGGER.debug("Found FFmpeg at {}", candidate);
                        return candidate.getAbsolutePath();
                    }
                }

                // Retain ReplayMod's support-friendly recursive search for users who unpack
                // versioned ffmpeg folders somewhere beneath .minecraft.
                try {
                    final Path[] result = new Path[1];
                    Files.walkFileTree(dotMinecraft.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if ("ffmpeg.exe".equalsIgnoreCase(file.getFileName().toString())) {
                                result[0] = file;
                                return FileVisitResult.TERMINATE;
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
                    if (result[0] != null) {
                        LOGGER.debug("Found FFmpeg under .minecraft at {}", result[0]);
                        return result[0].toAbsolutePath().toString();
                    }
                } catch (IOException e) {
                    LOGGER.debug("Error searching .minecraft for ffmpeg.exe:", e);
                }

                String onPath = findExecutableOnPath("ffmpeg.exe");
                if (onPath != null) {
                    return onPath;
                }
                break;
            }

            case OSX: {
                // Finder/IntelliJ launches do not reliably inherit the user's shell PATH.
                // Check Apple Silicon Homebrew, Intel Homebrew and system locations explicitly.
                for (String path : new String[]{
                        "/opt/homebrew/bin/ffmpeg",
                        "/usr/local/bin/ffmpeg",
                        "/usr/bin/ffmpeg"
                }) {
                    File file = new File(path);
                    if (file.isFile()) {
                        LOGGER.debug("Found FFmpeg at {}", path);
                        return file.getAbsolutePath();
                    }
                }

                // Also inspect both Homebrew Cellar roots in case the symlink is not visible.
                for (String homebrewRoot : new String[]{
                        "/opt/homebrew/Cellar/ffmpeg",
                        "/usr/local/Cellar/ffmpeg"
                }) {
                    File homebrewFolder = new File(homebrewRoot);
                    String[] homebrewVersions = homebrewFolder.list();
                    if (homebrewVersions == null) {
                        continue;
                    }
                    Optional<File> latestOpt = Arrays.stream(homebrewVersions)
                            .map(ComparableVersion::new)
                            .sorted(Comparator.reverseOrder())
                            .map(ComparableVersion::toString)
                            .map(v -> new File(new File(homebrewFolder, v), "bin/ffmpeg"))
                            .filter(File::isFile)
                            .findFirst();
                    if (latestOpt.isPresent()) {
                        File latest = latestOpt.get();
                        LOGGER.debug("Found {} versions of FFmpeg in {}, chose {}",
                                homebrewVersions.length, homebrewRoot, latest);
                        return latest.getAbsolutePath();
                    }
                }

                String onPath = findExecutableOnPath("ffmpeg");
                if (onPath != null) {
                    return onPath;
                }
                break;
            }

            case LINUX:
            case SOLARIS:
            case UNKNOWN: {
                String onPath = findExecutableOnPath("ffmpeg");
                if (onPath != null) {
                    return onPath;
                }
                break;
            }
        }

        // Preserve ProcessBuilder's ordinary PATH lookup as the last fallback.
        LOGGER.debug("Using default FFmpeg executable");
        return Util.getOSType() == Util.EnumOS.WINDOWS ? "ffmpeg.exe" : "ffmpeg";
    }

    private static String findExecutableOnPath(String executable) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isEmpty()) {
            return null;
        }

        for (String pathEntry : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (pathEntry == null || pathEntry.isEmpty()) {
                continue;
            }

            // Windows PATH entries are often quoted.
            if (pathEntry.length() >= 2 && pathEntry.startsWith("\"") && pathEntry.endsWith("\"")) {
                pathEntry = pathEntry.substring(1, pathEntry.length() - 1);
            }

            File candidate = new File(pathEntry, executable);
            if (candidate.isFile()) {
                LOGGER.debug("Found FFmpeg on PATH at {}", candidate);
                return candidate.getAbsolutePath();
            }
        }

        return null;
    }

    public RenderMethod getRenderMethod() {
        return renderMethod;
    }

    public EncodingPreset getEncodingPreset() {
        return encodingPreset;
    }

    public int getFramesPerSecond() {
        return framesPerSecond;
    }

    public int getBitRate() {
        return bitRate;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public boolean isRenderNameTags() {
        return renderNameTags;
    }

    public boolean isStabilizeYaw() {
        return stabilizeYaw;
    }

    public boolean isStabilizePitch() {
        return stabilizePitch;
    }

    public boolean isStabilizeRoll() {
        return stabilizeRoll;
    }

    public ReadableColor getChromaKeyingColor() {
        return chromaKeyingColor;
    }

    public int getSphericalFovX() {
        return sphericalFovX;
    }

    public int getSphericalFovY() {
        return sphericalFovY;
    }

    public boolean isInjectSphericalMetadata() {
        return injectSphericalMetadata;
    }

    public boolean isDepthMap() {
        return depthMap;
    }

    public boolean isCameraPathExport() {
        return cameraPathExport;
    }

    public AntiAliasing getAntiAliasing() {
        return antiAliasing;
    }

    public String getExportCommand() {
        return exportCommand;
    }

    public String getExportArguments() {
        return exportArguments;
    }

    public boolean isHighPerformance() {
        return highPerformance;
    }

    @Override
    public String toString() {
        return "RenderSettings{" +
                "renderMethod=" + renderMethod +
                ", encodingPreset=" + encodingPreset +
                ", videoWidth=" + videoWidth +
                ", videoHeight=" + videoHeight +
                ", framesPerSecond=" + framesPerSecond +
                ", bitRate=" + bitRate +
                ", outputFile=" + outputFile +
                ", renderNameTags=" + renderNameTags +
                ", stabilizeYaw=" + stabilizeYaw +
                ", stabilizePitch=" + stabilizePitch +
                ", stabilizeRoll=" + stabilizeRoll +
                ", chromaKeyingColor=" + chromaKeyingColor +
                ", sphericalFovX=" + sphericalFovX +
                ", sphericalFovY=" + sphericalFovY +
                ", injectSphericalMetadata=" + injectSphericalMetadata +
                ", depthMap=" + depthMap +
                ", cameraPathExport=" + cameraPathExport +
                ", antiAliasing=" + antiAliasing +
                ", exportCommand='" + exportCommand + '\'' +
                ", exportArgumentsPreBgra='" + exportArgumentsPreBgra + '\'' +
                ", exportArguments='" + exportArguments + '\'' +
                ", highPerformance=" + highPerformance +
                '}';
    }
}
