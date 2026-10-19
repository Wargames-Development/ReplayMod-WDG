package com.replaymod.render.audio;

import com.replaymod.render.rendering.VideoRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Adds the offline soundtrack without re-encoding video. Failed muxes preserve video and WAV. */
public final class AudioMuxer {
    private AudioMuxer() { }
    public static void mux(VideoRenderer renderer, File video, File wave) throws IOException, InterruptedException {
        mux(renderer.getRenderSettings().getExportCommandOrDefault(), video, wave, () -> {
            if (renderer.drawGui()) return true;
            renderer.cancel(); return false;
        });
    }
    public static void mux(String executable, File video, File wave, java.util.function.BooleanSupplier keepRunning) throws IOException, InterruptedException {
        String name = video.getName(), extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("mp4|mkv|webm|mov")) throw new IOException("Cannot embed audio in ." + extension + ". The soundtrack is saved at " + wave);
        Path mixed = Files.createTempFile(video.getParentFile().toPath(), "replay-audio-", "." + extension);
        Path log = Files.createTempFile(video.getParentFile().toPath(), "replay-audio-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(executable, "-y", "-i", video.getAbsolutePath(), "-i", wave.getAbsolutePath(),
                    "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", extension.equals("webm") ? "libopus" : "aac", "-b:a", "192k", mixed.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                if (!keepRunning.getAsBoolean()) { process.destroyForcibly(); return; }
            }
            if (process.exitValue() != 0 || Files.size(mixed) == 0) {
                String detail = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
                throw new IOException("Audio mux failed; video and WAV were preserved. " + detail.substring(Math.max(0, detail.length() - 3000)));
            }
            Files.move(mixed, video.toPath(), StandardCopyOption.REPLACE_EXISTING);
            discardTemporary(wave.toPath());
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            discardTemporary(mixed); discardTemporary(log);
        }
    }
    private static void discardTemporary(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException temporarilyLocked) { path.toFile().deleteOnExit(); }
    }
}
