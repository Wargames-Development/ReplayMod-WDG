package com.replaymod.replay;

import com.replaymod.replay.compat.RecordedTerrain;
import com.replaymod.replay.compat.ReplayTerrainReadiness;
import java.io.*;
import java.util.zip.ZipFile;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class RecordedTerrainTest {
    private byte[] join(int dimension) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream p=new DataOutputStream(b);
        p.writeByte(1); p.writeInt(123); p.writeByte(0); p.writeByte(dimension); p.writeInt(0); return b.toByteArray();
    }
    private byte[] single(int x,int z,boolean full,int mask,boolean extended) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream p=new DataOutputStream(b);
        p.writeByte(0x21); p.writeInt(x); p.writeInt(z); p.writeBoolean(full);
        if (extended) { p.writeInt(mask); p.writeInt(0); } else { p.writeShort(mask); p.writeShort(0); }
        p.writeInt(1); p.writeByte(0); return b.toByteArray();
    }
    private byte[] bulk(boolean extended) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream p=new DataOutputStream(b);
        p.writeByte(0x26); p.writeShort(2); p.writeInt(1); p.writeBoolean(true); p.writeByte(0);
        for (int x=1;x<=2;x++) {
            p.writeInt(x); p.writeInt(-3);
            if (extended) { p.writeInt(31); p.writeInt(15); } else { p.writeShort(31); p.writeShort(15); }
        }
        return b.toByteArray();
    }
    private RecordedTerrain read(byte[]... packets) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(b);
        for (int i=0;i<packets.length;i++) { out.writeInt(i*1000); out.writeInt(packets[i].length); out.write(packets[i]); }
        return RecordedTerrain.read(new ByteArrayInputStream(b.toByteArray()));
    }
    @Test public void recoveryRetainsFirstFullSnapshotAndSeparatesDimensionsForBothWireLayouts() throws Exception {
        for (boolean extended:new boolean[]{false,true}) {
            RecordedTerrain t=read(join(0),single(1,-3,false,31,extended),bulk(extended),single(1,-3,true,63,extended),
                    single(2,-3,true,0,extended),join(-1),single(1,-3,true,31,extended));
            assertEquals(3,t.size()); assertEquals(2000,t.at(0,1,-3).time);
            assertEquals(0,t.at(0,1,-3).index); assertEquals(1,t.at(0,2,-3).index);
            assertSame(t.at(0,1,-3).packet,t.at(0,2,-3).packet);
            assertEquals(6000,t.at(-1,1,-3).time); assertNull(t.at(1,1,-3));
        }
    }
    @Test public void singleFullSnapshotsAcceptOriginalAndExtendedMasks() throws Exception {
        for (boolean extended:new boolean[]{false,true}) {
            RecordedTerrain t=read(join(0),single(-13,30,true,31,extended));
            assertEquals(1,t.size()); assertEquals(-1,t.at(0,-13,30).index);
        }
    }
    @Test(expected=EOFException.class) public void truncatedPacketCannotSilentlySupplyPartialTerrain() throws Exception {
        RecordedTerrain.read(new ByteArrayInputStream(new byte[]{0,0,0}));
    }
    @Test public void captureWaitsForShadowRefreshAfterEachNewMesh() {
        ReplayTerrainReadiness.beginFrame(); assertFalse(ReplayTerrainReadiness.afterPass());
        ReplayTerrainReadiness.meshUploaded(); assertFalse(ReplayTerrainReadiness.afterPass());
        assertTrue(ReplayTerrainReadiness.afterPass());
        ReplayTerrainReadiness.beginFrame(); assertFalse(ReplayTerrainReadiness.afterPass()); assertTrue(ReplayTerrainReadiness.afterPass());
    }
    @Test public void terrainRecoveryIsExplicitAndSurvivesSavedSettingsAndPresetChanges() {
        com.google.gson.Gson gson=new com.google.gson.Gson();
        com.replaymod.render.RenderSettings settings=gson.fromJson("{}",com.replaymod.render.RenderSettings.class);
        assertFalse(settings.isRecoverRecordedTerrain());
        settings.setRecoverRecordedTerrain(true);
        settings=gson.fromJson(gson.toJson(settings),com.replaymod.render.RenderSettings.class);
        assertTrue(settings.isRecoverRecordedTerrain());
        assertTrue(settings.withEncodingPreset(com.replaymod.render.RenderSettings.EncodingPreset.MP4_DEFAULT).isRecoverRecordedTerrain());
    }
    @Test public void clientChunkExistsCannotBeUsedAsLoadedTerrainEvidence() {
        net.minecraft.client.multiplayer.ChunkProviderClient provider=new net.minecraft.client.multiplayer.ChunkProviderClient(null);
        assertTrue("Vanilla's client existence method is only a stub",provider.chunkExists(-13,30));
        assertTrue(com.replaymod.replay.compat.ReplayTerrainExport.missing(provider.provideChunk(-13,30)));
        assertFalse("A real loaded all-air chunk still has precedence",
            com.replaymod.replay.compat.ReplayTerrainExport.missing(new net.minecraft.world.chunk.Chunk(null,-13,30)));
    }
    @Test public void actualOpeningCanRecoverFullDetailTerrainWhichArrivesLaterInRecording() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_TERRAIN_RECORDING"); assumeNotNull(file);
        try (ZipFile zip=new ZipFile(file); InputStream input=zip.getInputStream(zip.getEntry("recording.tmcpr"))) {
            RecordedTerrain t=RecordedTerrain.read(input);
            assertTrue(t.size()>1500); assertEquals(20301,t.at(0,-13,30).time);
            for (int x=-15;x<=-11;x++) for (int z=28;z<=32;z++) assertNotNull(t.at(0,x,z));
        }
    }
}
