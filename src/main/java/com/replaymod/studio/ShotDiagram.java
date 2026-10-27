package com.replaymod.studio;

/** A frozen orthographic transform keeps point drags stable as the curve changes. */
final class ShotDiagram {
    enum View { TOP, SIDE }
    final View view;
    final int left,top,width,height;
    final double scale,originX,originY;
    private final boolean mounted;
    private final AutomatedShot.Pose anchor;
    ShotDiagram(AutomatedShot.Plan plan,View view,int left,int top,int width,int height) {
        this.view=view; this.left=left; this.top=top; this.width=width; this.height=height;
        mounted=plan.kind==AutomatedShot.Kind.MOUNTED; anchor=plan.frames.get(0).target;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(AutomatedShot.Frame f:plan.frames) {
            minX=Math.min(minX,Math.min(f.x,f.target.x)); maxX=Math.max(maxX,Math.max(f.x,f.target.x));
            minY=Math.min(minY,Math.min(vertical(f.y,f.z),vertical(f.target.y,f.target.z)));
            maxY=Math.max(maxY,Math.max(vertical(f.y,f.z),vertical(f.target.y,f.target.z)));
        }
        scale=Math.min((width-64)/Math.max(1,maxX-minX),(height-64)/Math.max(1,maxY-minY));
        originX=left+width/2.0-(minX+maxX)*scale/2;
        originY=top+height/2.0-(minY+maxY)*scale/2;
    }
    private double vertical(double y,double z) { return view==View.TOP?z:-y; }
    double x(AutomatedShot.Frame f) { return originX+f.x*scale; }
    double y(AutomatedShot.Frame f) { return originY+vertical(f.y,f.z)*scale; }
    double targetX(AutomatedShot.Frame f) { return originX+f.target.x*scale; }
    double targetY(AutomatedShot.Frame f) { return originY+vertical(f.target.y,f.target.z)*scale; }
    int pick(AutomatedShot.Plan plan,int x,int y) {
        return pick(plan,x,y,-1);
    }
    int pick(AutomatedShot.Plan plan,int x,int y,int preferred) {
        int result=-1; double nearest=100;
        for(int i=0;i<plan.handles.size();i++) {
            AutomatedShot.Frame f=plan.handles.get(i);
            double distance=Math.pow(x(f)-x,2)+Math.pow(y(f)-y,2);
            if(distance<=100 && (result<0 || distance<nearest-1e-6 || Math.abs(distance-nearest)<=1e-6 && i==preferred)) { result=i; nearest=distance; }
        }
        return result;
    }
    ShotCurve drag(ShotCurve original,int index,AutomatedShot.Frame handle,AutomatedShot.Space space,double pixelsX,double pixelsY) {
        double dx=pixelsX/scale,dy=view==View.SIDE?-pixelsY/scale:0,dz=view==View.TOP?pixelsY/scale:0;
        double rotation=Math.toRadians(space==AutomatedShot.Space.VEHICLE?handle.target.yaw:0);
        ShotCurve.Point start=original.points.get(index);
        if(mounted) {
            double[] offset=MountedCamera.inverse(space==AutomatedShot.Space.VEHICLE?handle.target:anchor,dx,dy,dz);
            return original.move(index,start.x+offset[0],start.y+offset[1],start.z+offset[2]);
        }
        // Invert the same heading-only transform used during generation.
        return original.move(index,start.x+Math.cos(rotation)*dx+Math.sin(rotation)*dz,
                start.y+dy,start.z-Math.sin(rotation)*dx+Math.cos(rotation)*dz);
    }
}
