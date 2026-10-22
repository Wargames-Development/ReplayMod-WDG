package com.replaymod.render.audio;

import com.google.gson.Gson;
import com.replaymod.render.RenderSettings;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class AudioExportTest {
    @Test public void installedMchrEngineAndWeaponAssetsDecodeCompletelyAndRemainAudible() throws Exception {
        String root=System.getenv("REPLAYMOD_TEST_MCHR"), executable=System.getenv("REPLAYMOD_TEST_FFMPEG");
        assumeNotNull(root,executable);
        for(String name:new String[]{"plane.ogg","heli.ogg","gau-8_snd.ogg"}) {
            File sound=new File(root,"assets/mcheli/sounds/"+name);
            assertTrue(sound.toString(),sound.isFile());
            File probe=new File(new File(executable).getParentFile(),"ffprobe.exe");
            run(probe.toString(),"-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",sound.toString());
            double expected=Double.parseDouble(new String(Files.readAllBytes(new File(temporary.getRoot(),"process.log").toPath()),StandardCharsets.UTF_8).trim());
            paulscode.sound.codecs.CodecJOrbis codec=new paulscode.sound.codecs.CodecJOrbis();
            try {
                assertTrue(codec.initialize(sound.toURI().toURL()));
                paulscode.sound.SoundBuffer buffer=codec.readAll(); assertNotNull(buffer);
                PcmMixer.Clip clip=new PcmMixer.Clip(buffer.audioData,buffer.audioFormat);
                double actual=clip.samples.length/2.0/clip.rate;
                assertEquals(name+" must not lose its tail during decoding",expected,actual,0.1);
                long energy=0; for(float sample:clip.samples) energy+=Math.abs(Math.round(sample*32768));
                assertTrue(name+" must contain audible samples",energy>1000);
            } finally { codec.cleanup(); }
        }
    }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File wave(String name) throws Exception {
        File file = new File(temporary.getRoot(), name);
        byte[] pcm = new byte[48000 * 4];
        for (int i = 0; i < 48000; i++) {
            short sample = (short) (12000 * Math.sin(i * 2 * Math.PI * 440 / 48000));
            for (int channel = 0; channel < 2; channel++) { int at = i * 4 + channel * 2; pcm[at] = (byte) sample; pcm[at + 1] = (byte) (sample >> 8); }
        }
        try (WaveWriter writer = new WaveWriter(file)) { writer.write(pcm); }
        return file;
    }
    @Test public void wavHeaderHasTheExactDurationAndPreservesExistingFiles() throws Exception {
        File file = wave("test.wav");
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            assertEquals(48000, stream.getFrameLength()); assertEquals(2, stream.getFormat().getChannels()); assertEquals(48000, stream.getFormat().getSampleRate(), 0);
        }
        byte[] original = Files.readAllBytes(file.toPath());
        try { new WaveWriter(file); fail(); } catch (IOException expected) { }
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));
    }
    @Test public void olderExportSettingsEnableAudioAndExplicitOptOutPersists() {
        RenderSettings settings = new Gson().fromJson("{}", RenderSettings.class);
        assertTrue(settings.isRenderAudio()); settings.setRenderAudio(false);
        assertFalse(new Gson().fromJson(new Gson().toJson(settings), RenderSettings.class).isRenderAudio());
    }
    private void run(String... command) throws Exception {
        File log = new File(temporary.getRoot(), "process.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("FFmpeg test timed out"); }
        assertEquals(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8), 0, process.exitValue());
    }
    @Test public void ffmpegMuxProducesAudioAndVideoStreamsWithMatchingDuration() throws Exception {
        String executable = System.getenv("REPLAYMOD_TEST_FFMPEG"); assumeTrue(executable != null && new File(executable).isFile());
        File video = new File(temporary.getRoot(), "test.mp4"), wave = wave("sound.wav");
        run(executable, "-y", "-f", "lavfi", "-i", "color=c=black:s=64x64:r=30:d=1", "-an", "-c:v", "libx264", video.toString());
        AudioMuxer.mux(executable, video, wave, () -> true);
        assertFalse(wave.exists());
        File probe = new File(new File(executable).getParentFile(), "ffprobe.exe");
        run(probe.toString(), "-v", "error", "-show_entries", "stream=codec_type,duration", "-of", "json", video.toString());
        String result = new String(Files.readAllBytes(new File(temporary.getRoot(), "process.log").toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("\"video\"")); assertTrue(result.contains("\"audio\"")); assertTrue(result.contains("1.000000"));
        File decoded = new File(temporary.getRoot(), "decoded.pcm");
        run(executable, "-y", "-i", video.toString(), "-map", "0:a:0", "-f", "s16le", decoded.toString());
        byte[] pcm = Files.readAllBytes(decoded.toPath()); long energy = 0;
        for (byte sample : pcm) energy += Math.abs(sample);
        assertTrue("The encoded soundtrack must contain audible samples", energy > 100000);
    }
    @Test public void failedMuxPreservesTheVideoAndSoundtrack() throws Exception {
        String executable = System.getenv("REPLAYMOD_TEST_FFMPEG"); assumeTrue(executable != null && new File(executable).isFile());
        File video = new File(temporary.getRoot(), "test.mp4"), wave = new File(temporary.getRoot(), "bad.wav");
        run(executable, "-y", "-f", "lavfi", "-i", "color=c=black:s=64x64:r=30:d=1", "-an", "-c:v", "libx264", video.toString());
        Files.write(wave.toPath(), new byte[]{1, 2, 3}); byte[] original = Files.readAllBytes(video.toPath());
        try { AudioMuxer.mux(executable, video, wave, () -> true); fail(); } catch (IOException expected) { }
        assertTrue(wave.exists()); assertArrayEquals(original, Files.readAllBytes(video.toPath()));
    }
    @Test public void installedFmurExplosionOggDecodesToAudiblePcm() throws Exception {
        String path = System.getenv("REPLAYMOD_TEST_FMUR"); assumeTrue(path != null && new File(path).isFile());
        File sound = new File(temporary.getRoot(), "explosion.ogg");
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path); InputStream input = zip.getInputStream(zip.getEntry("assets/flansmod/sounds/distantexplosion.ogg"))) {
            Files.copy(input, sound.toPath());
        }
        paulscode.sound.codecs.CodecJOrbis codec = new paulscode.sound.codecs.CodecJOrbis();
        try {
            assertTrue(codec.initialize(sound.toURI().toURL()));
            paulscode.sound.SoundBuffer buffer = codec.readAll(); assertNotNull(buffer);
            PcmMixer mixer = new PcmMixer(); mixer.play(new PcmMixer.Clip(buffer.audioData, buffer.audioFormat));
            byte[] pcm = mixer.mix(48000); long energy = 0;
            for (byte sample : pcm) energy += Math.abs(sample);
            assertTrue("FMUR's actual explosion asset must decode to non-silent audio", energy > 1000);
        } finally { codec.cleanup(); }
    }
}
