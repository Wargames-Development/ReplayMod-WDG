package com.replaymod.render.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Streaming WAV output; memory does not grow with the length of a cinematic. */
public final class WaveWriter implements AutoCloseable {
    private final RandomAccessFile output;
    private long bytes;
    public WaveWriter(File path) throws IOException {
        if (path.exists()) throw new IOException("Audio file already exists: " + path);
        output = new RandomAccessFile(path, "rw"); output.write(new byte[44]);
    }
    public void write(byte[] pcm) throws IOException {
        if (bytes + pcm.length > 0xFFFFFFFFL - 36) throw new IOException("Audio exceeds the WAV size limit; export shorter clips.");
        output.write(pcm); bytes += pcm.length;
    }
    private void little(int value) throws IOException { output.writeInt(Integer.reverseBytes(value)); }
    @Override public void close() throws IOException {
        try {
            output.seek(0); output.writeBytes("RIFF"); little((int) (bytes + 36)); output.writeBytes("WAVEfmt "); little(16);
            output.writeShort(Short.reverseBytes((short) 1)); output.writeShort(Short.reverseBytes((short) 2));
            little(PcmMixer.SAMPLE_RATE); little(PcmMixer.SAMPLE_RATE * 4);
            output.writeShort(Short.reverseBytes((short) 4)); output.writeShort(Short.reverseBytes((short) 16));
            output.writeBytes("data"); little((int) bytes);
        } finally { output.close(); }
    }
}
