package com.replaymod.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map;

/** Reads MCHR's framed server snapshots without loading a second Minecraft world. */
public final class RecordedVehicleTrack implements AutomatedShot.Target {
    private final NavigableMap<Integer,AutomatedShot.Pose> poses=new TreeMap<>();
    private final java.util.NavigableSet<Integer> missing=new java.util.TreeSet<>();
    private RecordedVehicleTrack() { }
    @Override public AutomatedShot.Pose at(int time) {
        Map.Entry<Integer,AutomatedShot.Pose> before=poses.floorEntry(time),after=poses.ceilingEntry(time);
        if(before==null && after!=null && after.getKey()-time<=150) before=after;
        if(after==null && before!=null && time-before.getKey()<=150) after=before;
        if(before==null || after==null || after.getKey()-before.getKey()>500)
            throw new IllegalArgumentException("Vehicle tracking has no continuous data at "+StudioTime.format(time)+". Choose another replay range or use a fixed target.");
        Integer lastMissing=missing.floor(time);
        if(lastMissing!=null && lastMissing>=before.getKey()) throw new IllegalArgumentException("The vehicle is missing or destroyed near "+StudioTime.format(time)+".");
        Integer absent=missing.ceiling(before.getKey());
        if(absent!=null && absent<=after.getKey())
            throw new IllegalArgumentException("The vehicle is missing or destroyed near "+StudioTime.format(time)+".");
        AutomatedShot.Pose a=before.getValue(),b=after.getValue();
        double t=before.getKey().equals(after.getKey())?0:(time-before.getKey())/(double)(after.getKey()-before.getKey());
        if (before.getKey().equals(after.getKey())) return a;
        Map.Entry<Integer,AutomatedShot.Pose> previous=poses.lowerEntry(before.getKey()),next=poses.higherEntry(after.getKey());
        if (previous!=null && (before.getKey()-previous.getKey()>500 || missingBetween(previous.getKey(),before.getKey()))) previous=null;
        if (next!=null && (next.getKey()-after.getKey()>500 || missingBetween(after.getKey(),next.getKey()))) next=null;
        double h=after.getKey()-before.getKey(),h0=previous==null?h:before.getKey()-previous.getKey(),h1=next==null?h:next.getKey()-after.getKey();
        AutomatedShot.Pose p=previous==null?a:previous.getValue(),n=next==null?b:next.getValue();
        return new AutomatedShot.Pose(
            smooth(p.x,a.x,b.x,n.x,h0,h,h1,t,previous!=null,next!=null),
            smooth(p.y,a.y,b.y,n.y,h0,h,h1,t,previous!=null,next!=null),
            smooth(p.z,a.z,b.z,n.z,h0,h,h1,t,previous!=null,next!=null),
            angle(p.yaw,a.yaw,b.yaw,n.yaw,h0,h,h1,t,previous!=null,next!=null),
            angle(p.pitch,a.pitch,b.pitch,n.pitch,h0,h,h1,t,previous!=null,next!=null),
            angle(p.roll,a.roll,b.roll,n.roll,h0,h,h1,t,previous!=null,next!=null));
    }
    private static float angle(float p,float a,float b,float n,double h0,double h,double h1,double t,boolean previous,boolean next) {
        double end=a+AutomatedShot.wrap(b-a);
        return (float)smooth(a-AutomatedShot.wrap(a-p),a,end,end+AutomatedShot.wrap(n-b),h0,h,h1,t,previous,next);
    }
    private boolean missingBetween(int from,int to) { Integer absent=missing.ceiling(from); return absent!=null && absent<=to; }
    /** Monotone Hermite tangents keep velocity continuous without overshooting turns. */
    static double smooth(double p,double a,double b,double n,double h0,double h,double h1,double t,boolean previous,boolean next) {
        return com.replaymod.replay.compat.ReplayInterpolation.smooth(p,a,b,n,h0,h,h1,t,previous,next);
    }
    @Override public void validateRange(int from,int to) {
        at(from); at(to);
        Integer absent=missing.ceiling(from);
        if(absent!=null && absent<=to) throw new IllegalArgumentException("The vehicle is missing or destroyed inside this replay range. Shorten the shot or use a fixed target.");
        Integer previous=poses.floorKey(from); if(previous==null) previous=poses.ceilingKey(from);
        for(Integer time:poses.subMap(from,false,to,true).keySet()) {
            if(time-previous>500) throw new IllegalArgumentException("Vehicle tracking has a gap inside this replay range. Shorten the shot or use a fixed target.");
            previous=time;
        }
    }
    public int size() { return poses.size(); }
    public static RecordedVehicleTrack read(InputStream stream,int entityId) throws IOException {
        RecordedVehicleTrack track=new RecordedVehicleTrack();
        com.replaymod.replay.compat.RecordedSnapshotTimeline timeline=new com.replaymod.replay.compat.RecordedSnapshotTimeline();
        DataInputStream input=new DataInputStream(new BufferedInputStream(stream));
        while(true) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Vehicle tracking cancelled.");
            int high=input.read(); if(high<0) break;
            int time=(high<<24)|(input.readUnsignedByte()<<16)|(input.readUnsignedByte()<<8)|input.readUnsignedByte();
            int length=input.readInt();
            if(time<0 || length<1 || length>16*1024*1024) throw new IOException("Invalid replay packet framing.");
            int id=varInt(input),remaining=length-varIntSize(id);
            if(remaining<0) throw new IOException("Truncated replay packet.");
            if(id!=0x3F) { skip(input,remaining); continue; }
            byte[] data=new byte[remaining]; input.readFully(data);
            DataInputStream packet=new DataInputStream(new ByteArrayInputStream(data));
            String channel=text(packet,256); if(!"MCHReforged".equals(channel)) continue;
            int payload=packet.readUnsignedShort(); if(payload!=packet.available() || payload<13 || packet.readUnsignedByte()!=7) continue;
            int batch=timeline.add(time,packet.readLong()); int count=packet.readInt();
            if(count<0 || count>packet.available()/80) throw new IOException("Invalid MCHR snapshot count.");
            boolean found=false;
            for(int i=0;i<count;i++) {
                int entity=packet.readInt(); text(packet,32767); text(packet,32767); String type=text(packet,32767);
                double x=packet.readDouble(),y=packet.readDouble(),z=packet.readDouble();
                packet.readDouble(); packet.readDouble(); packet.readDouble();
                float yaw=packet.readFloat(),pitch=packet.readFloat(); packet.readByte(); packet.readLong();
                float roll=packet.readFloat(); boolean destroyed=packet.readBoolean(); packet.readFloat(); packet.readFloat();
                if(entity==entityId && type.startsWith("mcheli.") && !destroyed && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(yaw) && Float.isFinite(pitch) && Float.isFinite(roll)) {
                    track.poses.put(batch,new AutomatedShot.Pose(x,y,z,yaw,pitch,roll)); found=true;
                }
            }
            if(!found) track.missing.add(batch); else track.missing.remove(batch);
            if(track.poses.size()>50000 || track.missing.size()>100000) throw new IOException("This recording is too large for automatic vehicle tracking. Split it into a shorter recording.");
        }
        if(track.poses.isEmpty()) throw new IOException("No MCHR vehicle snapshots were found for entity "+entityId+". Pick a recorded vehicle or use a fixed target.");
        int[] times=timeline.times();
        NavigableMap<Integer,AutomatedShot.Pose> timed=new TreeMap<>();
        for(Map.Entry<Integer,AutomatedShot.Pose> sample:track.poses.entrySet()) timed.put(times[sample.getKey()],sample.getValue());
        java.util.NavigableSet<Integer> absent=new java.util.TreeSet<>();
        for(Integer batch:track.missing) absent.add(times[batch]);
        track.poses.clear(); track.poses.putAll(timed);
        track.missing.clear(); track.missing.addAll(absent);
        return track;
    }
    private static int varInt(DataInputStream input) throws IOException {
        int value=0; for(int i=0;i<5;i++) { int b=input.readUnsignedByte(); value|=(b&127)<<(i*7); if((b&128)==0) return value; }
        throw new IOException("Invalid replay VarInt.");
    }
    private static int varIntSize(int value) { int size=1; while((value&~127)!=0) { size++; value>>>=7; } return size; }
    private static void skip(DataInputStream input,int bytes) throws IOException { while(bytes>0) { int skipped=input.skipBytes(bytes); if(skipped==0) { input.readByte(); skipped=1; } bytes-=skipped; } }
    private static String text(DataInputStream input,int maximum) throws IOException {
        int length=varInt(input); if(length<0 || length>maximum || length>input.available()) throw new IOException("Invalid replay string length.");
        byte[] data=new byte[length]; input.readFully(data); return new String(data,StandardCharsets.UTF_8);
    }
}
