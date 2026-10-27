package com.replaymod.studio;

import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replay.compat.ReplayVehiclePose;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import java.lang.reflect.Field;
import java.util.*;

/** Current vehicle identities are used for picking; generated movement comes from the recording. */
final class ShotTargets {
    static final class Target {
        final int id; final String name; final AutomatedShot.Pose pose;
        Target(int id,String name,AutomatedShot.Pose pose) { this.id=id; this.name=name; this.pose=pose; }
    }
    static List<Target> collect(Minecraft mc,CameraEntity camera) {
        Map<Integer,Target> result=new HashMap<>();
        try {
            Class<?> tracker=Class.forName("mcheli.MCH_EntityInfoClientTracker");
            for(Object info:(Collection<?>)tracker.getMethod("getAllTrackedEntities").invoke(null)) {
                Class<?> type=info.getClass();
                if(type.getField("destroyed").getBoolean(info)) continue;
                String entityType=(String)type.getField("entityClassName").get(info);
                if(entityType==null || !entityType.startsWith("mcheli.") || !(entityType.contains("Plane") || entityType.contains("Heli") || entityType.contains("Tank") || entityType.contains("Vehicle"))) continue;
                int id=type.getField("entityId").getInt(info);
                result.put(id,new Target(id,(String)type.getField("entityName").get(info),new AutomatedShot.Pose(number(info,"posX"),number(info,"posY"),number(info,"posZ"),(float)number(info,"rotationYaw"),(float)number(info,"rotationPitch"),(float)number(info,"rotationRoll"))));
            }
        } catch(ReflectiveOperationException absent) { /* Ordinary loaded vehicles remain selectable. */ }
        if(mc.theWorld!=null) for(Object value:mc.theWorld.loadedEntityList) {
            if(!(value instanceof Entity) || !(value instanceof ReplayVehiclePose)) continue;
            Entity entity=(Entity)value; if(entity.isDead) continue;
            String name=entity.getCommandSenderName();
            try { name=String.valueOf(entity.getClass().getMethod("getTypeName").invoke(entity)); } catch(ReflectiveOperationException ignored) { }
            float roll=0;
            try { roll=((Number)entity.getClass().getMethod("getRotRoll").invoke(entity)).floatValue(); } catch(ReflectiveOperationException ignored) { }
            result.put(entity.getEntityId(),new Target(entity.getEntityId(),name,new AutomatedShot.Pose(entity.posX,entity.posY,entity.posZ,entity.rotationYaw,entity.rotationPitch,roll)));
        }
        List<Target> targets=new ArrayList<>(result.values());
        targets.sort(Comparator.comparingDouble(t -> distance(t.pose,camera.posX,camera.posY,camera.posZ)));
        return targets;
    }
    static int aimed(List<Target> targets,CameraEntity camera) {
        double yaw=Math.toRadians(camera.rotationYaw),pitch=Math.toRadians(camera.rotationPitch);
        double fx=-Math.sin(yaw)*Math.cos(pitch),fy=-Math.sin(pitch),fz=Math.cos(yaw)*Math.cos(pitch);
        int best=-1; double score=Double.POSITIVE_INFINITY;
        for(int i=0;i<targets.size();i++) {
            AutomatedShot.Pose p=targets.get(i).pose;
            double dx=p.x-camera.posX,dy=p.y+2-camera.posY,dz=p.z-camera.posZ,d=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(d<0.1) continue;
            double dot=(dx*fx+dy*fy+dz*fz)/d;
            if(dot<Math.cos(Math.toRadians(20))) continue;
            double next=(1-dot)*10000+d*.01;
            if(next<score) { score=next; best=i; }
        }
        if(best>=0) return best;
        // At close range the camera may be beside/inside the hull, with the
        // vehicle origin outside the aiming cone. Prefer the nearest hull then.
        double nearest=12*12;
        for(int i=0;i<targets.size();i++) {
            double d=distance(targets.get(i).pose,camera.posX,camera.posY,camera.posZ);
            if(d<=nearest) { nearest=d; best=i; }
        }
        return best;
    }
    private static double distance(AutomatedShot.Pose p,double x,double y,double z) { return (p.x-x)*(p.x-x)+(p.y-y)*(p.y-y)+(p.z-z)*(p.z-z); }
    private static double number(Object object,String name) throws ReflectiveOperationException { Field field=object.getClass().getField(name); return ((Number)field.get(object)).doubleValue(); }
}
