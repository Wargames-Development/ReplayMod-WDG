package com.replaymod.render.audio;

import com.replaymod.replay.*;
import com.replaymod.replay.compat.ReplayVehicleClock;
import com.replaymod.replay.mixin.MixinMCHSoundUpdater_MCHeli;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.audio.*;
import net.minecraft.util.ResourceLocation;
import org.junit.*;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.*;
import paulscode.sound.SoundSystem;
import sun.misc.Unsafe;
import static org.junit.Assert.*;

public class ReplaySoundPlaybackTest {
    private Backend backend;
    private Map<String,ISound> sounds;
    private ReplayModReplay previous;
    @Before public void setup() throws Exception {
        ReplaySoundPlayback.clear(); previous=ReplayModReplay.instance;
        backend=allocate(Backend.class); backend.calls=new ArrayList<>(); sounds=new LinkedHashMap<>();
        sounds.put("engine",new Sound(true,false)); sounds.put("shot",new Sound(false,false)); sounds.put("click",new Sound(false,true));
    }
    @After public void clear() { ReplaySoundPlayback.clear(); ReplayModReplay.instance=previous; }
    @Test public void pausePreservesWorldSourcesAndResumeDoesNotRestartOrAffectUiSounds() {
        frame(true,true); assertEquals(Arrays.asList("pause:engine","pause:shot"),backend.calls);
        for(int i=0;i<100;i++) frame(true,true);
        assertEquals("A frozen scene must not repeatedly restart or stop clips",2,backend.calls.size());
        frame(true,false); assertEquals(Arrays.asList("pause:engine","pause:shot","play:engine","play:shot"),backend.calls);
        frame(true,false); assertEquals(4,backend.calls.size());
    }
    @Test public void closingAGuiCannotAccidentallyResumeAFrozenReplaySound() {
        frame(true,true); backend.calls.clear(); ReplaySoundPlayback.resumed();
        frame(true,true); assertEquals(Arrays.asList("pause:engine","pause:shot"),backend.calls);
    }
    @Test public void clearedOrExpiredChannelsAreNeverResumedAfterASeek() {
        frame(true,true); backend.calls.clear(); sounds.remove("shot");
        frame(true,false); assertEquals(Collections.singletonList("play:engine"),backend.calls);
        frame(true,true); backend.calls.clear(); ReplaySoundPlayback.clear(); frame(true,false);
        assertTrue(backend.calls.isEmpty());
    }
    @Test public void explicitlyStoppedSoundsCannotBeResurrectedByPauseOrGuiResume() throws Exception {
        ReplayModReplay module=allocate(ReplayModReplay.class);
        set(module,ReplayModReplay.class,"replayHandler",allocate(ReplayHandler.class)); ReplayModReplay.instance=module;
        frame(true,true); backend.calls.clear(); ISound shot=sounds.get("shot");
        ReplaySoundPlayback.stop(shot);
        assertFalse(ReplaySoundPlayback.mayResume(shot));
        frame(true,true); frame(true,false);
        assertEquals(Collections.singletonList("play:engine"),backend.calls);
        ReplaySoundPlayback.started(shot); frame(true,true);
        assertTrue(backend.calls.contains("pause:shot"));
    }
    @Test public void liveGameplayDoesNotPauseOrRepositionNativeAudio() {
        frame(false,false); assertTrue(backend.calls.isEmpty());
    }
    @Test public void nativeEnginePositionFollowsEveryRenderedFrameAndReleasesItsBindingOnStop() throws Exception {
        net.minecraft.entity.player.EntityPlayerMP aircraft=allocate(net.minecraft.entity.player.EntityPlayerMP.class);
        Constructor<VehicleSound> constructor=VehicleSound.class.getDeclaredConstructor(net.minecraft.entity.Entity.class,String.class,double.class,boolean.class); constructor.setAccessible(true);
        VehicleSound vehicle=constructor.newInstance(aircraft,"f16",160,false);
        Field bindings=VehicleSound.class.getDeclaredField("playback"); bindings.setAccessible(true);
        ((Map<ISound,VehicleSound>)bindings.get(null)).put(sounds.get("engine"),vehicle);
        for(int frame=0;frame<120;frame++) {
            double camera=frame*400.0/60;
            aircraft.prevPosX=camera+20; aircraft.posX=camera+28;
            ReplaySoundPlayback.frame(backend,sounds,camera,0,0,0.5f,true,false);
            assertEquals(camera+2.4,backend.lastX,0.0001);
        }
        assertEquals(120,backend.positions);
        ReplaySoundPlayback.stop(sounds.get("engine")); frame(true,false); assertEquals(120,backend.positions);
    }
    @Test public void onlyReplayMchrLoopsGetProtectedChannels() throws Exception {
        ReplayModReplay.instance=null;
        assertFalse(ReplaySoundPlayback.priority(false,sounds.get("engine")));
        ReplayModReplay module=allocate(ReplayModReplay.class);
        set(module,ReplayModReplay.class,"replayHandler",allocate(ReplayHandler.class)); ReplayModReplay.instance=module;
        assertTrue(ReplaySoundPlayback.priority(false,sounds.get("engine")));
        assertFalse(ReplaySoundPlayback.priority(false,sounds.get("shot")));
        assertTrue(ReplaySoundPlayback.priority(true,sounds.get("shot")));
    }
    @Test public void engineRetryDelayResetsOnceAfterASeekAndRetainsNormalBackoffOtherwise() throws Exception {
        ReplayModReplay module=allocate(ReplayModReplay.class);
        set(module,ReplayModReplay.class,"replayHandler",allocate(ReplayHandler.class)); ReplayModReplay.instance=module;
        Updater updater=allocate(Updater.class);
        Method hook=MixinMCHSoundUpdater_MCHeli.class.getDeclaredMethod("replayMod$restartAfterSeek",org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class); hook.setAccessible(true);
        set(updater,MixinMCHSoundUpdater_MCHeli.class,"replayMod$soundRevision",Long.MIN_VALUE);
        set(updater,MixinMCHSoundUpdater_MCHeli.class,"soundDelay",20); hook.invoke(updater,new Object[]{null}); assertEquals(0,delay(updater));
        set(updater,MixinMCHSoundUpdater_MCHeli.class,"soundDelay",20); hook.invoke(updater,new Object[]{null}); assertEquals(20,delay(updater));
        ReplayVehicleClock.invalidate(); hook.invoke(updater,new Object[]{null}); assertEquals(0,delay(updater));
        ReplayModReplay.instance=null; set(updater,MixinMCHSoundUpdater_MCHeli.class,"soundDelay",20);
        ReplayVehicleClock.invalidate(); hook.invoke(updater,new Object[]{null}); assertEquals(20,delay(updater));
    }
    @Test public void nativeSoundManagerHasTheExactPerFrameAndSourceAllocationCallSites() throws Exception {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream("net/minecraft/client/audio/SoundManager.class")) {
            assertNotNull(input); ClassNode node=new ClassNode(); new ClassReader(input).accept(node,0);
            assertCall(node,"setListener","setListenerPosition","(FFF)V");
            assertCall(node,"playSound","newSource","(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V");
            assertCall(node,"resumeAllSounds","play","(Ljava/lang/String;)V");
            assertTrue(node.fields.stream().anyMatch(f->f.name.equals("playingSounds")&&f.desc.equals("Ljava/util/Map;")));
        }
    }
    private void frame(boolean active,boolean paused) { ReplaySoundPlayback.frame(backend,sounds,0,0,0,0.5f,active,paused); }
    private static int delay(Object updater) throws Exception { Field f=MixinMCHSoundUpdater_MCHeli.class.getDeclaredField("soundDelay"); f.setAccessible(true); return f.getInt(updater); }
    private static void assertCall(ClassNode node,String method,String call,String descriptor) {
        int count=0;
        for(MethodNode m:node.methods) if(m.name.equals(method)) for(AbstractInsnNode insn:m.instructions.toArray()) if(insn instanceof MethodInsnNode) {
            MethodInsnNode invoke=(MethodInsnNode)insn;
            if(invoke.owner.equals("net/minecraft/client/audio/SoundManager$SoundSystemStarterThread") && invoke.name.equals(call) && invoke.desc.equals(descriptor)) count++;
        }
        assertEquals(method+" "+call,1,count);
    }
    private static class Updater extends MixinMCHSoundUpdater_MCHeli { }
    private static class Backend extends SoundSystem {
        List<String> calls;
        float lastX; int positions;
        @Override public void pause(String channel) { calls.add("pause:"+channel); }
        @Override public void play(String channel) { calls.add("play:"+channel); }
        @Override public void setPosition(String channel,float x,float y,float z) { calls.add("position:"+channel+":"+x); lastX=x; positions++; }
    }
    private static class Sound extends MovingSound {
        Sound(boolean loop,boolean ui) { super(new ResourceLocation("mcheli:plane")); repeat=loop; if(ui) attenuationType=ISound.AttenuationType.NONE; }
        @Override public void update() { }
    }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
    private static void set(Object target,Class<?> type,String name,Object value) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); f.set(target,value); }
}
