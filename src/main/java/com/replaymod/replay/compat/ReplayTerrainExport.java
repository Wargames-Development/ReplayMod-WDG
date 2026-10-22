package com.replaymod.replay.compat;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;

/** Opt-in presentation recovery. Existing chunks and recorded packet timing keep precedence. */
public final class ReplayTerrainExport {
    private static RecordedTerrain terrain;
    private static int restored;
    private ReplayTerrainExport() { }
    public static boolean missing(Chunk chunk) { return chunk instanceof EmptyChunk; }
    public static void begin(ReplayHandler replay,boolean enabled) throws IOException {
        end();
        if (!enabled) return;
        if (!com.replaymod.replaystudio.us.myles.ViaVersion.api.protocol.ProtocolVersion.getProtocol(5)
                .equals(replay.getReplayFile().getMetaData().getProtocolVersion()))
            throw new IOException("Recorded terrain recovery currently supports Minecraft 1.7.10 recordings.");
        com.google.common.base.Optional<InputStream> input=replay.getReplayFile().get("recording.tmcpr");
        if (input.isPresent()) try (InputStream stream=input.get()) { terrain=RecordedTerrain.read(stream); }
        ReplayModReplay.LOGGER.info("Terrain recovery enabled; indexed {} first recorded full chunks",terrain==null?0:terrain.size());
    }
    public static void prepareFrame() {
        if (terrain==null) return;
        Minecraft mc=Minecraft.getMinecraft(); WorldClient world=mc.theWorld;
        if (world==null || mc.renderViewEntity==null) return;
        int cx=(int)Math.floor(mc.renderViewEntity.posX/16),cz=(int)Math.floor(mc.renderViewEntity.posZ/16);
        int radius=mc.gameSettings.renderDistanceChunks+1;
        for (int x=cx-radius;x<=cx+radius;x++) for (int z=cz-radius;z<=cz+radius;z++) {
            // ChunkProviderClient.chunkExists is an unconditional true in 1.7.10.
            // Only its shared EmptyChunk placeholder denotes missing data; a real
            // all-air Chunk must still keep precedence over later snapshots.
            if (!missing(world.getChunkProvider().provideChunk(x,z))) continue;
            RecordedTerrain.Snapshot snapshot=terrain.at(world.provider.dimensionId,x,z);
            if (snapshot==null) continue;
            try { restore(world,x,z,snapshot); restored++; }
            catch (IOException error) { throw new IllegalStateException("Could not recover recorded terrain at "+x+", "+z,error); }
        }
    }
    private static void restore(WorldClient world,int x,int z,RecordedTerrain.Snapshot snapshot) throws IOException {
        byte[] bytes; int mask,add;
        ByteBuf buffer=Unpooled.wrappedBuffer(snapshot.packet,1,snapshot.packet.length-1);
        try {
            PacketBuffer data=new PacketBuffer(buffer);
            if (snapshot.index>=0) {
                S26PacketMapChunkBulk packet=new S26PacketMapChunkBulk(); packet.readPacketData(data);
                int index=snapshot.index;
                if (packet.func_149255_a(index)!=x || packet.func_149253_b(index)!=z) throw new IOException("Recorded bulk chunk identity changed");
                bytes=packet.func_149256_c(index); mask=packet.func_149252_e()[index]; add=packet.func_149257_f()[index];
            } else {
                S21PacketChunkData packet=new S21PacketChunkData(); packet.readPacketData(data);
                if (packet.func_149273_e()!=x || packet.func_149271_f()!=z) throw new IOException("Recorded chunk identity changed");
                bytes=packet.func_149272_d(); mask=packet.func_149276_g(); add=packet.func_149270_h();
            }
        } finally { buffer.release(); }
        // Match NetHandlerPlayClient's full-chunk installation, without spawning entities,
        // replaying future events, or replacing any chunk already loaded by the real replay.
        world.doPreChunk(x,z,true);
        world.invalidateBlockReceiveRegion(x<<4,0,z<<4,(x<<4)+15,256,(z<<4)+15);
        Chunk chunk=world.getChunkFromChunkCoords(x,z);
        chunk.fillChunk(bytes,mask,add,true);
        world.markBlockRangeForRenderUpdate(x<<4,0,z<<4,(x<<4)+15,256,(z<<4)+15);
        if (!(world.provider instanceof WorldProviderSurface)) chunk.resetRelightChecks();
    }
    public static void end() {
        if (restored>0) ReplayModReplay.LOGGER.info("Terrain recovery supplied {} missing chunks during export",restored);
        terrain=null; restored=0;
    }
}
