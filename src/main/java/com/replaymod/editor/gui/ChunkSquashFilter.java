package com.replaymod.editor.gui;

import com.replaymod.core.utils.LegacyChunkPacket;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.filter.SquashFilter;
import com.replaymod.replaystudio.filter.StreamFilter;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.stream.IteratorStream;
import com.replaymod.replaystudio.stream.PacketStream;
import com.replaymod.replaystudio.us.myles.ViaVersion.api.protocol.ProtocolVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Adapt only chunk headers at ReplayStudio's vanilla 1.7.10 squash boundary. */
final class ChunkSquashFilter {
    private final SquashFilter delegate;
    private boolean wideMasks;
    ChunkSquashFilter() { this(new SquashFilter(),false); }
    private ChunkSquashFilter(SquashFilter delegate,boolean wideMasks) { this.delegate=delegate; this.wideMasks=wideMasks; }
    ChunkSquashFilter copy() { return new ChunkSquashFilter(delegate.copy(),wideMasks); }
    void release() { delegate.release(); }
    boolean onPacket(PacketStream stream,PacketData data) throws IOException {
        Packet packet=data.getPacket();
        if (packet.atLeast(ProtocolVersion.v1_8)) return delegate.onPacket(stream,data);
        if (packet.getType()!=PacketType.ChunkData && packet.getType()!=PacketType.BulkChunkData)
            return delegate.onPacket(stream,data);
        byte[] bytes=bytes(packet),standard;
        if (packet.getType()==PacketType.ChunkData) {
            byte[] normalized=LegacyChunkPacket.normalizeSingle(bytes);
            wideMasks|=LegacyChunkPacket.extendedSingle(normalized);
            standard=LegacyChunkPacket.standardSingle(normalized);
        } else if (packet.getType()==PacketType.BulkChunkData) {
            wideMasks|=LegacyChunkPacket.extendedBulk(bytes);
            standard=LegacyChunkPacket.standardBulk(bytes);
        } else return delegate.onPacket(stream,data);
        if (standard==bytes) return delegate.onPacket(stream,data);
        Packet converted=replace(packet,standard);
        try { return delegate.onPacket(stream,new PacketData(data.getTime(),converted)); }
        finally { converted.release(); }
    }
    void onEnd(PacketStream stream,long time) throws IOException {
        List<PacketData> packets=new ArrayList<>();
        delegate.onEnd(new IteratorStream(packets.listIterator(),(StreamFilter)null),time);
        for (PacketData data:packets) {
            Packet packet=data.getPacket();
            if (packet.getType()==PacketType.ChunkData && !packet.atLeast(ProtocolVersion.v1_8)) {
                byte[] bytes=bytes(packet);
                byte[] normalized=wideMasks ? LegacyChunkPacket.widenSingle(bytes) : LegacyChunkPacket.normalizeSingle(bytes);
                if (normalized!=bytes) {
                    Packet converted=replace(packet,normalized); packet.release();
                    stream.insert(data.getTime(),converted); continue;
                }
            }
            stream.insert(data);
        }
    }
    private static byte[] bytes(Packet packet) {
        byte[] bytes=new byte[packet.getBuf().readableBytes()];
        packet.getBuf().getBytes(packet.getBuf().readerIndex(),bytes); return bytes;
    }
    private static Packet replace(Packet packet,byte[] bytes) throws IOException {
        Packet result=new Packet(packet.getRegistry(),packet.getType());
        try (Packet.Writer out=result.overwrite()) { out.writeBytes(bytes); }
        return result;
    }
}
