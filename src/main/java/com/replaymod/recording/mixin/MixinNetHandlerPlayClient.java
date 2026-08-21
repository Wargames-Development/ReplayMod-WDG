package com.replaymod.recording.mixin;

import com.replaymod.core.versions.MCVer;
import com.replaymod.recording.handler.RecordingEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S07PacketRespawn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.play.server.S01PacketJoinGame;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    // The stupid name is required as otherwise Mixin treats it as a shadow, seemingly ignoring the lack of @Shadow
    private static Minecraft mcStatic = MCVer.getMinecraft();

    public RecordingEventHandler getRecordingEventHandler() {
        return ((RecordingEventHandler.RecordingEventSender) mcStatic.renderGlobal).getRecordingEventHandler();
    }

    /**
     * Record the own player entity joining the world.
     * We cannot use the {@link net.minecraftforge.event.entity.EntityJoinWorldEvent} because the entity id
     * of the player is set afterwards and the tablist entry might not yet be sent.
     * @param packet The packet
     * @param ci Callback info
     */
    @Inject(method = "handleJoinGame", at=@At("RETURN"))
    public void recordOwnJoin(S01PacketJoinGame packet, CallbackInfo ci) {
        RecordingEventHandler handler = getRecordingEventHandler();
        if (handler != null) {
            handler.spawnRecordingPlayer();
        }
    }

    /**
     * Record the own player entity respawning.
     * We cannot use the {@link net.minecraftforge.event.entity.EntityJoinWorldEvent} because that would also include
     * the first spawn which is already handled by the above method.
     * @param packet The packet
     * @param ci Callback info
     */
    @Inject(method = "handleRespawn", at=@At("RETURN"))
    public void recordOwnRespawn(S07PacketRespawn packet, CallbackInfo ci) {
        RecordingEventHandler handler = getRecordingEventHandler();
        if (handler != null) {
            handler.spawnRecordingPlayer();
        }
    }
}
