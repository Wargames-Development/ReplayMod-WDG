package com.replaymod.replay.compat;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class RecordedLodKeysTest {
    private static final String CHANNEL = "distant_horizonsmsg";
    private void text(DataOutputStream output, String text) throws IOException { byte[] bytes = text.getBytes(StandardCharsets.UTF_8); output.writeShort(bytes.length); output.write(bytes); }
    private byte[] record(String channel, int message, String key) throws IOException {
        return record(channel, message, key, null);
    }
    private byte[] record(String channel, int message, String key, String storageName) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream(); DataOutputStream dh = new DataOutputStream(payload);
        dh.writeByte(0); dh.writeByte(message); text(dh, "overworld:0"); text(dh, ""); text(dh, key); dh.writeLong(0);
        if (storageName != null) text(dh, storageName);
        ByteArrayOutputStream packet = new ByteArrayOutputStream(); DataOutputStream minecraft = new DataOutputStream(packet);
        minecraft.writeByte(0x3F); minecraft.writeByte(channel.length()); minecraft.write(channel.getBytes(StandardCharsets.UTF_8)); minecraft.writeShort(payload.size()); minecraft.write(payload.toByteArray());
        ByteArrayOutputStream framed = new ByteArrayOutputStream(); DataOutputStream recording = new DataOutputStream(framed);
        recording.writeInt(500); recording.writeInt(packet.size()); recording.write(packet.toByteArray()); return framed.toByteArray();
    }
    @Test public void exactWorldIdentityComesFromDhLevelInitAndNotOtherPayloads() throws Exception {
        assertTrue(RecordedLodKeys.read(new ByteArrayInputStream(record("OtherChannel", 2, "world@overworld:0")), CHANNEL, 2).isEmpty());
        assertTrue(RecordedLodKeys.read(new ByteArrayInputStream(record(CHANNEL, 3, "world@overworld:0")), CHANNEL, 2).isEmpty());
        Map<Integer, Set<String>> keys = RecordedLodKeys.read(new ByteArrayInputStream(record(CHANNEL, 2, "world@overworld:0")), CHANNEL, 2);
        assertEquals(Collections.singleton("world@overworld@@0"), keys.get(0));
    }
    @Test public void multipleWorldsInOneDimensionRemainAmbiguousRatherThanPickingTheLast() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); stream.write(record(CHANNEL, 2, "first@overworld:0")); stream.write(record(CHANNEL, 2, "second@overworld:0"));
        assertEquals(2, RecordedLodKeys.read(new ByteArrayInputStream(stream.toByteArray()), CHANNEL, 2).get(0).size());
    }
    @Test public void readableStorageNameExtensionPreservesTheWorldIdentity() throws Exception {
        for (String name : Arrays.asList("", "earth40", "../untrusted-name")) {
            Map<Integer, Set<String>> keys = RecordedLodKeys.read(new ByteArrayInputStream(record(CHANNEL, 2, "world@overworld:0", name)), CHANNEL, 2);
            assertEquals(Collections.singleton("world@overworld@@0"), keys.get(0));
        }
    }
    @Test public void malformedLengthsAndTraversalKeysCannotBecomeSourcePaths() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(stream); output.writeInt(0); output.writeInt(Integer.MAX_VALUE);
        try { RecordedLodKeys.read(new ByteArrayInputStream(stream.toByteArray()), CHANNEL, 2); fail(); } catch (IOException expected) { }
        assertTrue(RecordedLodKeys.read(new ByteArrayInputStream(record(CHANNEL, 2, "../outside")), CHANNEL, 2).isEmpty());
    }
    @Test public void actualTestRecordingContainsAnUnambiguousOverworldIdentity() throws Exception {
        String path = System.getenv("REPLAYMOD_TEST_RECORDING"); assumeTrue(path != null && new File(path).isFile());
        try (ZipFile recording = new ZipFile(path); InputStream packets = recording.getInputStream(recording.getEntry("recording.tmcpr"))) {
            Map<Integer, Set<String>> keys = RecordedLodKeys.read(packets, CHANNEL, 2);
            assertNotNull(keys.get(0)); assertEquals(1, keys.get(0).size());
            assertTrue(keys.get(0).iterator().next().endsWith("@overworld@@0"));
        }
    }
}
