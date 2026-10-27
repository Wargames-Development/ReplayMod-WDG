package com.replaymod.replay.compat;

/** Bounded camera-ray traversal through vertical columns; no world loads or I/O. */
public final class TerrainVisibility {
    public enum Result { CLEAR, BLOCKED, UNKNOWN }
    public interface Columns {
        Result test(int x,int z,double minY,double maxY);
        default boolean exhausted() { return false; }
    }
    private TerrainVisibility() { }
    public static Result trace(Columns columns,double x0,double y0,double z0,double x1,double y1,double z1) {
        double[] coordinates={x0,y0,z0,x1,y1,z1};
        for(double p:coordinates) if(!Double.isFinite(p) || Math.abs(p)>30000000) return Result.UNKNOWN;
        double dx=x1-x0,dy=y1-y0,dz=z1-z0;
        if(dx*dx+dy*dy+dz*dz>32768.0*32768.0) return Result.UNKNOWN;
        int x=(int)Math.floor(x0),z=(int)Math.floor(z0),sx=Double.compare(dx,0),sz=Double.compare(dz,0);
        double t=0; boolean unknown=false;
        for(int step=0;step<8192;step++) {
            if(columns.exhausted()) return Result.UNKNOWN;
            double tx=dx==0?Double.POSITIVE_INFINITY:(x+(sx>0?1.0:0.0)-x0)/dx;
            double tz=dz==0?Double.POSITIVE_INFINITY:(z+(sz>0?1.0:0.0)-z0)/dz;
            double end=Math.min(1,Math.min(tx,tz));
            if(end<t) return Result.UNKNOWN;
            double a=y0+dy*t,b=y0+dy*end;
            Result result=columns.test(x,z,Math.min(a,b),Math.max(a,b));
            if(result==Result.BLOCKED) return result;
            unknown|=result!=Result.CLEAR;
            if(end==1) return unknown?Result.UNKNOWN:Result.CLEAR;
            if(tx==end) x+=sx;
            if(tz==end) z+=sz;
            t=end;
        }
        return Result.UNKNOWN;
    }
    static Result intervals(int[] intervals,double low,double high) {
        if(intervals==null) return Result.UNKNOWN;
        for(int i=0;i<intervals.length;i+=2)
            if(high>=intervals[i] && low<intervals[i+1]) return Result.BLOCKED;
        return Result.CLEAR;
    }
}
