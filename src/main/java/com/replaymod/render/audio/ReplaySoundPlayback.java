package com.replaymod.render.audio;

import com.replaymod.replay.*;
import com.replaymod.replay.compat.ReplayVehicleClock;
import java.util.*;
import net.minecraft.client.audio.ISound;
import paulscode.sound.SoundSystem;

/** Keeps native audio sources in step with replay pause and the rendered camera. */
public final class ReplaySoundPlayback {
    private static final Map<String,ISound> paused = new HashMap<>();
    private static final Set<ISound> stopped = Collections.newSetFromMap(new IdentityHashMap<>());
    private ReplaySoundPlayback() { }
    public static void frame(SoundSystem system,Map<String,ISound> sounds,double x,double y,double z,float partialTicks) {
        if (ExportAudio.active()) return;
        boolean active=ReplayVehicleClock.active();
        boolean frozen=active && ReplayModReplay.instance.getReplayHandler().getReplaySender().paused();
        frame(system,sounds,x,y,z,partialTicks,active,frozen);
    }
    static void frame(SoundSystem system,Map<String,ISound> sounds,double x,double y,double z,float partialTicks,boolean active,boolean frozen) {
        paused.keySet().retainAll(sounds.keySet());
        stopped.retainAll(sounds.values());
        for (Map.Entry<String,ISound> entry:sounds.entrySet()) {
            String channel=entry.getKey(); ISound sound=entry.getValue();
            if (stopped.contains(sound)) { paused.remove(channel); continue; }
            boolean world=sound.getAttenuationType()!=ISound.AttenuationType.NONE;
            // UI clicks remain usable while the scene is paused.
            if (active && frozen && world) { if (paused.putIfAbsent(channel,sound)==null) system.pause(channel); }
            else if (paused.remove(channel)!=null) system.play(channel);
            VehicleSound vehicle=active?VehicleSound.get(sound):null;
            if (vehicle!=null) {
                double[] source=vehicle.position(x,y,z,partialTicks);
                system.setPosition(channel,(float)source[0],(float)source[1],(float)source[2]);
            }
        }
        if (active) VehicleSound.retain(sounds.values()); else { VehicleSound.clear(); stopped.clear(); }
    }
    public static boolean priority(boolean original,ISound sound) {
        return original || ReplayVehicleClock.active() && sound!=null && sound.canRepeat() && "mcheli".equals(sound.getSoundLocation().getResourceDomain());
    }
    public static void started(ISound sound) { stopped.remove(sound); }
    public static void stop(ISound sound) {
        VehicleSound.stop(sound); paused.values().removeIf(value->value==sound);
        if (ReplayVehicleClock.active() && !ExportAudio.active()) stopped.add(sound);
    }
    public static boolean mayResume(ISound sound) {
        if (sound==null) return false;
        if (!ReplayVehicleClock.active()) return true;
        if (stopped.contains(sound)) return false;
        return sound.getAttenuationType()==ISound.AttenuationType.NONE
                || !ReplayModReplay.instance.getReplayHandler().getReplaySender().paused();
    }
    public static void clear() { paused.clear(); stopped.clear(); VehicleSound.clear(); }
    /** Minecraft resumes sources when GUI focus changes; reapply replay pause next frame. */
    public static void resumed() { paused.clear(); }
}
