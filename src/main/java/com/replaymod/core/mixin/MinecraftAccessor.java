package com.replaymod.core.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

import java.util.concurrent.FutureTask;

import net.minecraft.client.resources.IResourcePack;
import java.util.List;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor
    Timer getTimer();
    @Accessor
    void setTimer(Timer value);

    @Accessor
    Queue<FutureTask<?>> getScheduledTasks();

    @Accessor
    CrashReport getCrashReporter();

    @Accessor
    List<IResourcePack> getDefaultResourcePacks();
}
