package com.replaymod.studio;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;

/** A non-destructive shot draft, with background vehicle-track loading and explicit placement. */
public final class StudioAutoShot extends StudioScreen {
    private static final int HISTORY_LIMIT=32;
    private final Deque<CurveState> undoCurves=new ArrayDeque<>(),redoCurves=new ArrayDeque<>();
    private final CinematicEditor parent;
    private final ReplayHandler replay;
    private AutomatedShot.Kind kind=AutomatedShot.Kind.ORBIT;
    private AutomatedShot.Ease easing=AutomatedShot.Ease.IN_OUT;
    private AutomatedShot.Space space=AutomatedShot.Space.VEHICLE;
    private ShotCurve curve=ShotCurve.flat(),dragStartCurve;
    private ShotDiagram.View view=ShotDiagram.View.TOP;
    private ShotDiagram diagram;
    private int selectedPoint,draggingPoint=-1,dragX,dragY,curveRevision;
    private AutomatedShot.Frame dragHandle;
    private List<ShotTargets.Target> targets;
    private int target=-1,loadedId=Integer.MIN_VALUE,loadingId=Integer.MIN_VALUE;
    private boolean motion=true;
    private boolean followYaw=true,followPitch=true,followRoll=true;
    private FutureTask<RecordedVehicleTrack> loading;
    private RecordedVehicleTrack track;
    private GuiTextField output,duration,in,out,px,py,pz,d0,d1,h0,h1,a0,a1,focus,r0,r1,lookPitch;
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
        cancelledDrag(); diagram=null;
        CameraEntity camera=replay.getCameraEntity();
        if(camera==null) throw new IllegalStateException("Wait for the replay camera to finish loading.");
        if(targets==null) { targets=ShotTargets.collect(mc,camera); target=ShotTargets.aimed(targets,camera); }
        button(1,20,52,108,(kind==AutomatedShot.Kind.ORBIT?"> ":"")+"Orbit");
        button(2,138,52,108,(kind==AutomatedShot.Kind.FOLLOW?"> ":"")+"Follow");
        button(3,256,52,108,(kind==AutomatedShot.Kind.FLY_BY?"> ":"")+"Fly-by");
        button(24,374,52,108,(kind==AutomatedShot.Kind.MOUNTED?"> ":"")+"Mounted");
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
        if(kind==AutomatedShot.Kind.MOUNTED) {
            lookPitch=field(500,52,106,text(lookPitch,"0"));
            button(25,616,52,150,"Follow yaw: "+(followYaw?"On":"Off"));
            button(26,776,52,150,"Follow pitch: "+(followPitch?"On":"Off"));
            button(27,936,52,150,"Follow roll: "+(followRoll?"On":"Off"));
        }
        button(10,20,538,344,"Camera easing: "+easeName());
        button(11,20,574,344,"Refresh draft");
        button(12,canvasWidth-374,canvasHeight-62,160,"Place shot");
        button(13,canvasWidth-200,canvasHeight-62,180,"Place & preview");
        button(14,414,130,236,"Reference: "+(space==AutomatedShot.Space.VEHICLE?"Vehicle relative":kind==AutomatedShot.Kind.MOUNTED?"Frozen mount at In":"World relative"));
        button(15,660,130,140,view==ShotDiagram.View.TOP?"View: Top (X/Z)":"View: Side (X/Y)");
        button(16,810,130,130,"Reset path"); button(17,950,130,140,"Fit view");
        button(18,414,canvasHeight-180,50,"<"); button(19,474,canvasHeight-180,50,">");
        button(20,534,canvasHeight-180,140,"Add after point"); button(21,684,canvasHeight-180,130,"Remove point");
        button(22,824,canvasHeight-180,130,"Undo [Ctrl+Z]"); button(23,964,canvasHeight-180,130,"Redo [Ctrl+Y]");
    }
    private static String text(GuiTextField field,String fallback) { return field==null?fallback:field.getText(); }
    private static String num(double n) { return String.format(Locale.ROOT,"%.3f",n); }
    private static AutomatedShot.Pose pointAhead(CameraEntity c) {
        double yaw=Math.toRadians(c.rotationYaw),pitch=Math.toRadians(c.rotationPitch);
        return new AutomatedShot.Pose(c.posX-Math.sin(yaw)*Math.cos(pitch)*30,c.posY-Math.sin(pitch)*30,c.posZ+Math.cos(yaw)*Math.cos(pitch)*30,0);
    }
    private String easeName() { return new String[]{"Linear","Ease in / out","Ease in","Ease out"}[easing.ordinal()]; }
    @Override protected String fieldHelp(int index) {
        if(kind==AutomatedShot.Kind.MOUNTED) switch(index) {
            case 7: case 8: return "Side offset\nLocal X in blocks. Use opposite signs for the two wings; keep start and end equal for a fixed mount.";
            case 9: case 10: return "Up offset\nBlocks above the aircraft origin in its own coordinate system. This offset banks and pitches with the aircraft.";
            case 11: case 12: return "Forward offset\nLocal Z in blocks. Positive is toward the nose; negative is toward the tail.";
            case 13: return "Look yaw\nView angle relative to the enabled aircraft rotation axes. Zero looks forward; 180 looks backward.";
            case 14: case 15: return "View roll offset\nAdditional camera tilt in degrees, after the enabled aircraft rotation axes. Keep both values zero for a rigid mount.";
            case 16: return "Look pitch\nView angle in degrees. Positive looks down; negative looks up. This is composed with the enabled aircraft rotation axes.";
        }
        return super.fieldHelp(index);
    }
    private String signature() {
        StringBuilder s=new StringBuilder(kind+"/"+easing+"/"+space+"/"+curveRevision+"/"+(target<0?"point":targets.get(target).id)+"/"+motion+"/"+followYaw+"/"+followPitch+"/"+followRoll);
        for(GuiTextField f:fields) s.append('/').append(f.getText()); return s.toString();
    }
    private AutomatedShot.Settings settings() {
        AutomatedShot.Settings s=new AutomatedShot.Settings(); s.kind=kind; s.ease=easing;
        s.space=space; s.curve=curve;
        s.outputStart=StudioTime.parse(output.getText()); s.duration=StudioTime.parse(duration.getText());
        long from=StudioTime.parse(in.getText()),to=StudioTime.parse(out.getText());
        if(from>Integer.MAX_VALUE || to>Integer.MAX_VALUE) throw new IllegalArgumentException("Replay time is outside this recording.");
        s.replayIn=(int)from; s.replayOut=(int)to;
        s.distanceStart=StudioTime.number(d0.getText(),"Starting distance"); s.distanceEnd=StudioTime.number(d1.getText(),"Ending distance");
        s.heightStart=StudioTime.number(h0.getText(),"Starting height"); s.heightEnd=StudioTime.number(h1.getText(),"Ending height");
        s.angleStart=StudioTime.number(a0.getText(),"Starting angle / travel"); s.angleChange=StudioTime.number(a1.getText(),"Ending angle / travel");
        if(kind==AutomatedShot.Kind.FOLLOW) s.angleChange-=s.angleStart;
        s.focusHeight=StudioTime.number(focus.getText(),"Focus height"); s.rollStart=StudioTime.number(r0.getText(),"Starting roll"); s.rollEnd=StudioTime.number(r1.getText(),"Ending roll");
        if(kind==AutomatedShot.Kind.MOUNTED) {
            s.viewYaw=s.focusHeight; s.viewPitch=StudioTime.number(lookPitch.getText(),"Look pitch");
            s.followYaw=followYaw; s.followPitch=followPitch; s.followRoll=followRoll;
        }
        return s;
    }
    private void refreshDraft() {
        proposal=null; draftError="";
        AutomatedShot.Settings settings=settings();
        if(kind==AutomatedShot.Kind.MOUNTED && target<0) throw new IllegalArgumentException("Pick a vehicle for a mounted camera. Side / Up / Forward set its attachment offset.");
        AutomatedShot.Target targetPose;
        if(target>=0 && motion) {
            int id=targets.get(target).id;
            if(track==null || loadedId!=id) {
                if(loading==null || loadingId!=id) loadTrack(id);
                return;
            }
            targetPose=track;
        } else {
            AutomatedShot.Pose selected=target<0?new AutomatedShot.Pose(0,0,0,0):targets.get(target).pose;
            AutomatedShot.Pose pose=new AutomatedShot.Pose(StudioTime.number(px.getText(),"Target X"),StudioTime.number(py.getText(),"Target Y"),StudioTime.number(pz.getText(),"Target Z"),selected.yaw,selected.pitch,selected.roll);
            targetPose=unused -> pose;
        }
        AutomatedShot.Plan generatedPlan=AutomatedShot.generate(settings,targetPose,replay.getReplayDuration());
        AutomatedShot.validatePlacement(ReplayModSimplePathing.instance.getCurrentTimeline(),generatedPlan);
        proposal=generatedPlan;
        generated=signature();
        observed=generated;
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
        if(!current.equals(observed)) { observed=current; changedAt=System.currentTimeMillis(); proposal=null; diagram=null; draftError=""; }
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
        boolean mounted=kind==AutomatedShot.Kind.MOUNTED;
        if(mounted) label("Look pitch",500,36,MUTED);
        String distance=kind==AutomatedShot.Kind.FLY_BY || mounted?"Side offset":"Distance";
        label(distance+" start",20,340,MUTED); label(distance+" end",204,340,MUTED);
        label(mounted?"Up start":"Height start",20,388,MUTED); label(mounted?"Up end":"Height end",204,388,MUTED);
        label(mounted?"Forward start":kind==AutomatedShot.Kind.FLY_BY?"Along-track start":kind==AutomatedShot.Kind.ORBIT?"Start angle":"Bearing start",20,436,MUTED);
        label(mounted?"Forward end":kind==AutomatedShot.Kind.FLY_BY?"Along-track end":kind==AutomatedShot.Kind.ORBIT?"Sweep degrees":"Bearing end",204,436,MUTED);
        label(mounted?"Look yaw":"Focus height",20,484,MUTED); label("Roll start",139,484,MUTED); label("Roll end",258,484,MUTED);
        label(mounted?"Local blocks: side / up / forward":"Follow bearing: 0 front / 180 behind",20,614,MUTED);
        label("Easing changes the camera, not action speed.",20,634,MUTED);
        label("Drag numbered points. Top adjusts X/Z; Side adjusts X/Y.",408,canvasHeight-118,TEXT);
        label("Blue: camera   Green: target   Place & preview to watch the shot.",408,canvasHeight-98,MUTED);
        String guidance=target<0?"Fixed point selected: pick a vehicle for a moving shot.":!motion?"Target motion is Off: this shot uses a fixed position.":in.getText().equals(out.getText())?"Replay In = Out: action is frozen. Set action 1x for motion.":mounted?(space==AutomatedShot.Space.WORLD?"Mount frozen at Replay In. Use Vehicle relative to follow.":"Mount follows the full body pose; toggles control view rotation."):space==AutomatedShot.Space.WORLD?"Path anchored at Replay In; aim tracks the moving vehicle.":"Path follows vehicle position and heading; pitch / bank stay independent.";
        label(guidance,408,106,0xFFFFCB82);
        if(proposal!=null) {
            drawDiagram(proposal,414,166,canvasWidth-450,canvasHeight-380);
            AutomatedShot.Frame handle=proposal.handles.get(selectedPoint);
            label("Point "+(selectedPoint+1)+" / "+curve.points.size()+"  |  Video "+StudioTime.format(handle.time)+"  |  Height "+num(handle.y),414,canvasHeight-200,TEXT);
            label(proposal.frames.size()+" samples | "+String.format(Locale.ROOT,"%.3fx action",proposal.speed())+" | Video "+StudioTime.format(proposal.start())+" - "+StudioTime.format(proposal.end()),414,canvasHeight-142,ACCENT);
        } else {
            String status=loading!=null?"Reading recorded vehicle motion...":draftError.isEmpty()?"Updating draft...":draftError;
            List<String> lines=fontRendererObj.listFormattedStringToWidth(status,canvasWidth-454);
            for(int i=0;i<Math.min(7,lines.size());i++) label(lines.get(i),414,174+i*14,draftError.isEmpty()?MUTED:0xFFFFCB82);
        }
        boolean ready=proposal!=null && generated.equals(signature());
        for(Object value:buttonList) {
            GuiButton button=(GuiButton)value;
            if(button.id==12 || button.id==13 || button.id==18 || button.id==19) button.enabled=ready;
            if(button.id==20) button.enabled=ready && curve.canAddAfter(selectedPoint);
            if(button.id==21) button.enabled=ready && selectedPoint>0 && selectedPoint<curve.points.size()-1;
            if(button.id==22) button.enabled=!undoCurves.isEmpty() || dragStartCurve!=null && !sameCurve(curve,dragStartCurve);
            if(button.id==23) button.enabled=!redoCurves.isEmpty();
        }
    }
    private void drawDiagram(AutomatedShot.Plan p,int left,int top,int width,int height) {
        if(height<50) return;
        if(diagram==null) diagram=new ShotDiagram(p,view,left,top,width,height);
        ShotDiagram d=diagram;
        StudioViewport viewport=createViewport();
        double sx=mc.displayWidth/(double)this.width,sy=mc.displayHeight/(double)this.height;
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int)((viewport.x+left*viewport.scale)*sx),(int)(mc.displayHeight-(viewport.y+(top+height)*viewport.scale)*sy),
                Math.max(0,(int)(width*viewport.scale*sx)),Math.max(0,(int)(height*viewport.scale*sy)));
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glLineWidth(2);
        try {
            Tessellator t=Tessellator.instance;
            t.startDrawing(GL11.GL_LINE_STRIP); t.setColorOpaque_I(ACCENT&0xffffff);
            for(AutomatedShot.Frame f:p.frames) t.addVertex(d.x(f),d.y(f),0); t.draw();
            t.startDrawing(GL11.GL_LINE_STRIP); t.setColorOpaque_I(0x80D4A3);
            for(AutomatedShot.Frame f:p.frames) t.addVertex(d.targetX(f),d.targetY(f),0); t.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            for(int i=0;i<p.handles.size();i++) if(i!=selectedPoint) drawHandle(p,i,false);
            drawHandle(p,selectedPoint,true);
        } finally { GL11.glLineWidth(1); GL11.glPopAttrib(); GL11.glColor4f(1,1,1,1); }
    }
    private void drawHandle(AutomatedShot.Plan plan,int index,boolean selected) {
        AutomatedShot.Frame f=plan.handles.get(index);
        int x=(int)Math.round(diagram.x(f)),y=(int)Math.round(diagram.y(f)),size=selected?5:4;
        int color=selected?0xFFFFCB82:index==0?0xFF80D4A3:index==plan.handles.size()-1?TimelineGraphics.RANGE:ACCENT;
        drawRect(x-size-1,y-size-1,x+size+2,y+size+2,0xFF0D131A);
        drawRect(x-size,y-size,x+size+1,y+size+1,color);
        label(Integer.toString(index+1),x+8,y-4,color);
    }
    @Override protected void clicked(int x,int y,int button) {
        if(button!=0 || diagram==null || proposal==null || !generated.equals(signature()) || !hit(x,y,diagram.left,diagram.top,diagram.width,diagram.height)) return;
        int picked=diagram.pick(proposal,x,y,selectedPoint);
        if(picked<0) return;
        selectedPoint=picked; draggingPoint=picked; dragStartCurve=curve; dragHandle=proposal.handles.get(picked); dragX=x; dragY=y;
    }
    @Override protected void dragged(int x,int y,int button) {
        if(button!=0 || draggingPoint<0 || diagram==null) return;
        x=Math.max(diagram.left+8,Math.min(diagram.left+diagram.width-8,x));
        y=Math.max(diagram.top+8,Math.min(diagram.top+diagram.height-8,y));
        ShotCurve previous=curve; AutomatedShot.Plan previousPlan=proposal;
        try {
            ShotCurve next=diagram.drag(dragStartCurve,draggingPoint,dragHandle,space,x-dragX,y-dragY);
            if(sameCurve(curve,next)) return;
            curve=next; curveRevision++;
            refreshDraft(); message="";
        } catch(IllegalArgumentException | IllegalStateException error) {
            curve=previous; proposal=previousPlan; generated=signature(); draftError=""; message=error.getMessage();
        }
        observed=signature();
    }
    @Override protected void released(int x,int y,int button) { if(button==0) cancelledDrag(); }
    @Override protected void cancelledDrag() {
        // Store one before-state per gesture, even if resize or another control ends the drag.
        if(dragStartCurve!=null && !sameCurve(curve,dragStartCurve)) rememberCurve(new CurveState(dragStartCurve,draggingPoint));
        draggingPoint=-1; dragStartCurve=null; dragHandle=null;
    }
    private static final class CurveState {
        final ShotCurve curve; final int selected;
        CurveState(ShotCurve curve,int selected) { this.curve=curve; this.selected=selected; }
    }
    private static boolean sameCurve(ShotCurve a,ShotCurve b) {
        if(a.points.size()!=b.points.size()) return false;
        for(int i=0;i<a.points.size();i++) {
            ShotCurve.Point x=a.points.get(i),y=b.points.get(i);
            if(x.t!=y.t || x.x!=y.x || x.y!=y.y || x.z!=y.z) return false;
        }
        return true;
    }
    private void rememberCurve(CurveState state) {
        pushCurve(undoCurves,state); redoCurves.clear();
    }
    private static void pushCurve(Deque<CurveState> history,CurveState state) {
        history.addLast(state);
        if(history.size()>HISTORY_LIMIT) history.removeFirst();
    }
    private void editCurve(ShotCurve next,int selected) {
        cancelledDrag();
        if(!sameCurve(curve,next)) { rememberCurve(new CurveState(curve,selectedPoint)); curve=next; curveRevision++; }
        selectedPoint=selected;
    }
    private void restoreCurve(boolean redo) {
        cancelledDrag();
        Deque<CurveState> from=redo?redoCurves:undoCurves,to=redo?undoCurves:redoCurves;
        if(from.isEmpty()) return;
        pushCurve(to,new CurveState(curve,selectedPoint));
        CurveState state=from.removeLast(); curve=state.curve; selectedPoint=state.selected; curveRevision++;
        proposal=null; draftError=""; message="";
        // Refresh immediately without refitting the diagram or changing other shot fields.
        observed=signature(); changedAt=0;
        pollDraft();
    }
    boolean historyKey(int key,boolean control,boolean shift) {
        if(!control || key!=Keyboard.KEY_Z && key!=Keyboard.KEY_Y) return false;
        attempt(() -> restoreCurve(key==Keyboard.KEY_Y || shift)); return true;
    }
    @Override protected void keyTyped(char character,int key) {
        if(historyKey(key,isCtrlKeyDown(),isShiftKeyDown())) return;
        super.keyTyped(character,key);
    }
    @Override protected void activate(int id) {
        if(id==99) { escape(); return; }
        if(id>=1 && id<=3 || id==24) {
            boolean wasMounted=kind==AutomatedShot.Kind.MOUNTED;
            kind=id==24?AutomatedShot.Kind.MOUNTED:AutomatedShot.Kind.values()[id-1];
            d0.setText(kind==AutomatedShot.Kind.FLY_BY?"12":"24"); d1.setText(d0.getText());
            a0.setText(kind==AutomatedShot.Kind.FLY_BY?"-50":"180"); a1.setText(kind==AutomatedShot.Kind.FLY_BY?"50":kind==AutomatedShot.Kind.ORBIT?"360":"180");
            if(kind==AutomatedShot.Kind.MOUNTED) {
                d0.setText("8"); d1.setText("8"); h0.setText("1"); h1.setText("1");
                a0.setText("0"); a1.setText("0"); focus.setText("0"); space=AutomatedShot.Space.VEHICLE;
            } else if(wasMounted) { h0.setText("10"); h1.setText("10"); focus.setText("2"); }
            initGui();
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
        else if(id==14) { space=space==AutomatedShot.Space.VEHICLE?AutomatedShot.Space.WORLD:AutomatedShot.Space.VEHICLE; initGui(); }
        else if(id==15) { view=view==ShotDiagram.View.TOP?ShotDiagram.View.SIDE:ShotDiagram.View.TOP; initGui(); }
        else if(id==16) { editCurve(ShotCurve.flat(),0); diagram=null; }
        else if(id==17) diagram=null;
        else if(id==18 || id==19) selectedPoint=(selectedPoint+(id==18?curve.points.size()-1:1))%curve.points.size();
        else if(id==20) editCurve(curve.addAfter(selectedPoint),selectedPoint+1);
        else if(id==21) editCurve(curve.remove(selectedPoint),selectedPoint-1);
        else if(id==22 || id==23) restoreCurve(id==23);
        else if(id>=25 && id<=27) {
            if(id==25) followYaw=!followYaw; else if(id==26) followPitch=!followPitch; else followRoll=!followRoll;
            initGui();
        }
        else if(id==12 || id==13) {
            if(proposal==null || !generated.equals(signature())) throw new IllegalStateException("Wait for the updated shot draft.");
            AutomatedShot.place(ReplayModSimplePathing.instance.getCurrentTimeline(),proposal);
            parent.timingApplied(proposal.start(),proposal.end(),id==13);
        }
    }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
    @Override public void onGuiClosed() { if(loading!=null) { loading.cancel(true); loading=null; } super.onGuiClosed(); }
}
