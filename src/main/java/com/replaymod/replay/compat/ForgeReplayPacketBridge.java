package com.replaymod.replay.compat;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

/**
 * Restores the Forge/FML packet representation that a live 1.7.10 client sees
 * before registered mod channels handle incoming custom payloads.
 *
 * Replay files must store {@link FMLProxyPacket}s as vanilla
 * {@link S3FPacketCustomPayload}s because ReplayStudio serializes Minecraft
 * packets.  During playback ReplayMod uses a deliberately small embedded
 * network pipeline, so Forge does not get the normal opportunity to wrap a
 * registered mod channel back into an FMLProxyPacket.  Without that wrapper,
 * MessageToMessageCodec/SimpleNetworkWrapper handlers used by mods never see
 * their recorded packets.
 *
 * This bridge is intentionally generic.  It only restores channels that are
 * actually registered with Forge on the replaying client and leaves vanilla,
 * ReplayMod, and FML handshake/control payloads alone.
 */
public final class ForgeReplayPacketBridge {
    private ForgeReplayPacketBridge() {
    }

    /**
     * Convert a recorded client-bound custom payload back to FML's proxy form
     * when Forge owns the channel on the client side.
     *
     * @param packet deserialized replay packet
     * @return an FMLProxyPacket for registered mod channels, otherwise the
     *         original packet instance
     */
    public static Packet restoreClientboundForgePacket(Packet packet) {
        if (!(packet instanceof S3FPacketCustomPayload)) {
            return packet;
        }

        S3FPacketCustomPayload payload = (S3FPacketCustomPayload) packet;
        String channel = payload.func_149169_c();

        if (!isModPayloadChannel(channel)) {
            return packet;
        }

        if (!NetworkRegistry.INSTANCE.hasChannel(channel, Side.CLIENT)) {
            return packet;
        }

        return new FMLProxyPacket(payload);
    }

    private static boolean isModPayloadChannel(String channel) {
        if (channel == null || channel.length() == 0) {
            return false;
        }

        // Vanilla payloads (resource packs, books, brand, etc.) are handled by
        // NetHandlerPlayClient and must remain S3FPacketCustomPayloads.
        if (channel.startsWith("MC|")) {
            return false;
        }

        // ReplayMod does not reproduce Forge's server handshake dispatcher.
        // These are connection-control channels, not gameplay mod channels.
        if (channel.startsWith("FML") || channel.charAt(0) == '\u0001') {
            return false;
        }

        return true;
    }
}
