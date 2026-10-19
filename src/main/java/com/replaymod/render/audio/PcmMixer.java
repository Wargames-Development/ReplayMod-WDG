package com.replaymod.render.audio;

import javax.sound.sampled.AudioFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small offline stereo mixer. Its only clock is the number of exported samples. */
public final class PcmMixer {
    public static final int SAMPLE_RATE = 48000;
    public static final class Clip {
        final float[] samples;
        final float rate;
        public Clip(byte[] pcm, AudioFormat format) {
            int channels = format.getChannels(), bytes = format.getSampleSizeInBits() / 8;
            if (channels < 1 || channels > 2 || bytes != 2 || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) throw new IllegalArgumentException("Expected mono/stereo signed 16-bit PCM audio.");
            rate = format.getSampleRate();
            if (!Float.isFinite(rate) || rate <= 0) throw new IllegalArgumentException("Invalid audio sample rate.");
            int frames = pcm.length / (channels * bytes);
            samples = new float[frames * 2];
            for (int i = 0; i < frames; i++) for (int channel = 0; channel < 2; channel++) {
                int offset = (i * channels + Math.min(channel, channels - 1)) * bytes;
                int value = format.isBigEndian() ? (pcm[offset] << 8) | (pcm[offset + 1] & 255) : (pcm[offset + 1] << 8) | (pcm[offset] & 255);
                samples[i * 2 + channel] = value / 32768f;
            }
        }
    }
    public static final class Voice {
        final Clip clip;
        public double position;
        public float pitch = 1, left = 1, right = 1;
        public boolean loop, stopped;
        public int repeatDelaySamples;
        private int repeatRemaining;
        Voice(Clip clip) { this.clip = clip; }
    }
    private final List<Voice> voices = new ArrayList<>();
    public Voice play(Clip clip) { Voice voice = new Voice(clip); voices.add(voice); return voice; }
    public static long sampleAtFrame(long frame, int fps) {
        if (fps <= 0 || frame < 0) throw new IllegalArgumentException("Invalid audio frame clock.");
        return frame * SAMPLE_RATE / fps;
    }
    public byte[] mix(int frames) {
        float[] mixed = new float[frames * 2];
        for (Iterator<Voice> iterator = voices.iterator(); iterator.hasNext();) {
            Voice voice = iterator.next();
            int length = voice.clip.samples.length / 2;
            if (voice.stopped || length == 0) { voice.stopped = true; iterator.remove(); continue; }
            for (int i = 0; i < frames; i++) {
                if (voice.repeatRemaining > 0) { voice.repeatRemaining--; continue; }
                if (voice.position >= length) {
                    if (!voice.loop) { voice.stopped = true; break; }
                    voice.position %= length;
                    voice.repeatRemaining = voice.repeatDelaySamples;
                    if (voice.repeatRemaining > 0) { voice.repeatRemaining--; continue; }
                }
                int at = (int) voice.position, next = Math.min(length - 1, at + 1);
                float fraction = (float) (voice.position - at);
                for (int channel = 0; channel < 2; channel++) {
                    float a = voice.clip.samples[at * 2 + channel], b = voice.clip.samples[next * 2 + channel];
                    mixed[i * 2 + channel] += (a + (b - a) * fraction) * (channel == 0 ? voice.left : voice.right);
                }
                voice.position += voice.pitch * voice.clip.rate / SAMPLE_RATE;
            }
            if (voice.stopped) iterator.remove();
        }
        byte[] pcm = new byte[frames * 4];
        for (int i = 0; i < mixed.length; i++) {
            int sample = Math.round(Math.max(-1f, Math.min(32767 / 32768f, mixed[i])) * 32768);
            pcm[i * 2] = (byte) sample; pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        return pcm;
    }
}
