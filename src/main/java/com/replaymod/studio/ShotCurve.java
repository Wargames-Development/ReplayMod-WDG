package com.replaymod.studio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sparse camera adjustments over normalized video time, independent of source replay time. */
public final class ShotCurve {
    public static final int MAX_POINTS=16;
    private static final double MIN_GAP=.01;
    public static final class Point {
        public final double t,x,y,z;
        public Point(double t,double x,double y,double z) { this.t=t; this.x=x; this.y=y; this.z=z; }
    }
    public final List<Point> points;
    public ShotCurve(List<Point> points) {
        this(points,MIN_GAP);
    }
    private ShotCurve(List<Point> points,double minGap) {
        if(points==null || points.size()<2 || points.size()>MAX_POINTS)
            throw new IllegalArgumentException("Use between 2 and 16 path points.");
        double previous=-1;
        for(Point p:points) {
            if(p==null || !Double.isFinite(p.t) || p.t<0 || p.t>1 || p.t-previous<minGap-1e-9
                    || !finite(p.x) || !finite(p.y) || !finite(p.z))
                throw new IllegalArgumentException("Path points must be finite, ordered and at least 1% apart.");
            previous=p.t;
        }
        if(points.get(0).t!=0 || points.get(points.size()-1).t!=1)
            throw new IllegalArgumentException("Keep path points at the start and end of the shot.");
        this.points=Collections.unmodifiableList(new ArrayList<>(points));
    }
    ShotCurve onClock(long duration) {
        List<Point> result=new ArrayList<>();
        for(Point p:points) result.add(new Point(Math.round(p.t*duration)/(double)duration,p.x,p.y,p.z));
        return new ShotCurve(result,1.0/duration);
    }
    private static boolean finite(double value) { return Double.isFinite(value) && Math.abs(value)<=1000000; }
    public static ShotCurve flat() {
        List<Point> points=new ArrayList<>();
        for(int i=0;i<5;i++) points.add(new Point(i/4.0,0,0,0));
        return new ShotCurve(points);
    }
    public ShotCurve move(int index,double x,double y,double z) {
        List<Point> result=new ArrayList<>(points);
        result.set(index,new Point(points.get(index).t,x,y,z)); return new ShotCurve(result);
    }
    public boolean canAddAfter(int index) {
        return points.size()<MAX_POINTS && index>=0 && index<points.size()-1
                && points.get(index+1).t-points.get(index).t>=2*MIN_GAP-1e-9;
    }
    public ShotCurve addAfter(int index) {
        if(!canAddAfter(index)) throw new IllegalArgumentException("Select a wider interval before the end point, or remove a point first (maximum 16).");
        double t=(points.get(index).t+points.get(index+1).t)/2;
        List<Point> result=new ArrayList<>(points); result.add(index+1,at(t)); return new ShotCurve(result);
    }
    public ShotCurve remove(int index) {
        if(index<=0 || index>=points.size()-1) throw new IllegalArgumentException("Keep the start and end points; drag them to move them.");
        List<Point> result=new ArrayList<>(points); result.remove(index); return new ShotCurve(result);
    }
    public Point at(double t) {
        if(!Double.isFinite(t)) throw new IllegalArgumentException("Choose a finite point time.");
        if(t<=0) return points.get(0);
        if(t>=1) return points.get(points.size()-1);
        int i=0; while(points.get(i+1).t<t) i++;
        Point a=points.get(i),b=points.get(i+1),before=points.get(Math.max(0,i-1)),after=points.get(Math.min(points.size()-1,i+2));
        double span=b.t-a.t,u=(t-a.t)/span;
        return new Point(t,component(a.x,b.x,before.x,after.x,a,b,before,after,span,u),
                component(a.y,b.y,before.y,after.y,a,b,before,after,span,u),
                component(a.z,b.z,before.z,after.z,a,b,before,after,span,u));
    }
    private static double component(double a,double b,double before,double after,Point pa,Point pb,Point pBefore,Point pAfter,double span,double u) {
        // Nonuniform Hermite form of Catmull-Rom: one shared tangent at each point.
        double m0=(b-before)/(pb.t-pBefore.t),m1=(after-a)/(pAfter.t-pa.t),u2=u*u,u3=u2*u;
        return (2*u3-3*u2+1)*a+(u3-2*u2+u)*span*m0+(-2*u3+3*u2)*b+(u3-u2)*span*m1;
    }
}
