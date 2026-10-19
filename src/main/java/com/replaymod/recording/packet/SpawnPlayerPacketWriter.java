package com.replaymod.recording.packet;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.entity.DataWatcher;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;

import java.io.IOException;

/** Writes the 1.7.10 spawn-player format without changing the live player's profile. */
public final class SpawnPlayerPacketWriter {
    private SpawnPlayerPacketWriter() {
    }

    public static void write(S0CPacketSpawnPlayer packet, PacketBuffer out) throws IOException {
        GameProfile profile = packet.func_148948_e();
        out.writeVarIntToBuffer(packet.func_148943_d());
        out.writeStringToBuffer(profile.getId() == null ? "" : profile.getId().toString());
        out.writeStringToBuffer(profile.getName() == null ? "ReplayPlayer" : profile.getName());
        out.writeVarIntToBuffer(profile.getProperties().size());
        for (Property property : profile.getProperties().values()) {
            out.writeStringToBuffer(property.getName());
            out.writeStringToBuffer(property.getValue());
            // Local session properties can be unsigned. Unlike newer protocols,
            // 1.7.10 always writes a signature string, so vanilla's writer throws
            // on null and silently loses the recorded pilot's entire spawn.
            out.writeStringToBuffer(property.getSignature() == null ? "" : property.getSignature());
        }
        out.writeInt(packet.func_148942_f());
        out.writeInt(packet.func_148949_g());
        out.writeInt(packet.func_148946_h());
        out.writeByte(packet.func_148941_i());
        out.writeByte(packet.func_148945_j());
        out.writeShort(packet.func_148947_k());
        DataWatcher.writeWatchedListToPacketBuffer(packet.func_148944_c(), out);
    }
}
