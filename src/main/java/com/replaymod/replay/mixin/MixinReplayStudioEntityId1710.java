package com.replaymod.replay.mixin;

import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.packets.EntityId;
import com.replaymod.replaystudio.us.myles.ViaVersion.api.protocol.ProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

/**
 * ReplayStudio a1f82a7 assumes every pre-1.8 EntityAnimation entity id is a
 * fixed-width int. Minecraft 1.7.10's S0BPacketAnimation uses a VarInt instead.
 *
 * Keep the replay packet bytes vanilla-correct and repair only ReplayStudio's
 * tracker-side interpretation.
 */
@Mixin(value = EntityId.class, remap = false)
public abstract class MixinReplayStudioEntityId1710 {

    @Inject(method = "getEntityId", at = @At("HEAD"), cancellable = true, remap = false)
    private static void replaymod$read1710AnimationEntityId(
            Packet packet,
            CallbackInfoReturnable<Integer> cir
    ) throws IOException {
        if (packet.getType() == PacketType.EntityAnimation && !packet.atLeast(ProtocolVersion.v1_8)) {
            try (Packet.Reader in = packet.reader()) {
                cir.setReturnValue(in.readVarInt());
            }
        }
    }
}
