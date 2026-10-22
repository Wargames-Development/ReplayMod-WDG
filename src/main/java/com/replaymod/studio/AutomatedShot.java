package com.replaymod.studio;

import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.PathSegment;
import com.replaymod.simplepathing.InterpolatorType;
import com.replaymod.simplepathing.SPTimeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Camera generation is pure; only an explicit placement changes the project. */
public final class AutomatedShot {
    public enum Kind { ORBIT, FOLLOW, FLY_BY }
    public enum Ease { LINEAR, IN_OUT, IN, OUT }
    public interface Target {
        Pose at(int replayMillis);
        default void validateRange(int from,int to) { }
    }
    public static final class Pose {
        public final double x,y,z; public final float yaw;
        public Pose(double x,double y,double z,float yaw) { this.x=x; this.y=y; this.z=z; this.yaw=yaw; }
    }
    public static final class Settings {
        public Kind kind=Kind.ORBIT;
        public Ease ease=Ease.IN_OUT;
        public long outputStart,duration=6000;
        public int replayIn,replayOut,samplesPerSecond=60;
        public double distanceStart=24,distanceEnd=24,heightStart=10,heightEnd=10;
        public double angleStart=180,angleChange=180,focusHeight=2,rollStart,rollEnd;
    }
    public static final class Frame {
        public final long time; public final int replay;
        public final double x,y,z; public final float yaw,pitch,roll; public final Pose target;
        Frame(long time,int replay,double x,double y,double z,float yaw,float pitch,float roll,Pose target) {
            this.time=time; this.replay=replay; this.x=x; this.y=y; this.z=z;
            this.yaw=yaw; this.pitch=pitch; this.roll=roll; this.target=target;
        }
    }
    public static final class Plan {
        public final Kind kind; public final List<Frame> frames;
        private Plan(Kind kind,List<Frame> frames) { this.kind=kind; this.frames=Collections.unmodifiableList(frames); }
        public long start() { return frames.get(0).time; }
        public long end() { return frames.get(frames.size()-1).time; }
        public double speed() { return (frames.get(frames.size()-1).replay-(double)frames.get(0).replay)/(end()-start()); }
    }
    private AutomatedShot() { }
    public static Plan generate(Settings s,Target target,int recordingDuration) {
        if(s.kind==null || s.ease==null || target==null) throw new IllegalArgumentException("Choose a shot type and target.");
        if(s.outputStart<0 || s.duration<100 || s.duration>300000 || s.outputStart>Integer.MAX_VALUE-s.duration)
            throw new IllegalArgumentException("Choose a video duration from 0.1 seconds to 5 minutes, inside the video clock.");
        if(s.replayIn<0 || s.replayOut<s.replayIn || s.replayOut>recordingDuration)
            throw new IllegalArgumentException("Replay Out must be at or after In, inside the recording. Equal times freeze action.");
        double[] values={s.distanceStart,s.distanceEnd,s.heightStart,s.heightEnd,s.angleStart,s.angleChange,s.focusHeight,s.rollStart,s.rollEnd};
        for(double value:values) if(!Double.isFinite(value) || Math.abs(value)>1000000) throw new IllegalArgumentException("Use finite camera values within one million.");
        if(s.kind!=Kind.FLY_BY && (s.distanceStart<0.5 || s.distanceEnd<0.5)) throw new IllegalArgumentException("Camera distance must be at least 0.5 blocks.");
        if(s.kind!=Kind.FLY_BY && Math.abs(s.angleChange)>7200) throw new IllegalArgumentException("Limit one shot to 20 turns. Split longer rotations into several shots.");
        if(s.samplesPerSecond<1 || s.samplesPerSecond>60) throw new IllegalArgumentException("Path detail must be between 1 and 60 samples per second.");
        target.validateRange(s.replayIn,s.replayOut);
        int count=(int)Math.min(600,Math.max(2,Math.ceil(s.duration*s.samplesPerSecond/1000.0)+1));
        List<Frame> frames=new ArrayList<>(count); float lastYaw=0;
        for(int i=0;i<count;i++) {
            double t=i/(double)(count-1),u=ease(t,s.ease);
            long time=s.outputStart+Math.round(t*s.duration);
            int replay=(int)Math.round(s.replayIn+t*(s.replayOut-(double)s.replayIn));
            Pose p=target.at(replay);
            if(p==null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z) || !Float.isFinite(p.yaw))
                throw new IllegalArgumentException("No valid target pose at replay "+StudioTime.format(replay)+".");
            double distance=lerp(s.distanceStart,s.distanceEnd,u),height=lerp(s.heightStart,s.heightEnd,u);
            double angle=Math.toRadians(s.angleStart+s.angleChange*u+(s.kind==Kind.ORBIT?0:p.yaw));
            double x,z;
            if(s.kind==Kind.FLY_BY) {
                // Angles become along-track distances; distance is the sideways offset.
                double along=lerp(s.angleStart,s.angleChange,u),heading=Math.toRadians(p.yaw);
                x=p.x-Math.sin(heading)*along+Math.cos(heading)*distance;
                z=p.z+Math.cos(heading)*along+Math.sin(heading)*distance;
            } else { x=p.x-Math.sin(angle)*distance; z=p.z+Math.cos(angle)*distance; }
            double y=p.y+height,dx=p.x-x,dy=p.y+s.focusHeight-y,dz=p.z-z;
            double horizontal=Math.hypot(dx,dz);
            if(Math.hypot(horizontal,dy)<0.1) throw new IllegalArgumentException("The camera crosses its focus point. Increase distance, side offset or height.");
            float yaw=horizontal<1e-7 ? lastYaw : (float)Math.toDegrees(Math.atan2(-dx,dz));
            if(i>0) yaw=lastYaw+wrap(yaw-lastYaw);
            float pitch=(float)-Math.toDegrees(Math.atan2(dy,horizontal));
            frames.add(new Frame(time,replay,x,y,z,yaw,pitch,(float)lerp(s.rollStart,s.rollEnd,u),p)); lastYaw=yaw;
        }
        return new Plan(s.kind,frames);
    }
    public static double ease(double t,Ease ease) {
        switch(ease) { case IN_OUT:return t*t*(3-2*t); case IN:return t*t; case OUT:return 1-(1-t)*(1-t); default:return t; }
    }
    public static float wrap(float angle) { angle%=360; if(angle>=180) angle-=360; if(angle< -180) angle+=360; return angle; }
    private static double lerp(double from,double to,double t) { return from+(to-from)*t; }
    public static void validatePlacement(SPTimeline timeline,Plan plan) {
        if(timeline.getPositionPath().getKeyframes().isEmpty() && plan.start()!=0) throw new IllegalArgumentException("Start the first automated shot at video 0:00.000.");
        for(Path path:timeline.getTimeline().getPaths()) {
            Keyframe previous=null;
            for(Keyframe key:path.getKeyframes()) {
                if(key.getTime()>=plan.start() && key.getTime()<=plan.end()
                        || previous!=null && previous.getTime()<plan.end() && key.getTime()>plan.start())
                    throw new IllegalArgumentException("That video range is occupied. Use Append or choose an empty range.");
                previous=key;
            }
        }
        Frame first=plan.frames.get(0),last=plan.frames.get(plan.frames.size()-1);
        for(Keyframe key:timeline.getTimePath().getKeyframes()) {
            int source=key.getValue(TimestampProperty.PROPERTY).orElse(0);
            if(key.getTime()<plan.start() && source>first.replay || key.getTime()>plan.end() && source<last.replay)
                throw new IllegalArgumentException("This shot would reverse replay time across a neighbouring shot. Adjust Replay In / Out.");
        }
    }
    public static void place(SPTimeline timeline,Plan plan) {
        validatePlacement(timeline,plan);
        timeline.editAtomically(() -> {
            String group=java.util.UUID.randomUUID().toString(); int index=0;
            for(Frame frame:plan.frames) {
                timeline.addPositionKeyframe(frame.time,frame.x,frame.y,frame.z,frame.yaw,frame.pitch,frame.roll,-1);
                timeline.addTimeKeyframe(frame.time,frame.replay);
                String tag=group+(index==0?":start":index==plan.frames.size()-1?":end":":point"); index++;
                com.replaymod.replaystudio.pathing.change.Change grouping=com.replaymod.replaystudio.pathing.change.UpdateKeyframeProperties
                        .create(timeline.getPositionPath(),timeline.getPositionPath().getKeyframe(frame.time))
                        .setValue(com.replaymod.simplepathing.properties.ShotGroupProperty.PROPERTY,tag).done();
                grouping.apply(timeline.getTimeline()); timeline.recordAppliedChange(grouping);
            }
            // Share one spline across the generated samples so the camera's
            // velocity stays continuous through rapid turns. Time keys remain linear.
            for(PathSegment segment:new ArrayList<>(timeline.getPositionPath().getSegments()))
                if(segment.getStartKeyframe().getTime()>=plan.start() && segment.getEndKeyframe().getTime()<=plan.end())
                    timeline.recordAppliedChange(timeline.setInterpolator(segment.getStartKeyframe().getTime(),InterpolatorType.CATMULL_ROM.newInstance()));
        });
    }
}
