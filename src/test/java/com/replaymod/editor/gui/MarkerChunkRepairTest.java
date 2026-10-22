package com.replaymod.editor.gui;

import com.replaymod.core.ReplayMod;
import com.replaymod.replaystudio.studio.ReplayStudio;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replay.compat.RecordedTerrain;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.Test;
import sun.misc.Unsafe;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class MarkerChunkRepairTest {
    @Test public void copiedSquashFilterPreservesWideChunkSectionsAndTheirBlockAndLightBytes() throws Exception {
        byte[] terrain=new byte[10496];
        java.util.Arrays.fill(terrain,0,4096,(byte)1);
        java.util.Arrays.fill(terrain,8192,10240,(byte)255);
        java.util.Arrays.fill(terrain,10240,10496,(byte)2);
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream deflate=new java.util.zip.DeflaterOutputStream(compressed)) { deflate.write(terrain); }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(1); out.writeInt(compressed.size()); out.writeBoolean(true); out.write(compressed.toByteArray());
        out.writeInt(1); out.writeInt(-36); out.writeInt(1); out.writeInt(0);
        com.replaymod.replaystudio.protocol.Packet packet=new com.replaymod.replaystudio.protocol.Packet(
            com.replaymod.core.versions.MCVer.getPacketTypeRegistry(false),com.replaymod.replaystudio.protocol.PacketType.BulkChunkData);
        try (com.replaymod.replaystudio.protocol.Packet.Writer writer=packet.overwrite()) { writer.writeBytes(bytes.toByteArray()); }
        ChunkSquashFilter filter=new ChunkSquashFilter();
        try {
            filter.onPacket(null,new com.replaymod.replaystudio.PacketData(0,packet));
            ChunkSquashFilter copy=filter.copy();
            java.util.List<com.replaymod.replaystudio.PacketData> result=new java.util.ArrayList<>();
            try {
                copy.onEnd(new com.replaymod.replaystudio.stream.IteratorStream(result.listIterator(),
                    (com.replaymod.replaystudio.filter.StreamFilter)null),0);
                assertEquals(1,result.size());
                byte[] nativeBytes=new byte[result.get(0).getPacket().getBuf().readableBytes()];
                result.get(0).getPacket().getBuf().getBytes(0,nativeBytes);
                assertTrue(com.replaymod.core.utils.LegacyChunkPacket.extendedSingle(nativeBytes));
                byte[] standard=com.replaymod.core.utils.LegacyChunkPacket.standardSingle(nativeBytes);
                DataInputStream header=new DataInputStream(new ByteArrayInputStream(standard));
                assertEquals(1,header.readInt()); assertEquals(-36,header.readInt()); assertTrue(header.readBoolean());
                assertEquals(1,header.readUnsignedShort()); assertEquals(0,header.readUnsignedShort());
                int size=header.readInt(); byte[] packed=new byte[size]; header.readFully(packed);
                byte[] actual=new byte[terrain.length];
                try (DataInputStream inflate=new DataInputStream(new java.util.zip.InflaterInputStream(new ByteArrayInputStream(packed)))) { inflate.readFully(actual); assertEquals(-1,inflate.read()); }
                assertArrayEquals(terrain,actual);
            } finally { for (com.replaymod.replaystudio.PacketData data:result) data.release(); copy.release(); }
        } finally { packet.release(); filter.release(); }
    }
    @Test public void actualRawRecordingKeepsOpeningTerrainThroughTheRealMarkerProcessor() throws Exception {
        String raw=System.getenv("REPLAYMOD_TEST_SMOOTH_RAW"),edited=System.getenv("REPLAYMOD_TEST_SMOOTH_EDITED");
        assumeNotNull(raw,edited);
        Path folder=Files.createTempDirectory(Paths.get(".").toAbsolutePath(),"smooth-repair-");
        Path input=folder.resolve("f-15Shot-repaired.mcpr");
        Files.copy(Paths.get(raw),input);
        ReplayMod previous=ReplayMod.instance;
        Field allocator=Unsafe.class.getDeclaredField("theUnsafe"); allocator.setAccessible(true);
        FilesMod mod=(FilesMod)((Unsafe)allocator.get(null)).allocateInstance(FilesMod.class); mod.folder=folder;
        try {
            ReplayMod.instance=mod;
            assertEquals(input,MarkerProcessor.apply(input,ignored -> { }).get(0).getKey());
        } finally { ReplayMod.instance=previous; }
        RecordedTerrain original,repaired;
        try (ZipFile zip=new ZipFile(edited); InputStream packets=zip.getInputStream(zip.getEntry("recording.tmcpr"))) { original=RecordedTerrain.read(packets); }
        try (ZipFile zip=new ZipFile(input.toFile()); InputStream packets=zip.getInputStream(zip.getEntry("recording.tmcpr"))) { repaired=RecordedTerrain.read(packets); }
        assertNull("Original trim lost the camera's chunk",original.at(0,1,-36));
        assertNotNull("Native raw terrain must survive the repaired trim",repaired.at(0,1,-36));
        assertEquals(0,repaired.at(0,1,-36).time);
        int present=0;
        for (int x=-8;x<=10;x++) for (int z=-45;z<=-27;z++) if (repaired.at(0,x,z)!=null) present++;
        System.out.println("Repaired opening: "+present+" / 361 camera-radius chunks contain full terrain");
        System.out.println("Repaired replay: "+input);
        assertEquals(361,present);
    }
    private static class FilesMod extends ReplayMod {
        Path folder;
        FilesMod() { super(null); }
        @Override public Path getRawReplayFolder() throws IOException { return Files.createDirectories(folder.resolve("raw")); }
        @Override public ReplayFile openReplay(Path path) throws IOException { return openReplay(path,path); }
        @Override public ReplayFile openReplay(Path input,Path output) throws IOException {
            return new ZipReplayFile(new ReplayStudio(),input==null?null:input.toFile(),output.toFile(),folder.resolve("cache").resolve(output.getFileName()).toFile());
        }
    }
}
