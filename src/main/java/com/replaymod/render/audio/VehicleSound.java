package com.replaymod.render.audio;

import com.replaymod.replay.compat.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;

/** MCHR compresses engine-source distance to simulate its configured audible range. */
public final class VehicleSound {
    private static Access access;
    private static boolean warned;
    private static final Map<ISound,VehicleSound> playback = new IdentityHashMap<>();
    private final Entity aircraft;
    private final String type;
    private final double range;
    private final boolean riding;
    private VehicleSound(Entity aircraft,String type,double range,boolean riding) {
        this.aircraft=aircraft; this.type=type; this.range=range; this.riding=riding;
    }
    public static void bind(Object updater) {
        if (!ReplayVehicleClock.active()) return;
        try {
            if (access==null) access=new Access(updater.getClass());
            Entity aircraft=(Entity)access.aircraft.get(updater);
            ISound sound=(ISound)access.sound.get(updater);
            Object info=access.info.invoke(aircraft);
            if (sound==null || info==null) return;
            double range=((Number)access.range.get(info)).doubleValue();
            if (!Double.isFinite(range) || range<=0) return;
            boolean riding=((Number)access.seat.invoke(aircraft,Minecraft.getMinecraft().thePlayer)).intValue()>=0;
            VehicleSound source=new VehicleSound(aircraft,(String)access.type.invoke(aircraft),range,riding);
            if (ExportAudio.active()) ExportAudio.bindVehicle(sound,source);
            else playback.put(sound,source);
        } catch (ReflectiveOperationException | ClassCastException error) {
            if (!warned) { warned=true; com.replaymod.render.ReplayModRender.LOGGER.warn("Could not attach MCHR engine audio; retaining its original sound position",error); }
        }
    }
    double[] position(Entity listener) {
        return position(listener.posX,listener.posY,listener.posZ,1);
    }
    double[] position(double lx,double ly,double lz,float partialTicks) {
        RecordedAircraftFrames.Pose pose=ReplayAircraftExport.pose(aircraft.getUniqueID(),aircraft.getClass().getName(),type);
        double x=aircraft.prevPosX+(aircraft.posX-aircraft.prevPosX)*partialTicks;
        double y=aircraft.prevPosY+(aircraft.posY-aircraft.prevPosY)*partialTicks;
        double z=aircraft.prevPosZ+(aircraft.posZ-aircraft.prevPosZ)*partialTicks;
        return position(pose==null?x:pose.x,pose==null?y:pose.y,pose==null?z:pose.z,lx,ly,lz,range,riding);
    }
    static VehicleSound get(ISound sound) { return playback.get(sound); }
    static void retain(Collection<ISound> sounds) { playback.keySet().retainAll(sounds); }
    static void stop(ISound sound) { playback.remove(sound); }
    static void clear() { playback.clear(); }
    static double[] position(double x,double y,double z,double lx,double ly,double lz,double range,boolean riding) {
        double divisor=riding?1:range/16.0;
        return new double[]{lx+(x-lx)/divisor,ly+(y-ly)/divisor,lz+(z-lz)/divisor};
    }
    private static final class Access {
        final Field aircraft,sound,range;
        final Method info,type,seat;
        Access(Class<?> updater) throws ReflectiveOperationException {
            aircraft=updater.getDeclaredField("theAircraft"); aircraft.setAccessible(true);
            sound=updater.getSuperclass().getDeclaredField("es"); sound.setAccessible(true);
            Class<?> entity=aircraft.getType();
            info=entity.getMethod("getAcInfo"); type=entity.getMethod("getTypeName"); seat=entity.getMethod("getSeatIdByEntity",Entity.class);
            range=info.getReturnType().getField("soundRange");
        }
    }
}
