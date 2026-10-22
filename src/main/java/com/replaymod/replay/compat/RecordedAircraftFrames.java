package com.replaymod.replay.compat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable source-time poses for offline rendering, independent of client ticks and frame cost. */
public final class RecordedAircraftFrames {
    private final Map<UUID, NavigableMap<Integer, Pose>> tracks = new HashMap<>();
    public int trackCount() { return tracks.size(); }

    public static final class Pose {
        public final double x, y, z;
        public final float yaw, pitch, roll;
        public final int packedLight;
        final String type, name;
        final boolean destroyed;
        final int batch;
        Pose(double x, double y, double z, float yaw, float pitch, float roll,
                String type, String name, boolean destroyed, int batch) {
            this(x,y,z,yaw,pitch,roll,type,name,destroyed,batch,-1);
        }
        private Pose(double x, double y, double z, float yaw, float pitch, float roll,
                String type, String name, boolean destroyed, int batch, int packedLight) {
            this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch; this.roll=roll;
            this.type=type; this.name=name; this.destroyed=destroyed; this.batch=batch;
            this.packedLight=packedLight;
        }
        private Pose withLight(int light) { return new Pose(x,y,z,yaw,pitch,roll,type,name,destroyed,batch,light); }
        boolean valid() {
            return !destroyed && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch) && Float.isFinite(roll);
        }
    }

    public Pose at(UUID uuid, String type, String name, int time) {
        NavigableMap<Integer, Pose> track = tracks.get(uuid);
        if (track == null) return null;
        Map.Entry<Integer, Pose> lo=track.floorEntry(time), hi=track.ceilingEntry(time);
        if (hi == null && lo != null && time-lo.getKey() <= 150) hi=lo;
        if (lo == null || hi == null) return null;
        Pose a=lo.getValue(), b=hi.getValue();
        if (!a.valid() || !b.valid() || !type.equals(a.type) || !type.equals(b.type)
                || !name.equals(a.name) || !name.equals(b.name)) return null;
        if (lo.getKey().equals(hi.getKey())) return a;
        // Never bridge missing/destroyed aircraft, identity reuse, long outages or teleports.
        if (!continuous(lo,hi)) return null;
        Map.Entry<Integer, Pose> previous=track.lowerEntry(lo.getKey()), next=track.higherEntry(hi.getKey());
        if (!continuous(previous,lo)) previous=null;
        if (!continuous(hi,next)) next=null;
        Pose p=previous==null?a:previous.getValue(), n=next==null?b:next.getValue();
        double h=hi.getKey()-lo.getKey(), h0=previous==null?h:lo.getKey()-previous.getKey(),
                h1=next==null?h:next.getKey()-hi.getKey();
        double t=(time-lo.getKey())/(double)(hi.getKey()-lo.getKey());
        float[] angles=attitude(a, b, t);
        // Auto-shot targets use this same curve. Linear aircraft motion against
        // a smooth tracking camera otherwise makes the subject bob at each packet.
        return new Pose(
            ReplayInterpolation.smooth(p.x,a.x,b.x,n.x,h0,h,h1,t,previous!=null,next!=null),
            ReplayInterpolation.smooth(p.y,a.y,b.y,n.y,h0,h,h1,t,previous!=null,next!=null),
            ReplayInterpolation.smooth(p.z,a.z,b.z,n.z,h0,h,h1,t,previous!=null,next!=null),
            angles[0], angles[1], angles[2], type, name, false, a.batch, a.packedLight);
    }

    private static boolean continuous(Map.Entry<Integer, Pose> from, Map.Entry<Integer, Pose> to) {
        if (from==null || to==null || to.getKey()-from.getKey()>500) return false;
        Pose a=from.getValue(), b=to.getValue();
        if (!a.valid() || !b.valid() || b.batch!=a.batch+1
                || !a.type.equals(b.type) || !a.name.equals(b.name)) return false;
        double dx=b.x-a.x, dy=b.y-a.y, dz=b.z-a.z;
        return dx*dx+dy*dy+dz*dz<=64*64;
    }

    // Match MCHR's actual GL order: Ry(-yaw) Rx(pitch) Rz(roll). SLERP avoids Euler
    // inversions at vertical pitch and +/-180 wraps without damping real manoeuvres.
    private static double[] quaternion(Pose p) {
        double y=Math.toRadians(-p.yaw)/2, x=Math.toRadians(p.pitch)/2, z=Math.toRadians(p.roll)/2;
        double cy=Math.cos(y), sy=Math.sin(y), cx=Math.cos(x), sx=Math.sin(x), cz=Math.cos(z), sz=Math.sin(z);
        return new double[]{cy*cx*cz+sy*sx*sz, cy*sx*cz+sy*cx*sz,
            sy*cx*cz-cy*sx*sz, cy*cx*sz-sy*sx*cz};
    }
    private static float[] attitude(Pose from, Pose to, double t) {
        double[] a=quaternion(from), b=quaternion(to);
        double dot=0; for (int i=0;i<4;i++) dot+=a[i]*b[i];
        if (dot<0) { dot=-dot; for (int i=0;i<4;i++) b[i]=-b[i]; }
        double wa=1-t, wb=t;
        if (dot<0.9995) {
            double angle=Math.acos(Math.min(1,dot)), sin=Math.sin(angle);
            wa=Math.sin((1-t)*angle)/sin; wb=Math.sin(t*angle)/sin;
        }
        double length=0;
        for (int i=0;i<4;i++) { a[i]=a[i]*wa+b[i]*wb; length+=a[i]*a[i]; }
        length=Math.sqrt(length); for (int i=0;i<4;i++) a[i]/=length;
        double w=a[0], x=a[1], y=a[2], z=a[3];
        double pitch=Math.asin(Math.max(-1,Math.min(1,2*(w*x-y*z))));
        double yaw, roll;
        if (Math.abs(Math.cos(pitch))>1e-7) {
            yaw=-Math.atan2(2*(x*z+w*y),1-2*(x*x+y*y));
            roll=Math.atan2(2*(x*y+w*z),1-2*(x*x+z*z));
        } else {
            yaw=-Math.atan2(2*(w*y-x*z),1-2*(y*y+z*z)); roll=0;
        }
        return new float[]{(float)Math.toDegrees(yaw),(float)Math.toDegrees(pitch),(float)Math.toDegrees(roll)};
    }

    public static RecordedAircraftFrames read(InputStream stream) throws IOException {
        RecordedAircraftFrames result=new RecordedAircraftFrames();
        DataInputStream input=new DataInputStream(new BufferedInputStream(stream));
        RecordedSnapshotTimeline timeline=new RecordedSnapshotTimeline();
        int samples=0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Aircraft pose scan cancelled.");
            int high=input.read(); if (high<0) break;
            int time=(high<<24)|(input.readUnsignedByte()<<16)|(input.readUnsignedByte()<<8)|input.readUnsignedByte();
            int length=input.readInt();
            if (time<0 || length<1 || length>16*1024*1024) throw new IOException("Invalid aircraft replay packet framing.");
            // Packet id is one byte for the 1.7.10 clientbound protocol.
            if (input.readUnsignedByte()!=0x3f) { skip(input,length-1); continue; }
            byte[] bytes=new byte[length-1]; input.readFully(bytes);
            DataInputStream p=new DataInputStream(new ByteArrayInputStream(bytes));
            if (!"MCHReforged".equals(text(p))) continue;
            int payload=p.readUnsignedShort();
            if (payload!=p.available() || payload<13 || p.readUnsignedByte()!=7) continue;
            int batch=timeline.add(time,p.readLong()); int count=p.readInt();
            if (count<0 || count>p.available()/80) throw new IOException("Invalid MCHR aircraft snapshot count.");
            Pose[] poses=new Pose[count];
            for (int i=0;i<count;i++) {
                p.readInt(); text(p); String name=text(p), type=text(p);
                double x=p.readDouble(), y=p.readDouble(), z=p.readDouble(); skip(p,24);
                float yaw=p.readFloat(), pitch=p.readFloat(); skip(p,9);
                float roll=p.readFloat(); boolean destroyed=p.readBoolean(); skip(p,8);
                if (type.equals("mcheli.plane.MCP_EntityPlane") || type.equals("mcheli.helicopter.MCH_EntityHeli"))
                    poses[i]=new Pose(x,y,z,yaw,pitch,roll,type,name,destroyed,batch);
            }
            // Optional, ordered MCHR extensions. Identity is required: entity ids can be reused.
            while (p.available()>=8) {
                int magic=p.readInt(), n=p.readInt();
                if (magic!=0x41474c31 && magic!=0x5445414d && magic!=0x4c495431 && magic!=0x56494431) break;
                if (n!=count) throw new IOException("Invalid MCHR aircraft extension count.");
                if (magic==0x41474c31) skip(p,n*8); // AGL1
                else if (magic==0x5445414d) { for (int i=0;i<n;i++) text(p); } // TEAM
                else if (magic==0x4c495431) { // LIT1: authoritative light, including real darkness.
                    for (int i=0;i<n;i++) {
                        int light=p.readInt();
                        if (poses[i]!=null) poses[i]=poses[i].withLight(light);
                    }
                }
                else if (magic==0x56494431) { // VID1
                    for (int i=0;i<n;i++) {
                        long most=p.readLong(), least=p.readLong();
                        if (poses[i]!=null && (most!=0 || least!=0)) {
                            UUID uuid=new UUID(most,least);
                            result.tracks.computeIfAbsent(uuid,k -> new TreeMap<>()).put(batch,poses[i]);
                            if (++samples>500000) throw new IOException("Too many aircraft samples for one export. Split the recording.");
                        }
                    }
                    break; // Later radar/armour extensions do not affect the body pose.
                } else break; // Legacy/unknown layouts retain native rendering.
            }
        }
        int[] times=timeline.times();
        for (Map.Entry<UUID, NavigableMap<Integer, Pose>> track:result.tracks.entrySet()) {
            NavigableMap<Integer, Pose> timed=new TreeMap<>();
            for (Map.Entry<Integer, Pose> sample:track.getValue().entrySet()) timed.put(times[sample.getKey()],sample.getValue());
            track.setValue(timed);
        }
        return result;
    }
    private static void skip(DataInputStream input,int bytes) throws IOException {
        while (bytes>0) { int n=input.skipBytes(bytes); if (n==0) { input.readByte(); n=1; } bytes-=n; }
    }
    private static String text(DataInputStream input) throws IOException {
        int length=0;
        for (int i=0;i<5;i++) {
            int b=input.readUnsignedByte(); length|=(b&127)<<(i*7);
            if ((b&128)==0) {
                if (length<0 || length>32767 || length>input.available()) throw new IOException("Invalid MCHR aircraft string.");
                byte[] bytes=new byte[length]; input.readFully(bytes); return new String(bytes,StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Invalid MCHR aircraft string length.");
    }
}
