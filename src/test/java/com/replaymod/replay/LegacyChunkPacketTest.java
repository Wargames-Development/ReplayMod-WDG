package com.replaymod.replay;

import com.replaymod.core.utils.LegacyChunkPacket;
import java.io.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyChunkPacketTest {
    private static byte[] single(boolean wide,boolean varLength,int mask,int add) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(1); out.writeInt(-36); out.writeBoolean(true);
        if (wide) { out.writeInt(mask); out.writeInt(add); } else { out.writeShort(mask); out.writeShort(add); }
        if (varLength) out.writeByte(3); else out.writeInt(3);
        out.write(new byte[]{0x78,(byte)0x9c,0}); return bytes.toByteArray();
    }
    @Test public void recordingRetainsBothNativeLayoutsAndRepairsOnlyMalformedLengthFraming() throws Exception {
        for (boolean wide:new boolean[]{false,true}) {
            byte[] nativePacket=single(wide,false,63,31);
            assertSame(nativePacket,LegacyChunkPacket.normalizeSingle(nativePacket));
            assertArrayEquals(nativePacket,LegacyChunkPacket.normalizeSingle(single(wide,true,63,31)));
        }
    }
    @Test public void squashAdapterChangesOnlyMaskWidthsAndKeepsTheCompressedTerrainIdentical() throws Exception {
        byte[] standard=single(false,false,63,31),wide=single(true,false,63,31);
        assertArrayEquals(standard,LegacyChunkPacket.standardSingle(wide));
        assertArrayEquals(wide,LegacyChunkPacket.widenSingle(standard));
        assertArrayEquals(wide,LegacyChunkPacket.widenSingle(single(false,true,63,31)));
        assertArrayEquals(Arrays.copyOfRange(wide,21,wide.length),Arrays.copyOfRange(standard,17,standard.length));
    }
    @Test public void bulkAdapterPreservesCoordinatesMasksAndCompressedBytesForMultipleColumns() throws Exception {
        ByteArrayOutputStream wideBytes=new ByteArrayOutputStream(),normalBytes=new ByteArrayOutputStream();
        DataOutputStream wide=new DataOutputStream(wideBytes),normal=new DataOutputStream(normalBytes);
        for (DataOutputStream out:new DataOutputStream[]{wide,normal}) {
            out.writeShort(2); out.writeInt(3); out.writeBoolean(true); out.write(new byte[]{0x78,(byte)0x9c,0});
        }
        for (int x:new int[]{1,-2}) {
            wide.writeInt(x); wide.writeInt(-36); wide.writeInt(63); wide.writeInt(31);
            normal.writeInt(x); normal.writeInt(-36); normal.writeShort(63); normal.writeShort(31);
        }
        assertArrayEquals(normalBytes.toByteArray(),LegacyChunkPacket.standardBulk(wideBytes.toByteArray()));
    }
    @Test public void invalidFramingAndUnsupportedMasksCannotSilentlyEraseTerrain() throws Exception {
        for (byte[] bytes:new byte[][]{new byte[0],new byte[15]}) {
            try { LegacyChunkPacket.normalizeSingle(bytes); fail(); } catch (IOException expected) { }
        }
        try { LegacyChunkPacket.standardSingle(single(true,false,0x10000,0)); fail(); } catch (IOException expected) { }
    }
}
