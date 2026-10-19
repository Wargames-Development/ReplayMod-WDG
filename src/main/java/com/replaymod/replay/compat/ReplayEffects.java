package com.replaymod.replay.compat;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Replay-only bridge for MCHeli's visual explosion packet and transient FMUR effects. */
public final class ReplayEffects {
    private static final List<Explosion> pending = new ArrayList<>(), heard = new ArrayList<>();
    private static boolean seeking;
    private static final class Explosion {
        final double x, y, z; final float size; final long time;
        Explosion(double x, double y, double z, float size) { this.x = x; this.y = y; this.z = z; this.size = size; time = nanoTime(); }
    }
    public static void register() { FMLCommonHandler.instance().bus().register(new ReplayEffects()); }
    private static ReplayHandler replay() { return ReplayModReplay.instance == null ? null : ReplayModReplay.instance.getReplayHandler(); }
    public static long nanoTime() {
        ReplayHandler replay = replay();
        return replay == null ? System.nanoTime() : replay.getReplaySender().currentTimeStamp() * 1000000L;
    }
    public static synchronized void reset() {
        pending.clear(); heard.clear();
        try {
            java.lang.reflect.Field start = Class.forName("com.flansmod.client.TickHandlerClient").getDeclaredField("shotShakeStartNanos");
            start.setAccessible(true); start.setLong(null, Long.MIN_VALUE);
        } catch (ReflectiveOperationException ignored) { /* FMUR is optional. */ }
        try {
            java.lang.reflect.Field sound = Class.forName("com.flansmod.common.network.PacketPlaySound").getDeclaredField("lastExplosionSoundNanos");
            sound.setAccessible(true); sound.setLong(null, Long.MIN_VALUE);
            java.lang.reflect.Field queue = Class.forName("com.flansmod.client.MCHeliExplosionCompat").getDeclaredField("PENDING_EXPLOSIONS");
            queue.setAccessible(true); ((java.util.Queue<?>) queue.get(null)).clear();
        } catch (ReflectiveOperationException ignored) { }
    }
    public static void beginSeek() { seeking = true; reset(); }
    public static void endSeek() { reset(); seeking = false; }
    public static synchronized void packet(String channel, byte[] data) {
        if (seeking || replay() == null || !"MCHeli_CH".equals(channel) || data.length < 33) return;
        ByteBuffer packet = ByteBuffer.wrap(data); packet.get();
        if (packet.getInt() != 268437520) return;
        double x = packet.getDouble(), y = packet.getDouble(), z = packet.getDouble(); float size = packet.getFloat();
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(size) && size > 0) pending.add(new Explosion(x, y, z, size));
    }
    public static synchronized void observeSound(ISound sound) {
        if (replay() == null || sound == null) return;
        String path = sound.getSoundLocation().getResourcePath().toLowerCase(java.util.Locale.ROOT);
        if (path.contains("explo")) heard.add(new Explosion(sound.getXPosF(), sound.getYPosF(), sound.getZPosF(), 0));
        if (heard.size() > 256) heard.remove(0);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) { if (event.phase == TickEvent.Phase.END) flush(); }
    public static synchronized void flush() {
        ReplayHandler replay = replay();
        if (replay == null || seeking || replay.getReplaySender() instanceof com.replaymod.replay.FullReplaySender && ((com.replaymod.replay.FullReplaySender) replay.getReplaySender()).isHurrying()) { pending.clear(); return; }
        DistantHorizonsReplay.protectTerrain();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) { pending.clear(); return; }
        long now = nanoTime(); heard.removeIf(sound -> now < sound.time || now - sound.time > 250000000L);
        for (Iterator<Explosion> iterator = pending.iterator(); iterator.hasNext();) {
            Explosion blast = iterator.next(); iterator.remove();
            if (now < blast.time || now - blast.time > 250000000L) continue;
            try {
                Class.forName("com.flansmod.client.TickHandlerClient").getMethod("triggerMCHeliExplosionScreenShake", double.class, double.class, double.class, float.class).invoke(null, blast.x, blast.y, blast.z, blast.size);
            } catch (ReflectiveOperationException ignored) { }
            boolean alreadyHeard = false;
            for (Explosion sound : heard) if (Math.abs(sound.x - blast.x) < 2 && Math.abs(sound.y - blast.y) < 2 && Math.abs(sound.z - blast.z) < 2) { alreadyHeard = true; break; }
            if (alreadyHeard) continue;
            if (mc.thePlayer.getDistanceSq(blast.x, blast.y, blast.z) < 4096) {
                mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation("random.explode"), 4, 0.7f, (float) blast.x, (float) blast.y, (float) blast.z));
            } else {
                try {
                    Class.forName("com.flansmod.common.network.PacketPlaySound").getMethod("playDistantExplosionClient", double.class, double.class, double.class, float.class, float.class).invoke(null, blast.x, blast.y, blast.z, blast.size, 0f);
                } catch (ReflectiveOperationException ignored) { }
            }
        }
    }
}
