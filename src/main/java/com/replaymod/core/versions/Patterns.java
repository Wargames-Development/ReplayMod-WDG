package com.replaymod.core.versions;

import com.replaymod.gradle.remap.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import net.minecraft.client.gui.GuiButton;

import java.util.concurrent.Callable;

import net.minecraft.entity.EntityLivingBase;

import java.util.Collection;
import java.util.List;

class Patterns {
    @Pattern
    private static void addCrashCallable(CrashReportCategory category, String name, Callable<String> callable) {
        category.addCrashSectionCallable(name, callable);
    }

    @Pattern
    private static double Entity_getX(Entity entity) {
        return entity.posX;
    }

    @Pattern
    private static double Entity_getY(Entity entity) {
        return entity.posY;
    }

    @Pattern
    private static double Entity_getZ(Entity entity) {
        return entity.posZ;
    }

    @Pattern
    private static void Entity_setPos(Entity entity, double x, double y, double z) {
        { net.minecraft.entity.Entity self = entity; self.posX = x; self.posY = y; self.posZ = z; }
    }

    @Pattern
    private static void setWidth(GuiButton button, int value) {
        button.width = value;
    }

    @Pattern
    private static int getWidth(GuiButton button) {
        return button.width;
    }

    @Pattern
    private static int getHeight(GuiButton button) {
        return button.height;
    }

    @Pattern
    private static String readString(PacketBuffer buffer, int max) {
        return com.replaymod.core.versions.MCVer.tryReadString(buffer, max);
    }

    @Pattern
    private static EntityLivingBase getRenderViewEntity(Minecraft mc) {
        return mc.renderViewEntity;
    }

    @Pattern
    private static void setRenderViewEntity(Minecraft mc, EntityLivingBase entity) {
        mc.renderViewEntity = entity;
    }

    @Pattern
    private static Entity getVehicle(Entity passenger) {
        return passenger.ridingEntity;
    }

    @Pattern
    private static Iterable<Entity> loadedEntityList(WorldClient world) {
        return ((java.util.List<net.minecraft.entity.Entity>) world.loadedEntityList);
    }

    @Pattern
    private static Collection<Entity>[] getEntitySectionArray(Chunk chunk) {
        return chunk.entityLists;
    }

    @Pattern
    private static List<? extends EntityPlayer> playerEntities(World world) {
        return ((List<? extends net.minecraft.entity.player.EntityPlayer>) world.playerEntities);
    }

    @Pattern
    private static boolean isOnMainThread(Minecraft mc) {
        return mc.isCallingFromMinecraftThread();
    }

    @Pattern
    private static void scheduleOnMainThread(Minecraft mc, Runnable runnable) {
        mc.addScheduledTask(runnable);
    }

    @Pattern
    private static Window getWindow(Minecraft mc) {
        return new com.replaymod.core.versions.Window(mc);
    }

    @Pattern
    private static BufferBuilder Tessellator_getBuffer(Tessellator tessellator) {
        return new BufferBuilder(tessellator);

    }

    @Pattern
    private static void BufferBuilder_beginPosCol(BufferBuilder buffer, int mode) {
        buffer.startDrawing(mode /* POSITION_COLOR */);
    }

    @Pattern
    private static void BufferBuilder_addPosCol(BufferBuilder buffer, double x, double y, double z, int r, int g, int b, int a) {
        { BufferBuilder $buffer = buffer; double $x = x; double $y = y; double $z = z; $buffer.setColorRGBA(r, g, b, a); $buffer.addVertex($x, $y, $z); }
    }

    @Pattern
    private static void BufferBuilder_beginPosTex(BufferBuilder buffer, int mode) {
        buffer.startDrawing(mode /* POSITION_TEXTURE */);
    }

    @Pattern
    private static void BufferBuilder_addPosTex(BufferBuilder buffer, double x, double y, double z, float u, float v) {
        buffer.addVertexWithUV(x, y, z, u, v);
    }

    @Pattern
    private static void BufferBuilder_beginPosTexCol(BufferBuilder buffer, int mode) {
        buffer.startDrawing(mode /* POSITION_TEXTURE_COLOR */);
    }

    @Pattern
    private static void BufferBuilder_addPosTexCol(BufferBuilder buffer, double x, double y, double z, float u, float v, int r, int g, int b, int a) {
        { BufferBuilder $buffer = buffer; double $x = x; double $y = y; double $z = z; float $u = u; float $v = v; $buffer.setColorRGBA(r, g, b, a); $buffer.addVertexWithUV($x, $y, $z, $u, $v); }
    }

    @Pattern
    private static Tessellator Tessellator_getInstance() {
        return Tessellator.instance;
    }

    @Pattern
    private static RenderManager getEntityRenderDispatcher(Minecraft mc) {
        return com.replaymod.core.versions.MCVer.getRenderManager(mc);
    }

    @Pattern
    private static float getCameraYaw(RenderManager dispatcher) {
        return dispatcher.playerViewY;
    }

    @Pattern
    private static float getCameraPitch(RenderManager dispatcher) {
        return dispatcher.playerViewX;
    }

    @Pattern
    private static float getRenderPartialTicks(Minecraft mc) {
        return ((com.replaymod.core.mixin.MinecraftAccessor) mc).getTimer().renderPartialTicks;
    }

    @Pattern
    private static TextureManager getTextureManager(Minecraft mc) {
        return mc.renderEngine;
    }

    @Pattern
    private static String getBoundKeyName(KeyBinding keyBinding) {
        return org.lwjgl.input.Keyboard.getKeyName(keyBinding.getKeyCode());
    }

    @Pattern
    private static PositionedSoundRecord master(ResourceLocation sound, float pitch) {
        return PositionedSoundRecord.createPositionedSoundRecord(sound, pitch);
    }

    @Pattern
    private static boolean isKeyBindingConflicting(KeyBinding a, KeyBinding b) {
        return (a.getKeyCode() == b.getKeyCode());
    }
}
