package com.replaymod.render.audio;

import com.replaymod.render.RenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import paulscode.sound.SoundBuffer;
import paulscode.sound.codecs.CodecJOrbis;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.util.*;

/** Captures game sound events into a deterministic WAV, independently of OpenAL/wall time. */
public final class ExportAudio implements AutoCloseable {
    private static volatile ExportAudio active;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final VideoRenderer renderer;
    private final PcmMixer mixer = new PcmMixer();
    private final WaveWriter output;
    private final File wave;
    private final Map<ISound, Playing> playing = new IdentityHashMap<>();
    private final Map<ResourceLocation, PcmMixer.Clip> clips = new LinkedHashMap<>();
    private final Set<ResourceLocation> unknown = new HashSet<>();
    private final Map<ISound, Integer> delayed = new IdentityHashMap<>();
    private long cachedSamples;
    private volatile boolean capturing;
    private boolean warmingUp;
    private int ticks;
    private SoundManager soundManager;
    private final java.util.function.BiFunction<SoundManager, ISound, ISound> soundHook = ForgeHooksClient::playSound;
    private int frame;
    private static final class Playing {
        final ISound sound; final PcmMixer.Voice voice; final SoundCategory category; final double volume, pitch;
        VehicleSound vehicle;
        Playing(ISound sound, PcmMixer.Voice voice, SoundCategory category, double volume, double pitch) { this.sound = sound; this.voice = voice; this.category = category; this.volume = volume; this.pitch = pitch; }
    }
    public ExportAudio(VideoRenderer renderer) throws IOException {
        if (active != null) throw new IllegalStateException("An audio export is already running.");
        this.renderer = renderer;
        File video = renderer.getRenderSettings().getOutputFile();
        File folder = renderer.getRenderSettings().getEncodingPreset() == RenderSettings.EncodingPreset.PNG ? video : video.getParentFile();
        Files.createDirectories(folder.toPath());
        wave = new File(folder, video.getName() + ".audio.wav");
        output = new WaveWriter(wave);
        mc.getSoundHandler().stopSounds();
        active = this;
    }
    public synchronized void beginFrame(int frame) { this.frame = frame; capturing = true; }
    /** Warm-up advances voices too, retaining the audible tail at the first exported frame. */
    public synchronized void beginWarmup() { warmingUp = true; }
    public synchronized void advanceWarmup(int millis) { mix(millis * PcmMixer.SAMPLE_RATE / 1000); }
    public synchronized void endWarmup() { warmingUp = false; }
    private byte[] mix(int samples) {
        for (Playing entry : playing.values()) update(entry.sound, entry);
        byte[] pcm = mixer.mix(samples);
        playing.entrySet().removeIf(entry -> entry.getValue().voice.stopped);
        return pcm;
    }
    public synchronized void endFrame() {
        try {
            int fps = renderer.getRenderSettings().getFramesPerSecond();
            output.write(mix((int) (PcmMixer.sampleAtFrame(frame + 1L, fps) - PcmMixer.sampleAtFrame(frame, fps))));
        } catch (IOException error) { renderer.setFailure(error); }
        finally { capturing = false; }
    }
    public static void tick() {
        ExportAudio session = active;
        if (session == null || !(session.capturing || session.warmingUp)) return;
        synchronized (session) {
            ++session.ticks;
            for (Playing entry : new ArrayList<>(session.playing.values())) if (entry.sound instanceof ITickableSound) ((ITickableSound) entry.sound).update();
            List<ISound> due = new ArrayList<>();
            session.delayed.entrySet().removeIf(entry -> { if (entry.getValue() > session.ticks) return false; due.add(entry.getKey()); return true; });
            for (ISound sound : due) session.start(sound);
        }
    }
    /** True also during GUI/warmup: do not leak offline sounds into real-time speakers. */
    public static boolean capture(SoundManager manager, ISound sound) {
        ExportAudio session = active;
        if (session == null) return false;
        session.soundManager = manager;
        if (sound != null && (session.capturing || session.warmingUp || sound.canRepeat())) session.start(manager, sound);
        return true;
    }
    /** Null outside export: callers must retain vanilla's real-time behavior. */
    public static Boolean isPlaying(ISound sound) {
        ExportAudio session = active;
        if (session == null) return null;
        synchronized (session) { Playing voice = session.playing.get(sound); return voice != null && !voice.voice.stopped; }
    }
    public static boolean active() { return active != null; }
    static void bindVehicle(ISound sound, VehicleSound vehicle) {
        ExportAudio session=active;
        if (session!=null) synchronized(session) { Playing voice=session.playing.get(sound); if (voice!=null) voice.vehicle=vehicle; }
    }
    public static boolean delay(SoundManager manager, ISound sound, int ticks) {
        ExportAudio session = active;
        if (session == null) return false;
        synchronized (session) {
            session.soundManager = manager;
            if (sound != null && (session.capturing || session.warmingUp)) session.delayed.put(sound, session.ticks + Math.max(0, ticks));
        }
        return true;
    }
    private void start(ISound sound) { start(soundManager, sound); }
    private synchronized void start(SoundManager manager, ISound requested) {
        try {
            // A mod may return a replacement ISound. Queries and stops still use the original object.
            if (!Boolean.TRUE.equals(isPlaying(requested))) play(requested, soundHook.apply(manager, requested));
        } catch (Exception error) { renderer.setFailure(new IOException("Could not capture game audio: " + requested.getSoundLocation(), error)); }
    }
    public static void stop(ISound sound) {
        ExportAudio session = active;
        if (session != null) synchronized (session) { session.delayed.remove(sound); Playing voice = session.playing.remove(sound); if (voice != null) voice.voice.stopped = true; }
    }
    public static void stopAll() {
        ExportAudio session = active;
        if (session != null) synchronized (session) { for (Playing voice : session.playing.values()) voice.voice.stopped = true; session.playing.clear(); session.delayed.clear(); }
    }
    private synchronized void play(ISound requested, ISound sound) throws IOException {
        if (sound == null) return;
        SoundEventAccessorComposite event = mc.getSoundHandler().getSound(sound.getSoundLocation());
        if (event == null) { if (unknown.add(sound.getSoundLocation())) com.replaymod.render.ReplayModRender.LOGGER.warn("Unknown sound during audio export: {}", sound.getSoundLocation()); return; }
        SoundPoolEntry pool = event.func_148720_g();
        if (pool == SoundHandler.missing_sound) return;
        ResourceLocation location = pool.getSoundPoolEntryLocation();
        PcmMixer.Clip clip = clips.get(location);
        if (clip == null) {
            CodecJOrbis codec = new CodecJOrbis();
            try {
                URL url = new URL(null, "replayaudio:" + location, new URLStreamHandler() {
                    @Override protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override public void connect() { }
                            @Override public InputStream getInputStream() throws IOException { return mc.getResourceManager().getResource(location).getInputStream(); }
                        };
                    }
                });
                if (!codec.initialize(url)) throw new IOException("Could not decode " + location);
                SoundBuffer decoded = codec.readAll();
                if (decoded == null) throw new IOException("Empty audio resource " + location);
                clip = new PcmMixer.Clip(decoded.audioData, decoded.audioFormat);
            } finally { codec.cleanup(); }
            // Bound the decoded clip cache; active voices retain their own clips.
            if (cachedSamples + clip.samples.length > 16 * 1024 * 1024) { clips.clear(); cachedSamples = 0; }
            clips.put(location, clip); cachedSamples += clip.samples.length;
        }
        PcmMixer.Voice voice = mixer.play(clip);
        voice.loop = sound.canRepeat(); voice.repeatDelaySamples = Math.max(0, sound.getRepeatDelay()) * PcmMixer.SAMPLE_RATE / 20;
        playing.put(requested, new Playing(sound, voice, event.getSoundCategory(), pool.getVolume(), pool.getPitch()));
    }
    private void update(ISound sound, Playing playing) {
        if (sound instanceof ITickableSound && ((ITickableSound) sound).isDonePlaying()) { playing.voice.stopped = true; return; }
        float category = playing.category == SoundCategory.MASTER ? 1 : mc.gameSettings.getSoundLevel(playing.category);
        double gain = Math.max(0, Math.min(1, sound.getVolume() * playing.volume * category)) * mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
        float pan = 0;
        Entity listener = mc.renderViewEntity;
        if (sound.getAttenuationType() != ISound.AttenuationType.NONE && listener != null) {
            double[] source=playing.vehicle==null?new double[]{sound.getXPosF(),sound.getYPosF(),sound.getZPosF()}:playing.vehicle.position(listener);
            double dx = source[0] - listener.posX, dy = source[1] - listener.posY, dz = source[2] - listener.posZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz), range = 16 * Math.max(1, sound.getVolume());
            gain *= Math.max(0, 1 - distance / range);
            if (distance > 0) pan = (float) (-(dx * Math.cos(Math.toRadians(listener.rotationYaw)) + dz * Math.sin(Math.toRadians(listener.rotationYaw))) / distance);
        }
        playing.voice.pitch = (float) Math.max(0.5, Math.min(2, sound.getPitch() * playing.pitch));
        playing.voice.left = (float) (gain * Math.sqrt((1 - pan) / 2)); playing.voice.right = (float) (gain * Math.sqrt((1 + pan) / 2));
    }
    public File getWaveFile() { return wave; }
    @Override public synchronized void close() throws IOException { capturing = warmingUp = false; if (active == this) active = null; try { output.close(); } finally { clips.clear(); playing.clear(); delayed.clear(); } }
}
