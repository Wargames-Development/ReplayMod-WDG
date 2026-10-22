package com.replaymod.render.audio;

import java.lang.reflect.*;
import java.util.*;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.junit.*;
import sun.misc.Unsafe;
import static org.junit.Assert.*;

/** Runs the real export voice lifecycle without starting Minecraft or OpenAL. */
public class ExportSoundLifecycleTest {
    private ExportAudio session;
    private PcmMixer mixer;
    private SoundManager manager;
    private static final ResourceLocation EVENT = new ResourceLocation("mcheli:plane");
    private static final ResourceLocation ASSET = new ResourceLocation("mcheli:sounds/plane.ogg");
    @Before public void setup() throws Exception {
        session = allocate(ExportAudio.class); mixer = new PcmMixer();
        set(session,ExportAudio.class,"soundHook",(java.util.function.BiFunction<SoundManager,ISound,ISound>)(manager,sound)->sound);
        Minecraft mc = allocate(Minecraft.class);
        FakeHandler handler = allocate(FakeHandler.class);
        handler.event = new SoundEventAccessorComposite(EVENT, 1, 1, SoundCategory.BLOCKS);
        handler.event.addSoundToEventPool(new ISoundEventAccessor() {
            public int func_148721_a() { return 1; }
            public SoundPoolEntry func_148720_g() { return new SoundPoolEntry(ASSET,1,1,false); }
        });
        manager = allocate(SoundManager.class); set(manager, SoundManager.class, "sndHandler", handler);
        set(mc, Minecraft.class, "mcSoundHandler", handler);
        mc.gameSettings = allocate(Settings.class);
        set(session, ExportAudio.class, "mc", mc);
        set(session, ExportAudio.class, "mixer", mixer);
        set(session, ExportAudio.class, "playing", new IdentityHashMap<>());
        set(session, ExportAudio.class, "delayed", new IdentityHashMap<>());
        set(session, ExportAudio.class, "unknown", new HashSet<>());
        Map<ResourceLocation,PcmMixer.Clip> clips = new HashMap<>();
        byte[] pcm = new byte[48000 * 2];
        for (int i=0; i<48000; i++) { pcm[i*2]=0x10; pcm[i*2+1]=0x27; }
        clips.put(ASSET, new PcmMixer.Clip(pcm, new AudioFormat(48000,16,1,true,false)));
        set(session, ExportAudio.class, "clips", clips);
        set(null, ExportAudio.class, "active", session);
        session.beginFrame(0);
    }
    @After public void clear() throws Exception { ExportAudio.stopAll(); set(null, ExportAudio.class, "active", null); }
    @Test public void anImpactOutsideVanillaRangeStillProducesOfflineAudioAtTheCinematicCamera() throws Exception {
        Minecraft mc=(Minecraft)get(session,ExportAudio.class,"mc");
        mc.renderViewEntity=allocate(net.minecraft.entity.player.EntityPlayerMP.class);
        double[] source=com.replaymod.replay.compat.ReplayEffects.explosionSource(800,0,0,0,0,0);
        ISound impact=new PositionedSoundRecord(new ResourceLocation("random.explode"),4,.7f,(float)source[0],(float)source[1],(float)source[2]);
        ExportAudio.capture(manager,impact);
        byte[] audio=mix(800); boolean audible=false;
        for (byte sample:audio) audible|=sample!=0;
        assertTrue("Recorded impacts must reach the offline WAV even beyond 64 blocks",audible);
        assertTrue(voice(impact).left>0 || voice(impact).right>0);
    }
    private static Object get(Object target,Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    @Test public void engineLoopReportsPlayingAndRepeatedStartDoesNotResetItsPhase() throws Exception {
        Sound engine = new Sound(true);
        ExportAudio.capture(manager, engine); assertEquals(Boolean.TRUE, ExportAudio.isPlaying(engine));
        mix(800); PcmMixer.Voice voice = voice(engine); double position=voice.position;
        for (int i=0;i<100;i++) ExportAudio.capture(manager, engine);
        assertSame(voice, voice(engine)); assertEquals(position,voice.position,0);
        mix(96000); assertEquals(Boolean.TRUE, ExportAudio.isPlaying(engine));
        ExportAudio.stop(engine); assertEquals(Boolean.FALSE, ExportAudio.isPlaying(engine));
        ExportAudio.capture(manager, engine); assertNotSame(voice,voice(engine));
    }
    @Test public void replacementSoundRetainsTheOriginalQueryAndStopIdentity() throws Exception {
        Sound requested=new Sound(true), replacement=new Sound(true);
        Method play=ExportAudio.class.getDeclaredMethod("play",ISound.class,ISound.class); play.setAccessible(true);
        play.invoke(session,requested,replacement);
        assertEquals(Boolean.TRUE,ExportAudio.isPlaying(requested));
        PcmMixer.Voice voice=voice(requested); ExportAudio.stop(requested); assertTrue(voice.stopped);
    }
    @Test public void engineRemainsAudibleThroughoutFastFollowFramesInTheActualMixer() throws Exception {
        Sound engine=new Sound(true); ExportAudio.capture(manager,engine);
        TestEntity aircraft=allocate(TestEntity.class);
        net.minecraft.entity.player.EntityPlayerMP listener=allocate(net.minecraft.entity.player.EntityPlayerMP.class);
        Field mcField=ExportAudio.class.getDeclaredField("mc"); mcField.setAccessible(true);
        ((Minecraft)mcField.get(session)).renderViewEntity=listener;
        Constructor<VehicleSound> binding=VehicleSound.class.getDeclaredConstructor(net.minecraft.entity.Entity.class,String.class,double.class,boolean.class);
        binding.setAccessible(true); ExportAudio.bindVehicle(engine,binding.newInstance(aircraft,"f16",160,false));
        for(int frame=0;frame<120;frame++) {
            listener.posX=frame*400.0/60; aircraft.posX=listener.posX+24;
            mix(800);
            assertEquals("No silent slices between aircraft updates",0.85,voice(engine).left,0.00001);
            assertEquals(0,voice(engine).right,0.00001);
        }
    }
    @Test public void warmupKeepsOneShotTailsAndAdvancesLoopPhase() throws Exception {
        set(session,ExportAudio.class,"capturing",false); session.beginWarmup();
        Sound shot=new Sound(false), engine=new Sound(true);
        ExportAudio.capture(manager,shot); ExportAudio.capture(manager,engine);
        session.advanceWarmup(600); session.endWarmup();
        assertEquals(28800,voice(shot).position,0); assertEquals(28800,voice(engine).position,0);
        session.beginFrame(0); byte[] tail=mix(19200); assertTrue(tail[10]!=0 || tail[11]!=0);
        mix(1); assertEquals(Boolean.FALSE,ExportAudio.isPlaying(shot));
        assertEquals(Boolean.TRUE,ExportAudio.isPlaying(engine));
    }
    @Test public void delayedSoundsUseReplayTicksAndExplicitStopCancelsThem() throws Exception {
        Sound shot=new Sound(false), cancelled=new Sound(false);
        assertTrue(ExportAudio.delay(manager,shot,2)); ExportAudio.delay(manager,cancelled,2); ExportAudio.stop(cancelled);
        assertEquals(Boolean.FALSE,ExportAudio.isPlaying(shot));
        ExportAudio.tick(); assertEquals(Boolean.FALSE,ExportAudio.isPlaying(shot));
        ExportAudio.tick(); assertEquals(Boolean.TRUE,ExportAudio.isPlaying(shot));
        assertEquals(Boolean.FALSE,ExportAudio.isPlaying(cancelled));
        ExportAudio.delay(manager,cancelled,1); ExportAudio.stopAll(); ExportAudio.tick();
        assertEquals(Boolean.FALSE,ExportAudio.isPlaying(cancelled));
    }
    @Test public void tickableSoundsUpdateExactlyOncePerReplayTickAndReleaseOnCompletion() throws Exception {
        Sound sound=new Sound(true); ExportAudio.capture(manager,sound);
        mix(800); mix(800); assertEquals(0,sound.updates);
        ExportAudio.tick(); assertEquals(1,sound.updates);
        sound.done=true; mix(1); assertEquals(Boolean.FALSE,ExportAudio.isPlaying(sound));
    }
    @Test public void outsideExportEveryHookFallsBackToVanilla() throws Exception {
        set(null,ExportAudio.class,"active",null); Sound sound=new Sound(true);
        assertNull(ExportAudio.isPlaying(sound)); assertFalse(ExportAudio.capture(manager,sound)); assertFalse(ExportAudio.delay(manager,sound,2));
    }
    private byte[] mix(int samples) throws Exception { Method m=ExportAudio.class.getDeclaredMethod("mix",int.class); m.setAccessible(true); return (byte[])m.invoke(session,samples); }
    private PcmMixer.Voice voice(ISound sound) throws Exception {
        Field f=ExportAudio.class.getDeclaredField("playing"); f.setAccessible(true);
        Object entry=((Map<?,?>)f.get(session)).get(sound); Field v=entry.getClass().getDeclaredField("voice"); v.setAccessible(true); return (PcmMixer.Voice)v.get(entry);
    }
    private static class Settings extends GameSettings { @Override public float getSoundLevel(SoundCategory category) { return 1; } }
    private static class TestEntity extends net.minecraft.entity.Entity {
        TestEntity() { super(null); }
        protected void entityInit() { }
        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) { }
        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) { }
    }
    private static class FakeHandler extends SoundHandler {
        SoundEventAccessorComposite event;
        FakeHandler() { super(null,null); }
        @Override public SoundEventAccessorComposite getSound(ResourceLocation location) { return event; }
    }
    private static class Sound extends MovingSound {
        int updates; boolean done;
        Sound(boolean repeat) { super(EVENT); this.repeat=repeat; volume=1; }
        @Override public void update() { updates++; donePlaying=done; }
        @Override public boolean isDonePlaying() { return done; }
    }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
    private static void set(Object target,Class<?> type,String name,Object value) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); f.set(target,value); }
}
