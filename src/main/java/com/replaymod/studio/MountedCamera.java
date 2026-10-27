package com.replaymod.studio;

/** Uses MCHR's body transform: yaw around -Y, pitch around X, then bank around Z. */
final class MountedCamera {
    private MountedCamera() { }
    static double[] rotate(double yaw,double pitch,double roll,double x,double y,double z) {
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch),c=Math.toRadians(roll);
        double rx=Math.cos(c)*x-Math.sin(c)*y,ry=Math.sin(c)*x+Math.cos(c)*y;
        double py=Math.cos(b)*ry-Math.sin(b)*z,pz=Math.sin(b)*ry+Math.cos(b)*z;
        return new double[]{Math.cos(a)*rx-Math.sin(a)*pz,py,Math.sin(a)*rx+Math.cos(a)*pz};
    }
    static double[] inverse(AutomatedShot.Pose p,double x,double y,double z) {
        double a=Math.toRadians(p.yaw),b=Math.toRadians(p.pitch),c=Math.toRadians(p.roll);
        double rx=Math.cos(a)*x+Math.sin(a)*z,rz=-Math.sin(a)*x+Math.cos(a)*z;
        double ry=Math.cos(b)*y+Math.sin(b)*rz,pz=-Math.sin(b)*y+Math.cos(b)*rz;
        return new double[]{Math.cos(c)*rx+Math.sin(c)*ry,-Math.sin(c)*rx+Math.cos(c)*ry,pz};
    }
    static float[] rotation(double yaw,double pitch,double roll,double lookYaw,double lookPitch,double lookRoll,float[] previous) {
        double[] f=rotate(lookYaw,lookPitch,lookRoll,0,0,1),u=rotate(lookYaw,lookPitch,lookRoll,0,1,0);
        f=rotate(yaw,pitch,roll,f[0],f[1],f[2]); u=rotate(yaw,pitch,roll,u[0],u[1],u[2]);
        double horizontal=Math.hypot(f[0],f[2]);
        double y=horizontal<1e-8?(previous==null?yaw:previous[0]):Math.toDegrees(Math.atan2(-f[0],f[2]));
        double p=Math.toDegrees(Math.atan2(-f[1],horizontal));
        double[] right=rotate(y,p,0,1,0,0),up=rotate(y,p,0,0,1,0);
        double r=Math.toDegrees(Math.atan2(-dot(u,right),dot(u,up)));
        float[] result={(float)y,(float)p,(float)r};
        if(previous!=null) {
            // Equivalent Euler representations prevent a 180-degree flip at vertical flight.
            float[] alternate={(float)y+180,180-(float)p,(float)r+180};
            unwrap(result,previous); unwrap(alternate,previous);
            if(distance(alternate,previous)<distance(result,previous)) result=alternate;
        }
        return result;
    }
    private static double dot(double[] a,double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static void unwrap(float[] value,float[] previous) {
        for(int i=0;i<3;i++) value[i]=previous[i]+AutomatedShot.wrap(value[i]-previous[i]);
    }
    private static double distance(float[] a,float[] b) {
        double sum=0; for(int i=0;i<3;i++) sum+=(a[i]-b[i])*(a[i]-b[i]); return sum;
    }
}
