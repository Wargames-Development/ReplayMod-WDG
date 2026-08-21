package com.replaymod.recording.mixin;

import net.minecraft.entity.DataWatcher;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S0CPacketSpawnPlayer.class)
public interface SPacketSpawnPlayerAccessor {
    @Accessor("field_148960_i")
    DataWatcher getDataManager();
    @Accessor("field_148960_i")
    void setDataManager(DataWatcher value);
}
