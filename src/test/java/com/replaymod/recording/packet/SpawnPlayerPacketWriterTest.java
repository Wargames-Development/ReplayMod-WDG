package com.replaymod.recording.packet;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.DataWatcher;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class SpawnPlayerPacketWriterTest {
    @Test
    public void unsignedPropertiesDoNotLosePlayerSpawn() throws Exception {
        GameProfile profile = profile("RecordedPilot");
        Property textures = new Property("textures", "recorded-textures");
        profile.getProperties().put("textures", textures);

        S0CPacketSpawnPlayer decoded = roundTrip(profile);
        Property recorded = decoded.func_148948_e().getProperties().get("textures").iterator().next();
        assertEquals("recorded-textures", recorded.getValue());
        assertEquals("", recorded.getSignature());
        assertNull("The live session profile must remain unsigned", textures.getSignature());
        assertPlayerState(decoded, profile);
    }

    @Test
    public void signedPropertiesArePreserved() throws Exception {
        GameProfile profile = profile("RecordedPilot");
        profile.getProperties().put("textures", new Property("textures", "value", "signature"));

        S0CPacketSpawnPlayer decoded = roundTrip(profile);
        Property recorded = decoded.func_148948_e().getProperties().get("textures").iterator().next();
        assertEquals("value", recorded.getValue());
        assertEquals("signature", recorded.getSignature());
        assertPlayerState(decoded, profile);
    }

    @Test
    public void profileWithoutPropertiesStillRoundTrips() throws Exception {
        GameProfile profile = profile("RecordedPilot");
        S0CPacketSpawnPlayer decoded = roundTrip(profile);
        assertTrue(decoded.func_148948_e().getProperties().isEmpty());
        assertPlayerState(decoded, profile);
    }

    @Test
    public void namelessProfileGetsAReplayOnlyName() throws Exception {
        GameProfile profile = profile(null);
        assertEquals("ReplayPlayer", roundTrip(profile).func_148948_e().getName());
        assertNull(profile.getName());
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.fromString("12345678-1234-1234-1234-123456789abc"), name);
    }

    private static S0CPacketSpawnPlayer roundTrip(GameProfile profile) throws Exception {
        ByteBuf bytes = Unpooled.buffer();
        try {
            PacketBuffer buffer = new PacketBuffer(bytes);
            SpawnPlayerPacketWriter.write(new SpawnPacket(profile), buffer);
            S0CPacketSpawnPlayer decoded = new S0CPacketSpawnPlayer();
            decoded.readPacketData(buffer);
            assertEquals("The vanilla reader must consume the complete packet", 0, bytes.readableBytes());
            return decoded;
        } finally {
            bytes.release();
        }
    }

    private static void assertPlayerState(S0CPacketSpawnPlayer packet, GameProfile profile) {
        assertEquals(profile.getId(), packet.func_148948_e().getId());
        assertEquals(profile.getName(), packet.func_148948_e().getName());
        assertEquals(163951, packet.func_148943_d());
        assertEquals(-1000, packet.func_148942_f());
        assertEquals(2464, packet.func_148949_g());
        assertEquals(8192, packet.func_148946_h());
        assertEquals((byte) -90, packet.func_148941_i());
        assertEquals((byte) 10, packet.func_148945_j());
        assertEquals(0, packet.func_148947_k());
        assertEquals(2, packet.func_148944_c().size());
        DataWatcher.WatchableObject flags = (DataWatcher.WatchableObject) packet.func_148944_c().get(0);
        DataWatcher.WatchableObject health = (DataWatcher.WatchableObject) packet.func_148944_c().get(1);
        assertEquals(0, flags.getDataValueId());
        assertEquals((byte) 2, flags.getObject());
        assertEquals(6, health.getDataValueId());
        assertEquals(18.5F, health.getObject());
    }

    private static class SpawnPacket extends S0CPacketSpawnPlayer {
        private final GameProfile profile;

        SpawnPacket(GameProfile profile) { this.profile = profile; }

        @Override public GameProfile func_148948_e() { return profile; }
        @Override public int func_148943_d() { return 163951; }
        @Override public int func_148942_f() { return -1000; }
        @Override public int func_148949_g() { return 2464; }
        @Override public int func_148946_h() { return 8192; }
        @Override public byte func_148941_i() { return (byte) -90; }
        @Override public byte func_148945_j() { return (byte) 10; }
        @Override public int func_148947_k() { return 0; }
        @Override public List<DataWatcher.WatchableObject> func_148944_c() {
            return Arrays.asList(new DataWatcher.WatchableObject(0, 0, (byte) 2),
                    new DataWatcher.WatchableObject(3, 6, 18.5F));
        }
    }
}
