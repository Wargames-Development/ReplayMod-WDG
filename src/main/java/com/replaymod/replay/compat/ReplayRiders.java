package com.replaymod.replay.compat;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/** Render-scoped seat positions derived from the exact body pose, never from player packets. */
public final class ReplayRiders {
    private static boolean drawing;
    private static final ClassValue<AircraftAccess> access=new ClassValue<AircraftAccess>() {
        @Override protected AircraftAccess computeValue(Class<?> type) {
            try { return new AircraftAccess(type); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("MCHR seat render API is unavailable",e); }
        }
    };
    private ReplayRiders() { }
    public static boolean drawing() { return drawing; }
    public static boolean unified(Entity aircraft) {
        try { return (Boolean)access.get(aircraft.getClass()).unified.invoke(null,aircraft); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Could not check MCHR renderer",e); }
    }
    public static void render(Entity aircraft,float partial,double[] pose) {
        AircraftAccess api=access.get(aircraft.getClass());
        List<SavedPosition> saved=new ArrayList<>();
        boolean previous=drawing; drawing=true;
        float brightnessX=OpenGlHelper.lastBrightnessX,brightnessY=OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            if (!api.enabled.getBoolean(api.renderEntities.get(null))) return;
            Object info=api.info.invoke(aircraft);
            if (info==null) return;
            // renderContact has already translated and compressed the body. Restore
            // the coordinate origin expected by MCHR's ordinary rider renderer.
            GL11.glTranslated(RenderManager.renderPosX-pose[0],RenderManager.renderPosY-pose[1],RenderManager.renderPosZ-pose[2]);
            attach(api,aircraft,aircraft.riddenByEntity,pose,partial,true,saved);
            for (Object seat:(Object[])api.seats.invoke(aircraft)) if (seat instanceof Entity)
                attach(api,aircraft,((Entity)seat).riddenByEntity,pose,partial,false,saved);
            api.render.invoke(RenderManager.instance.getEntityRenderObject(aircraft),aircraft,partial,(float)pose[3],
                (float)pose[4]+api.entityPitch.getFloat(info),(float)pose[5]+api.entityRoll.getFloat(info),
                api.entityWidth.getFloat(info),api.entityHeight.getFloat(info));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Could not render MCHR aircraft seats",e); }
        finally {
            for (SavedPosition p:saved) p.restore(); drawing=previous;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,brightnessX,brightnessY);
            GL11.glPopMatrix(); GL11.glPopAttrib();
        }
    }
    private static void attach(AircraftAccess api,Entity aircraft,Entity rider,double[] pose,float partial,boolean pilot,List<SavedPosition> saved) throws ReflectiveOperationException {
        if (rider==null || rider.isDead) return;
        Object seat=api.seat.invoke(aircraft,rider); if (seat==null) return;
        Vec3 local=(Vec3)api.seatPosition.get(seat);
        double offset=pilot?rider.yOffset-0.5: rider.height>=1?1-rider.height:0;
        // Use MCHR's seat/turret rules, then rebase from its tick attitude to the
        // smooth body attitude. No flight state or mount relationship is modified.
        Vec3 nativeOffset=(Vec3)api.transform.invoke(aircraft,local.xCoord,local.yCoord+offset,local.zCoord,0d,0d,0d,api.rotSeat.getBoolean(seat));
        float yaw=((Number)api.yaw.invoke(aircraft)).floatValue(),pitch=((Number)api.pitch.invoke(aircraft)).floatValue(),roll=((Number)api.roll.invoke(aircraft)).floatValue();
        double[] localOffset=inverse(nativeOffset.xCoord,nativeOffset.yCoord,nativeOffset.zCoord,yaw,pitch,roll);
        double[] world=seatPosition(pose,localOffset[0],localOffset[1],localOffset[2]);
        saved.add(new SavedPosition(rider));
        rider.lastTickPosX=rider.prevPosX=rider.posX=world[0];
        rider.lastTickPosY=rider.prevPosY=rider.posY=world[1];
        rider.lastTickPosZ=rider.prevPosZ=rider.posZ=world[2];
    }
    public static double[] seatPosition(double[] pose,double x,double y,double z) {
        double[] offset=rotate(x,y,z,pose[3],pose[4],pose[5]);
        return new double[]{pose[0]+offset[0],pose[1]+offset[1],pose[2]+offset[2]};
    }
    // Same Ry(-yaw) Rx(pitch) Rz(roll) order as the MCHR aircraft model.
    private static double[] rotate(double x,double y,double z,double yaw,double pitch,double roll) {
        double r=Math.toRadians(roll),p=Math.toRadians(pitch),a=Math.toRadians(-yaw);
        double rx=x*Math.cos(r)-y*Math.sin(r),ry=x*Math.sin(r)+y*Math.cos(r);
        double py=ry*Math.cos(p)-z*Math.sin(p),pz=ry*Math.sin(p)+z*Math.cos(p);
        return new double[]{rx*Math.cos(a)+pz*Math.sin(a),py,-rx*Math.sin(a)+pz*Math.cos(a)};
    }
    private static double[] inverse(double x,double y,double z,double yaw,double pitch,double roll) {
        double a=Math.toRadians(-yaw),p=Math.toRadians(pitch),r=Math.toRadians(roll);
        double rx=x*Math.cos(a)-z*Math.sin(a),rz=x*Math.sin(a)+z*Math.cos(a);
        double ry=y*Math.cos(p)+rz*Math.sin(p),pz=-y*Math.sin(p)+rz*Math.cos(p);
        return new double[]{rx*Math.cos(r)+ry*Math.sin(r),-rx*Math.sin(r)+ry*Math.cos(r),pz};
    }
    private static final class AircraftAccess {
        final Method unified,info,seats,seat,transform,render,yaw,pitch,roll;
        final Field seatPosition,rotSeat,entityPitch,entityRoll,entityWidth,entityHeight,renderEntities,enabled;
        AircraftAccess(Class<?> type) throws ReflectiveOperationException {
            Class<?> base=Class.forName("mcheli.aircraft.MCH_EntityAircraft"),renderer=Class.forName("mcheli.aircraft.MCH_RenderAircraft");
            unified=Class.forName("mcheli.render.MCH_RenderFarVehicle").getMethod("usesUnifiedRender",base);
            info=type.getMethod("getAcInfo"); seats=type.getMethod("getSeats"); seat=type.getMethod("getSeatInfo",Entity.class);
            transform=type.getMethod("getTransformedPosition",double.class,double.class,double.class,double.class,double.class,double.class,boolean.class);
            yaw=type.getMethod("getRotYaw"); pitch=type.getMethod("getRotPitch"); roll=type.getMethod("getRotRoll");
            render=renderer.getMethod("renderRiddenEntity",base,float.class,float.class,float.class,float.class,float.class,float.class);
            Class<?> seatType=seat.getReturnType(),infoType=info.getReturnType();
            seatPosition=seatType.getField("pos"); rotSeat=seatType.getField("rotSeat");
            entityPitch=infoType.getField("entityPitch"); entityRoll=infoType.getField("entityRoll");
            entityWidth=infoType.getField("entityWidth"); entityHeight=infoType.getField("entityHeight");
            renderEntities=Class.forName("mcheli.MCH_Config").getField("EnableModEntityRender");
            enabled=renderEntities.getType().getField("prmBool");
        }
    }
    private static final class SavedPosition {
        final Entity entity; final double[] values;
        SavedPosition(Entity e) { entity=e; values=new double[]{e.posX,e.posY,e.posZ,e.prevPosX,e.prevPosY,e.prevPosZ,e.lastTickPosX,e.lastTickPosY,e.lastTickPosZ}; }
        void restore() { Entity e=entity; e.posX=values[0];e.posY=values[1];e.posZ=values[2];e.prevPosX=values[3];e.prevPosY=values[4];e.prevPosZ=values[5];e.lastTickPosX=values[6];e.lastTickPosY=values[7];e.lastTickPosZ=values[8]; }
    }
}
