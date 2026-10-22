package com.replaymod.studio;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;

/** A non-destructive shot draft, with background vehicle-track loading and explicit placement. */
public final class StudioAutoShot extends StudioScreen {
    private final CinematicEditor parent;
    private final ReplayHandler replay;
    private AutomatedShot.Kind kind=AutomatedShot.Kind.ORBIT;
    private AutomatedShot.Ease easing=AutomatedShot.Ease.IN_OUT;
    private List<ShotTargets.Target> targets;
    private int target=-1,loadedId=Integer.MIN_VALUE,loadingId=Integer.MIN_VALUE;
    private boolean motion=true;
    private FutureTask<RecordedVehicleTrack> loading;
    private RecordedVehicleTrack track;
    private GuiTextField output,duration,in,out,px,py,pz,d0,d1,h0,h1,a0,a1,focus,r0,r1;
    private AutomatedShot.Plan proposal;
    private String observed="",generated="",draftError="";
    private long changedAt;
    private final long initialOutput;
    public StudioAutoShot(CinematicEditor parent,ReplayHandler replay,long at) {
        this.parent=parent; this.replay=replay; initialOutput=at;
        canvasWidth=1120; canvasHeight=720;
    }
    @Override protected StudioViewport createViewport() { return new StudioViewport(width,height,1120,720,true); }
    @Override protected void build() {
        CameraEntity camera=replay.getCameraEntity();
        if(camera==null) throw new IllegalStateException("Wait for the replay camera to finish loading.");
        if(targets==null) { targets=ShotTargets.collect(mc,camera); target=ShotTargets.aimed(targets,camera); }
        button(1,20,52,108,(kind==AutomatedShot.Kind.ORBIT?"> ":"")+"Orbit");
        button(2,138,52,108,(kind==AutomatedShot.Kind.FOLLOW?"> ":"")+"Follow");
        button(3,256,52,108,(kind==AutomatedShot.Kind.FLY_BY?"> ":"")+"Fly-by");
        button(99,canvasWidth-150,14,130,"Back to editor");
        output=field(20,112,160,text(output,StudioTime.format(initialOutput)));
        duration=field(204,112,160,text(duration,"0:06.000"));
        in=field(20,160,160,text(in,StudioTime.format(replay.getReplaySender().currentTimeStamp())));
        int end=Math.min(replay.getReplayDuration(),replay.getReplaySender().currentTimeStamp()+6000);
        out=field(204,160,160,text(out,StudioTime.format(end)));
        button(4,20,190,108,"Set action 1x"); button(5,138,190,108,"Freeze action"); button(6,256,190,108,"Append");
        button(7,20,232,344,target<0?"Target: fixed point":"Target: "+targets.get(target).name+" (#"+targets.get(target).id+")");
        button(8,20,260,164,"Pick aimed / nearby"); button(9,200,260,164,target<0?"Fixed point":motion?"Target motion: On":"Target motion: Off").enabled=target>=0;
        AutomatedShot.Pose p=target<0?pointAhead(camera):targets.get(target).pose;
        px=field(20,308,106,text(px,num(p.x))); py=field(139,308,106,text(py,num(p.y))); pz=field(258,308,106,text(pz,num(p.z)));
        boolean point=target<0 || !motion; px.setEnabled(point); py.setEnabled(point); pz.setEnabled(point);
        d0=field(20,356,160,text(d0,kind==AutomatedShot.Kind.FLY_BY?"12":"24")); d1=field(204,356,160,text(d1,kind==AutomatedShot.Kind.FLY_BY?"12":"24"));
        h0=field(20,404,160,text(h0,"10")); h1=field(204,404,160,text(h1,"10"));
        a0=field(20,452,160,text(a0,kind==AutomatedShot.Kind.FLY_BY?"-50":"180"));
        a1=field(204,452,160,text(a1,kind==AutomatedShot.Kind.ORBIT?"360":kind==AutomatedShot.Kind.FLY_BY?"50":"180"));
        focus=field(20,500,106,text(focus,"2")); r0=field(139,500,106,text(r0,"0")); r1=field(258,500,106,text(r1,"0"));
        button(10,20,538,344,"Camera easing: "+easeName());
        button(11,20,574,344,"Refresh draft");
        button(12,canvasWidth-374,canvasHeight-62,160,"Place shot");
        button(13,canvasWidth-200,canvasHeight-62,180,"Place & preview");
    }
    private static String text(GuiTextField field,String fallback) { return field==null?fallback:field.getText(); }
    private static String num(double n) { return String.format(Locale.ROOT,"%.3f",n); }
    private static AutomatedShot.Pose pointAhead(CameraEntity c) {
        double yaw=Math.toRadians(c.rotationYaw),pitch=Math.toRadians(c.rotationPitch);
        return new AutomatedShot.Pose(c.posX-Math.sin(yaw)*Math.cos(pitch)*30,c.posY-Math.sin(pitch)*30,c.posZ+Math.cos(yaw)*Math.cos(pitch)*30,0);
    }
    private String easeName() { return new String[]{"Linear","Ease in / out","Ease in","Ease out"}[easing.ordinal()]; }
    private String signature() {
        StringBuilder s=new StringBuilder(kind+"/"+easing+"/"+(target<0?"point":targets.get(target).id)+"/"+motion);
        for(GuiTextField f:fields) s.append('/').append(f.getText()); return s.toString();
    }
    private AutomatedShot.Settings settings() {
        AutomatedShot.Settings s=new AutomatedShot.Settings(); s.kind=kind; s.ease=easing;
        s.outputStart=StudioTime.parse(output.getText()); s.duration=StudioTime.parse(duration.getText());
        long from=StudioTime.parse(in.getText()),to=StudioTime.parse(out.getText());
        if(from>Integer.MAX_VALUE || to>Integer.MAX_VALUE) throw new IllegalArgumentException("Replay time is outside this recording.");
        s.replayIn=(int)from; s.replayOut=(int)to;
        s.distanceStart=StudioTime.number(d0.getText(),"Starting distance"); s.distanceEnd=StudioTime.number(d1.getText(),"Ending distance");
        s.heightStart=StudioTime.number(h0.getText(),"Starting height"); s.heightEnd=StudioTime.number(h1.getText(),"Ending height");
        s.angleStart=StudioTime.number(a0.getText(),"Starting angle / travel"); s.angleChange=StudioTime.number(a1.getText(),"Ending angle / travel");
        if(kind==AutomatedShot.Kind.FOLLOW) s.angleChange-=s.angleStart;
        s.focusHeight=StudioTime.number(focus.getText(),"Focus height"); s.rollStart=StudioTime.number(r0.getText(),"Starting roll"); s.rollEnd=StudioTime.number(r1.getText(),"Ending roll");
        return s;
    }
    private void refreshDraft() {
        proposal=null; draftError="";
        AutomatedShot.Settings settings=settings();
        AutomatedShot.Target targetPose;
        if(target>=0 && motion) {
            int id=targets.get(target).id;
            if(track==null || loadedId!=id) {
                if(loading==null || loadingId!=id) loadTrack(id);
                return;
            }
            targetPose=track;
        } else {
            AutomatedShot.Pose pose=new AutomatedShot.Pose(StudioTime.number(px.getText(),"Target X"),StudioTime.number(py.getText(),"Target Y"),StudioTime.number(pz.getText(),"Target Z"),target<0?0:targets.get(target).pose.yaw);
            targetPose=unused -> pose;
        }
        AutomatedShot.Plan generatedPlan=AutomatedShot.generate(settings,targetPose,replay.getReplayDuration());
        AutomatedShot.validatePlacement(ReplayModSimplePathing.instance.getCurrentTimeline(),generatedPlan);
        proposal=generatedPlan;
        generated=signature();
    }
    private void loadTrack(int id) {
        if(loading!=null) loading.cancel(true);
        loadingId=id;
        loading=new FutureTask<>(() -> {
            com.google.common.base.Optional<InputStream> stream=replay.getReplayFile().get("recording.tmcpr");
            if(!stream.isPresent()) throw new java.io.IOException("This recording has no packet stream.");
            try(InputStream input=stream.get()) { return RecordedVehicleTrack.read(input,id); }
        });
        Thread worker=new Thread(loading,"ReplayMod automated vehicle shot"); worker.setDaemon(true); worker.start();
    }
    private void discardTargetDraft() {
        // An old worker's failure must not block the newly selected target.
        if(loading!=null) { loading.cancel(true); loading=null; }
        proposal=null; observed=""; draftError="";
    }
    private void pollDraft() {
        if(loading!=null && loading.isDone()) {
            try { track=loading.get(); loadedId=loadingId; observed=""; }
            catch(Exception error) { Throwable cause=error.getCause()==null?error:error.getCause(); draftError=cause.getMessage()==null?"Could not read vehicle motion.":cause.getMessage(); }
            loading=null;
        }
        String current=signature();
        if(!current.equals(observed)) { observed=current; changedAt=System.currentTimeMillis(); proposal=null; draftError=""; }
        if(proposal==null && loading==null && draftError.isEmpty() && System.currentTimeMillis()-changedAt>=250) {
            try { refreshDraft(); } catch(IllegalArgumentException | IllegalStateException error) { draftError=error.getMessage(); }
        }
    }
    @Override protected void drawContent(int mouseX,int mouseY,float partialTicks) {
        pollDraft(); drawRect(0,0,canvasWidth,canvasHeight,0xFF0D131A);
        label("AUTOMATED SHOT  /  "+kind.name().replace('_',' '),20,18,TEXT);
        panel(8,86,370,canvasHeight-106,""); panel(394,86,canvasWidth-410,canvasHeight-164,"SHOT DRAFT");
        label("Video start",20,96,MUTED); label("Video duration",204,96,MUTED);
        label("Replay In",20,144,MUTED); label("Replay Out",204,144,MUTED);
        label("X",20,292,MUTED); label("Y",139,292,MUTED); label("Z",258,292,MUTED);
        String distance=kind==AutomatedShot.Kind.FLY_BY?"Side offset":"Distance";
        label(distance+" start",20,340,MUTED); label(distance+" end",204,340,MUTED);
        label("Height start",20,388,MUTED); label("Height end",204,388,MUTED);
        label(kind==AutomatedShot.Kind.FLY_BY?"Along-track start":kind==AutomatedShot.Kind.ORBIT?"Start angle":"Bearing start",20,436,MUTED);
        label(kind==AutomatedShot.Kind.FLY_BY?"Along-track end":kind==AutomatedShot.Kind.ORBIT?"Sweep degrees":"Bearing end",204,436,MUTED);
        label("Focus height",20,484,MUTED); label("Roll start",139,484,MUTED); label("Roll end",258,484,MUTED);
        label("Follow bearing: 0 front / 180 behind",20,614,MUTED);
        label("Easing changes the camera, not action speed.",20,634,MUTED);
        label("Choose Follow, pick a vehicle, then Place & preview to follow it.",408,canvasHeight-118,TEXT);
        label("Blue: camera   Green: target   View: top down",408,canvasHeight-98,MUTED);
        String guidance=target<0?"Fixed point selected: pick a vehicle for a moving follow shot.":!motion?"Target motion is Off: this shot uses a fixed position.":in.getText().equals(out.getText())?"Replay In = Out: action is frozen. Set action 1x for motion.":"Draft only: the camera follows during placed-path playback.";
        label(guidance,408,106,0xFFFFCB82);
        if(proposal!=null) {
            drawDiagram(proposal,414,126,canvasWidth-450,canvasHeight-314);
            label(proposal.frames.size()+" camera samples | "+String.format(Locale.ROOT,"%.3fx action",proposal.speed()),414,canvasHeight-170,ACCENT);
            label("Video "+StudioTime.format(proposal.start())+" - "+StudioTime.format(proposal.end()),414,canvasHeight-150,TEXT);
        } else {
            String status=loading!=null?"Reading recorded vehicle motion...":draftError.isEmpty()?"Updating draft...":draftError;
            List<String> lines=fontRendererObj.listFormattedStringToWidth(status,canvasWidth-454);
            for(int i=0;i<Math.min(7,lines.size());i++) label(lines.get(i),414,140+i*14,draftError.isEmpty()?MUTED:0xFFFFCB82);
        }
        boolean ready=proposal!=null && generated.equals(signature());
        for(Object value:buttonList) { GuiButton button=(GuiButton)value; if(button.id==12 || button.id==13) button.enabled=ready; }
    }
    private void drawDiagram(AutomatedShot.Plan p,int left,int top,int width,int height) {
        if(height<50) return;
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(AutomatedShot.Frame f:p.frames) { minX=Math.min(minX,Math.min(f.x,f.target.x)); minZ=Math.min(minZ,Math.min(f.z,f.target.z)); maxX=Math.max(maxX,Math.max(f.x,f.target.x)); maxZ=Math.max(maxZ,Math.max(f.z,f.target.z)); }
        double scale=Math.min((width-32)/Math.max(1,maxX-minX),(height-32)/Math.max(1,maxZ-minZ));
        double ox=left+width/2.0-(minX+maxX)*scale/2,oz=top+height/2.0-(minZ+maxZ)*scale/2;
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glLineWidth(2);
        try {
            Tessellator t=Tessellator.instance;
            t.startDrawing(GL11.GL_LINE_STRIP); t.setColorOpaque_I(ACCENT&0xffffff);
            for(AutomatedShot.Frame f:p.frames) t.addVertex(ox+f.x*scale,oz+f.z*scale,0); t.draw();
            t.startDrawing(GL11.GL_LINE_STRIP); t.setColorOpaque_I(0x80D4A3);
            for(AutomatedShot.Frame f:p.frames) t.addVertex(ox+f.target.x*scale,oz+f.target.z*scale,0); t.draw();
        } finally { GL11.glLineWidth(1); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1); }
        AutomatedShot.Frame first=p.frames.get(0),last=p.frames.get(p.frames.size()-1);
        int sx=(int)(ox+first.x*scale),sy=(int)(oz+first.z*scale),ex=(int)(ox+last.x*scale),ey=(int)(oz+last.z*scale);
        drawRect(sx-4,sy-4,sx+4,sy+4,0xFF80D4A3); drawRect(ex-3,ey-3,ex+3,ey+3,TimelineGraphics.RANGE);
        if(Math.hypot(sx-ex,sy-ey)<16) label("Start / End",sx+6,sy,TEXT);
        else { label("Start",sx+6,sy,TEXT); label("End",ex+6,ey,TEXT); }
    }
    @Override protected void activate(int id) {
        if(id==99) { escape(); return; }
        if(id>=1 && id<=3) {
            kind=AutomatedShot.Kind.values()[id-1];
            d0.setText(kind==AutomatedShot.Kind.FLY_BY?"12":"24"); d1.setText(d0.getText());
            a0.setText(kind==AutomatedShot.Kind.FLY_BY?"-50":"180"); a1.setText(kind==AutomatedShot.Kind.FLY_BY?"50":kind==AutomatedShot.Kind.ORBIT?"360":"180"); initGui();
        } else if(id==4) { long end=StudioTime.parse(in.getText())+StudioTime.parse(duration.getText()); if(end>replay.getReplayDuration()) throw new IllegalArgumentException("Not enough recording for 1x action. Choose an earlier Replay In or shorter duration."); out.setText(StudioTime.format(end)); }
        else if(id==5) out.setText(in.getText());
        else if(id==6) output.setText(StudioTime.format(parent.automaticShotStart()));
        else if(id==7 || id==8) {
            if(id==8) { List<ShotTargets.Target> candidates=ShotTargets.collect(mc,replay.getCameraEntity()); int picked=ShotTargets.aimed(candidates,replay.getCameraEntity()); if(picked<0) throw new IllegalArgumentException("No vehicle is aimed at or within 12 blocks. Close this panel, aim toward a vehicle, then reopen Auto shots."); targets=candidates; target=picked; }
            else { if(targets.isEmpty()) throw new IllegalStateException("No MCHR vehicles are available here. Move the replay to a time with a vehicle, then reopen Auto shots."); target=target+1>=targets.size()?-1:target+1; }
            discardTargetDraft();
            AutomatedShot.Pose p=target<0?pointAhead(replay.getCameraEntity()):targets.get(target).pose;
            px.setText(num(p.x)); py.setText(num(p.y)); pz.setText(num(p.z)); initGui();
        } else if(id==9) { motion=!motion; discardTargetDraft(); initGui(); }
        else if(id==10) { easing=AutomatedShot.Ease.values()[(easing.ordinal()+1)%AutomatedShot.Ease.values().length]; initGui(); }
        else if(id==11) { draftError=""; refreshDraft(); }
        else if(id==12 || id==13) {
            if(proposal==null || !generated.equals(signature())) throw new IllegalStateException("Wait for the updated shot draft.");
            AutomatedShot.place(ReplayModSimplePathing.instance.getCurrentTimeline(),proposal);
            parent.timingApplied(proposal.start(),proposal.end(),id==13);
        }
    }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
    @Override public void onGuiClosed() { if(loading!=null) { loading.cancel(true); loading=null; } super.onGuiClosed(); }
}
