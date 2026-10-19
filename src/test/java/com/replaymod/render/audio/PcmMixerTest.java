package com.replaymod.render.audio;

import org.junit.Test;
import javax.sound.sampled.AudioFormat;
import static org.junit.Assert.*;

public class PcmMixerTest {
    private PcmMixer.Clip clip(int samples, int value) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) { pcm[i * 2] = (byte) value; pcm[i * 2 + 1] = (byte) (value >> 8); }
        return new PcmMixer.Clip(pcm, new AudioFormat(48000, 16, 1, true, false));
    }
    private int value(byte[] pcm, int frame, int channel) { int at = frame * 4 + channel * 2; return (short) ((pcm[at] & 255) | pcm[at + 1] << 8); }
    @Test public void soundStartsAtTheScheduledVideoFrameAndDoesNotDependOnRenderSpeed() {
        PcmMixer mixer = new PcmMixer();
        assertEquals(0, value(mixer.mix(1600), 1599, 0));
        mixer.play(clip(4800, 10000));
        assertEquals(10000, value(mixer.mix(1600), 0, 0));
        assertEquals(10000, value(mixer.mix(1600), 1599, 1));
    }
    @Test public void fractionalFramesNeverAccumulateAudioDrift() {
        for (int fps : new int[]{24, 30, 59, 60, 120, 144}) {
            long frames = fps * 60L * 10, samples = 0;
            for (int frame = 0; frame < frames; frame++) samples += PcmMixer.sampleAtFrame(frame + 1, fps) - PcmMixer.sampleAtFrame(frame, fps);
            assertEquals(48000L * 600, samples);
        }
    }
    @Test public void overlappingSoundsSumAndSaturateInsteadOfWrapping() {
        PcmMixer mixer = new PcmMixer(); mixer.play(clip(10, 25000)); mixer.play(clip(10, 25000));
        assertEquals(32767, value(mixer.mix(1), 0, 0));
    }
    @Test public void monoPcmDuplicatesChannelsAndVoiceGainsPlaceItInStereo() {
        PcmMixer mixer = new PcmMixer(); PcmMixer.Voice sound = mixer.play(clip(10, 10000));
        sound.left = 0.25f; sound.right = 1;
        byte[] pcm = mixer.mix(1); assertEquals(2500, value(pcm, 0, 0)); assertEquals(10000, value(pcm, 0, 1));
    }
    @Test public void pitchLoopRepeatDelayAndStopsWorkWithoutOpenAL() {
        PcmMixer mixer = new PcmMixer(); PcmMixer.Voice sound = mixer.play(clip(4, -12000));
        sound.pitch = 2; sound.loop = true; sound.repeatDelaySamples = 2;
        byte[] pcm = mixer.mix(6);
        assertEquals(-12000, value(pcm, 0, 0)); assertEquals(-12000, value(pcm, 1, 0));
        assertEquals(0, value(pcm, 2, 0)); assertEquals(0, value(pcm, 3, 0)); assertEquals(-12000, value(pcm, 4, 0));
        sound.stopped = true; assertEquals(0, value(mixer.mix(1), 0, 0));
    }
    @Test public void nativeSampleRatesAndEndianFormatsDecodeCorrectly() {
        PcmMixer mixer = new PcmMixer();
        PcmMixer.Clip clip = new PcmMixer.Clip(new byte[]{0x20, 0, 0x40, 0}, new AudioFormat(24000, 16, 1, true, true));
        mixer.play(clip); byte[] pcm = mixer.mix(3);
        assertEquals(8192, value(pcm, 0, 0)); assertEquals(12288, value(pcm, 1, 0)); assertEquals(16384, value(pcm, 2, 0));
    }
}
