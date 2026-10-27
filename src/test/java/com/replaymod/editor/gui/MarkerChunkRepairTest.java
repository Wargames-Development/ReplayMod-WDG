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
    @Test public void supportedNativeMasksStillApplyAutomaticCuts() throws Exception {
        Path folder=Files.createTempDirectory(Paths.get(".").toAbsolutePath(),"supported-mask-save-");
        Path input=folder.resolve("supported.mcpr");
        ReplayMod previous=ReplayMod.instance;
        Field allocator=Unsafe.class.getDeclaredField("theUnsafe"); allocator.setAccessible(true);
        FilesMod mod=(FilesMod)((Unsafe)allocator.get(null)).allocateInstance(FilesMod.class); mod.folder=folder;
        try {
            ReplayMod.instance=mod;
            com.replaymod.replaystudio.protocol.PacketTypeRegistry registry=
                com.replaymod.core.versions.MCVer.getPacketTypeRegistry(false);
            com.replaymod.replaystudio.replay.ReplayMetaData metadata=new com.replaymod.replaystudio.replay.ReplayMetaData();
            metadata.setDuration(2000); metadata.setMcVersion("1.7.10");
            java.util.Set<com.replaymod.replaystudio.data.Marker> markers=new java.util.HashSet<>();
            markers.add(marker(0,MarkerProcessor.MARKER_NAME_START_CUT));
            markers.add(marker(1000,MarkerProcessor.MARKER_NAME_END_CUT));
            try (ReplayFile replay=mod.openReplay(null,input)) {
                try (com.replaymod.replaystudio.io.ReplayOutputStream packets=replay.writePacketData()) {
                    packets.write(500,widePacket(registry,true,1));
                    packets.write(2000,widePacket(registry,true,1));
                }
                replay.writeMetaData(registry,metadata); replay.writeMarkers(markers); replay.save();
            }
            assertEquals(1000,MarkerProcessor.apply(input,ignored -> { }).get(0).getValue().getDuration());
            assertTrue(Files.exists(folder.resolve("raw/supported.mcpr")));
            assertNull(mod.warning);
            try (ReplayFile replay=mod.openReplay(input);
                 com.replaymod.replaystudio.io.ReplayInputStream packets=replay.getPacketData(
                     com.replaymod.core.versions.MCVer.getPacketTypeRegistry(true))) {
                boolean found=false;
                com.replaymod.replaystudio.PacketData data;
                while ((data=packets.readPacket())!=null) {
                    try {
                        if (data.getPacket().getType()==com.replaymod.replaystudio.protocol.PacketType.ChunkData) {
                            assertEquals(0,data.getTime()); found=true;
                            byte[] bytes=new byte[data.getPacket().getBuf().readableBytes()];
                            data.getPacket().getBuf().getBytes(data.getPacket().getBuf().readerIndex(),bytes);
                            assertTrue(com.replaymod.core.utils.LegacyChunkPacket.extendedSingle(bytes));
                        }
                    } finally { data.release(); }
                }
                assertTrue("The cut must preserve the opening terrain",found);
            }
        } finally { ReplayMod.instance=previous; }
    }
    @Test public void unsupportedNativeMasksKeepTheWholeRecordingBeforeAnyCutsOrSplits() throws Exception {
        for (boolean bulk:new boolean[]{false,true}) {
            for (String operation:new String[]{MarkerProcessor.MARKER_NAME_END_CUT,MarkerProcessor.MARKER_NAME_SPLIT}) {
                Path folder=Files.createTempDirectory(Paths.get(".").toAbsolutePath(),"wide-mask-save-");
                Path input=folder.resolve("extended.mcpr");
                ReplayMod previous=ReplayMod.instance;
                Field allocator=Unsafe.class.getDeclaredField("theUnsafe"); allocator.setAccessible(true);
                FilesMod mod=(FilesMod)((Unsafe)allocator.get(null)).allocateInstance(FilesMod.class); mod.folder=folder;
                try {
                    ReplayMod.instance=mod;
                    com.replaymod.replaystudio.protocol.PacketTypeRegistry registry=
                        com.replaymod.core.versions.MCVer.getPacketTypeRegistry(false);
                    com.replaymod.replaystudio.replay.ReplayMetaData metadata=new com.replaymod.replaystudio.replay.ReplayMetaData();
                    metadata.setDuration(2000); metadata.setMcVersion("1.7.10");
                    java.util.Set<com.replaymod.replaystudio.data.Marker> markers=new java.util.HashSet<>();
                    if (operation.equals(MarkerProcessor.MARKER_NAME_END_CUT)) markers.add(marker(0,MarkerProcessor.MARKER_NAME_START_CUT));
                    markers.add(marker(1000,operation));
                    try (ReplayFile replay=mod.openReplay(null,input)) {
                        try (com.replaymod.replaystudio.io.ReplayOutputStream packets=replay.writePacketData()) {
                            packets.write(100,widePacket(registry,bulk,1));
                            packets.write(500,widePacket(registry,bulk,16777215));
                            packets.write(2000,widePacket(registry,bulk,1));
                        }
                        replay.writeMetaData(registry,metadata); replay.writeMarkers(markers); replay.save();
                    }
                    byte[] original=Files.readAllBytes(input);
                    java.util.List<Float> progress=new java.util.ArrayList<>();
                    java.util.List<com.replaymod.replaystudio.us.myles.ViaVersion.api.Pair<Path,
                        com.replaymod.replaystudio.replay.ReplayMetaData>> outputs=MarkerProcessor.apply(input,progress::add);
                    assertEquals(1,outputs.size()); assertEquals(input,outputs.get(0).getKey());
                    assertEquals(2000,outputs.get(0).getValue().getDuration());
                    assertArrayEquals("Original archive must remain byte-for-byte intact",original,Files.readAllBytes(input));
                    assertFalse(Files.exists(folder.resolve("raw")));
                    assertFalse(Files.exists(folder.resolve("extended_0.mcpr")));
                    assertEquals(Float.valueOf(1f),progress.get(progress.size()-1));
                    assertEquals("replaymod.chat.unsupportedchunkmasks",mod.warning);
                } finally { ReplayMod.instance=previous; }
            }
        }
    }
    private static com.replaymod.replaystudio.data.Marker marker(int time,String name) {
        com.replaymod.replaystudio.data.Marker marker=new com.replaymod.replaystudio.data.Marker();
        marker.setTime(time); marker.setName(name); return marker;
    }
    private static com.replaymod.replaystudio.protocol.Packet widePacket(
            com.replaymod.replaystudio.protocol.PacketTypeRegistry registry,boolean bulk,int mask) throws IOException {
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        byte[] terrain=new byte[Integer.bitCount(mask)*10240+256];
        java.util.Arrays.fill(terrain,(byte)1);
        try (DeflaterOutputStream deflate=new DeflaterOutputStream(compressed)) { deflate.write(terrain); }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        if (bulk) {
            out.writeShort(1); out.writeInt(compressed.size()); out.writeBoolean(true); out.write(compressed.toByteArray());
            out.writeInt(1); out.writeInt(-36); out.writeInt(mask); out.writeInt(0);
        } else {
            out.writeInt(1); out.writeInt(-36); out.writeBoolean(true); out.writeInt(mask); out.writeInt(0);
            out.writeInt(compressed.size()); out.write(compressed.toByteArray());
        }
        com.replaymod.replaystudio.protocol.Packet packet=new com.replaymod.replaystudio.protocol.Packet(registry,
            bulk ? com.replaymod.replaystudio.protocol.PacketType.BulkChunkData : com.replaymod.replaystudio.protocol.PacketType.ChunkData);
        try (com.replaymod.replaystudio.protocol.Packet.Writer writer=packet.overwrite()) { writer.writeBytes(bytes.toByteArray()); }
        return packet;
    }
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
        String warning;
        FilesMod() { super(null); }
        @Override public void runLater(Runnable runnable) { runnable.run(); }
        @Override public void printWarningToChat(String message,Object... args) { warning=message; }
        @Override public Path getRawReplayFolder() throws IOException { return Files.createDirectories(folder.resolve("raw")); }
        @Override public ReplayFile openReplay(Path path) throws IOException { return openReplay(path,path); }
        @Override public ReplayFile openReplay(Path input,Path output) throws IOException {
            return new ZipReplayFile(new ReplayStudio(),input==null?null:input.toFile(),output.toFile(),folder.resolve("cache").resolve(output.getFileName()).toFile());
        }
    }
}
