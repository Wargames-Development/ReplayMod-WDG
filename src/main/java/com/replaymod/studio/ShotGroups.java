package com.replaymod.studio;

import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.properties.ShotGroupProperty;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import java.util.*;

/** A valid generated shot is one timing range, with an optional detailed sample view. */
public final class ShotGroups {
    private ShotGroups() { }
    public static List<Keyframe> boundaries(SPTimeline timeline) {
        Map<String,long[]> groups=new HashMap<>();
        for(Keyframe key:timeline.getPositionPath().getKeyframes()) {
            String tag=key.getValue(ShotGroupProperty.PROPERTY).orElse(""); int colon=tag.indexOf(':');
            if(colon<1) continue;
            long[] ends=groups.computeIfAbsent(tag.substring(0,colon),unused -> new long[]{-1,-1});
            if(tag.endsWith(":start")) ends[0]=key.getTime();
            if(tag.endsWith(":end")) ends[1]=key.getTime();
        }
        List<Keyframe> result=new ArrayList<>();
        for(Keyframe key:timeline.getPositionPath().getKeyframes()) {
            String tag=key.getValue(ShotGroupProperty.PROPERTY).orElse(""); int colon=tag.indexOf(':');
            long[] ends=colon<1?null:groups.get(tag.substring(0,colon));
            if(ends!=null && ends[0]>=0 && ends[1]>ends[0] && key.getTime()>ends[0] && key.getTime()<ends[1] && tag.endsWith(":point")) continue;
            result.add(key);
        }
        return result;
    }
    public static SortedSet<Long> visibleTimes(SPTimeline timeline,boolean samples) {
        SortedSet<Long> result=new ShotModel(timeline).times();
        if(samples) return result;
        Set<Long> visible=new HashSet<>(); for(Keyframe key:boundaries(timeline)) visible.add(key.getTime());
        for(Keyframe key:timeline.getPositionPath().getKeyframes()) if(!visible.contains(key.getTime())) result.remove(key.getTime());
        return result;
    }
    public static boolean boundary(SPTimeline timeline,long time,String role) {
        Keyframe key=timeline.getPositionPath().getKeyframe(time);
        return key!=null && key.getValue(ShotGroupProperty.PROPERTY).orElse("").endsWith(":"+role);
    }
    public static boolean removeGenerated(SPTimeline timeline,long selected) {
        Keyframe key=timeline.getPositionPath().getKeyframe(selected);
        if(key==null) return false;
        String tag=key.getValue(ShotGroupProperty.PROPERTY).orElse(""); int colon=tag.indexOf(':');
        if(colon<1 || !tag.endsWith(":start") && !tag.endsWith(":end")) return false;
        String group=tag.substring(0,colon)+":";
        List<Long> times=new ArrayList<>(); boolean start=false,end=false;
        for(Keyframe member:timeline.getPositionPath().getKeyframes()) {
            String value=member.getValue(ShotGroupProperty.PROPERTY).orElse("");
            if(!value.startsWith(group)) continue;
            times.add(member.getTime()); start|=value.endsWith(":start"); end|=value.endsWith(":end");
        }
        if(!start || !end) return false;
        Collections.reverse(times);
        timeline.editAtomically(() -> { for(Long time:times) {
            timeline.removePositionKeyframe(time);
            if(timeline.isTimeKeyframe(time)) timeline.removeTimeKeyframe(time);
        } });
        return true;
    }
}
