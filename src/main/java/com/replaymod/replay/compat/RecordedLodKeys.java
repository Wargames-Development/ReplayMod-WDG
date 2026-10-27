package com.replaymod.replay.compat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads DH world identities only from framed 1.7.10 custom-payload packets, never from arbitrary text. */
public final class RecordedLodKeys {
    private RecordedLodKeys() { }
    public static Map<Integer, Set<String>> read(InputStream stream, String channel, int levelMessageId) throws IOException {
        Map<Integer, Set<String>> result = new HashMap<>();
        DataInputStream input = new DataInputStream(new BufferedInputStream(stream));
        long scanned = 0;
        while (scanned < 128L * 1024 * 1024) {
            if (input.read() < 0) break;
            input.readUnsignedByte(); input.readUnsignedByte(); input.readUnsignedByte(); // Remaining timestamp bytes.
            int length = input.readInt();
            if (length < 1 || length > 16 * 1024 * 1024) throw new IOException("Invalid replay packet length while reading LOD identity.");
            scanned += length + 8L;
            int id = varInt(input); int remaining = length - varIntSize(id);
            if (id != 0x3F) { skip(input, remaining); continue; }
            byte[] bytes = new byte[remaining]; input.readFully(bytes);
            try {
                DataInputStream payload = new DataInputStream(new ByteArrayInputStream(bytes));
                int channelLength = varInt(payload);
                if (channelLength < 1 || channelLength > 256) continue;
                byte[] channelBytes = new byte[channelLength]; payload.readFully(channelBytes);
                if (!channel.equals(new String(channelBytes, StandardCharsets.UTF_8))) continue;
                int payloadLength = payload.readUnsignedShort();
                if (payloadLength != payload.available() || payloadLength < 10 || payload.readUnsignedByte() != 0 || payload.readUnsignedByte() != levelMessageId) continue;
                String dimension = text(payload), serverKey = text(payload), levelKey = text(payload);
                payload.readLong(); // Server time; newer WDG builds append the readable storage name.
                if (payload.available() > 0) text(payload);
                if (payload.available() != 0 || !serverKey.matches("[a-zA-Z0-9_-]{0,150}") || !levelKey.matches("[a-zA-Z0-9_@:-]{1,150}")) continue;
                int colon = dimension.lastIndexOf(':');
                int dim = Integer.parseInt(dimension.substring(colon + 1));
                result.computeIfAbsent(dim, unused -> new HashSet<>()).add(levelKey.replace(":", "@@"));
            } catch (IOException | RuntimeException malformedPayload) { /* Not a valid DH level-init packet. */ }
        }
        return result;
    }
    private static String text(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort(); if (length > 300) throw new IOException("LOD identity string too long");
        byte[] bytes = new byte[length]; input.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8);
    }
    private static int varInt(DataInputStream input) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) { int b = input.readUnsignedByte(); value |= (b & 127) << (7 * i); if ((b & 128) == 0) return value; }
        throw new IOException("Invalid replay VarInt");
    }
    private static int varIntSize(int value) { int bytes = 1; while ((value & ~127) != 0) { bytes++; value >>>= 7; } return bytes; }
    private static void skip(DataInputStream input, int bytes) throws IOException {
        if (bytes < 0) throw new IOException("Invalid replay packet size");
        while (bytes > 0) { int count = input.skipBytes(bytes); if (count == 0) { input.readByte(); count = 1; } bytes -= count; }
    }
}
