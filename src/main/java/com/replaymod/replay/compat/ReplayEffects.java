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
    private static final List<Explosion> flares = new ArrayList<>(), flareBursts = new ArrayList<>();
    private static boolean seeking;
    private static final class Explosion {
        final double x, y, z; final float size; final long time;
        Explosion(double x, double y, double z, float size) { this.x = x; this.y = y; this.z = z; this.size = size; time = nanoTime(); }
    }
    public static void register() {
        ReplayEffects effects = new ReplayEffects();
        FMLCommonHandler.instance().bus().register(effects);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(effects);
    }
    @SubscribeEvent public void spawned(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if (!event.world.isRemote || seeking || replay()==null || !event.entity.getClass().getName().equals("mcheli.flare.MCH_EntityFlare")) return;
        flare(event.entity.posX,event.entity.posY,event.entity.posZ);
    }
    static synchronized void flare(double x,double y,double z) {
        long now=nanoTime();
        flareBursts.removeIf(b -> now<b.time || now-b.time>80000000L);
        for (Explosion b:flareBursts) if (Math.abs(x-b.x)<12 && Math.abs(y-b.y)<12 && Math.abs(z-b.z)<12) return;
        Explosion burst=new Explosion(x,y,z,0); flareBursts.add(burst); flares.add(burst);
    }
    private static ReplayHandler replay() { return ReplayModReplay.instance == null ? null : ReplayModReplay.instance.getReplayHandler(); }
    public static long nanoTime() {
        ReplayHandler replay = replay();
        return replay == null ? System.nanoTime() : replay.getReplaySender().currentTimeStamp() * 1000000L;
    }
    public static synchronized void reset() {
        pending.clear(); heard.clear(); flares.clear(); flareBursts.clear();
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
    public static boolean isSeeking() { return seeking; }
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
        net.minecraft.entity.Entity listener=Minecraft.getMinecraft().renderViewEntity;
        double range=16*Math.max(1,sound.getVolume());
        if (path.contains("explo") && (listener==null || sound.getAttenuationType()==ISound.AttenuationType.NONE
                || listener.getDistanceSq(sound.getXPosF(),sound.getYPosF(),sound.getZPosF())<range*range))
            heard.add(new Explosion(sound.getXPosF(), sound.getYPosF(), sound.getZPosF(), 0));
        if (heard.size() > 256) heard.remove(0);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) { if (event.phase == TickEvent.Phase.END) flush(); }
    public static synchronized void flush() {
        ReplayHandler replay = replay();
        if (replay == null || seeking || replay.getReplaySender() instanceof com.replaymod.replay.FullReplaySender && ((com.replaymod.replay.FullReplaySender) replay.getReplaySender()).isHurrying()) { pending.clear(); flares.clear(); return; }
        DistantHorizonsReplay.protectTerrain();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) { pending.clear(); flares.clear(); return; }
        long now = nanoTime(); heard.removeIf(sound -> now < sound.time || now - sound.time > 250000000L);
        for (Explosion flare:flares) if (now>=flare.time && now-flare.time<=250000000L)
            mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation("mcheli:flare_deploy"),10,1,(float)flare.x,(float)flare.y,(float)flare.z));
        flares.clear();
        for (Iterator<Explosion> iterator = pending.iterator(); iterator.hasNext();) {
            Explosion blast = iterator.next(); iterator.remove();
            if (now < blast.time || now - blast.time > 250000000L) continue;
            try {
                Class.forName("com.flansmod.client.TickHandlerClient").getMethod("triggerMCHeliExplosionScreenShake", double.class, double.class, double.class, float.class).invoke(null, blast.x, blast.y, blast.z, blast.size);
            } catch (ReflectiveOperationException ignored) { }
            boolean alreadyHeard = false;
            for (Explosion sound : heard) if (Math.abs(sound.x - blast.x) < 2 && Math.abs(sound.y - blast.y) < 2 && Math.abs(sound.z - blast.z) < 2) { alreadyHeard = true; break; }
            if (alreadyHeard) continue;
            net.minecraft.entity.Entity listener=mc.renderViewEntity==null?mc.thePlayer:mc.renderViewEntity;
            double[] source=explosionSource(blast.x,blast.y,blast.z,listener.posX,listener.posY,listener.posZ);
            if (source!=null) mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation("random.explode"), 4, 0.7f, (float)source[0],(float)source[1],(float)source[2]));
        }
    }
    /** Preserve near-field sound and audible distant impacts from the cinematic camera. */
    public static double[] explosionSource(double x,double y,double z,double cx,double cy,double cz) {
        double dx=x-cx,dy=y-cy,dz=z-cz,d=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (d>=2048) return null;
        double projected=d<=48?d:48+(d-48)*15/2000,scale=d==0?1:projected/d;
        return new double[]{cx+dx*scale,cy+dy*scale,cz+dz*scale};
    }
}
