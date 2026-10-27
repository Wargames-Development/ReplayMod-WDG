package com.replaymod.studio;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShotDiagramTest {
    @Test public void topAndSideDragsMoveExactlyWithTheMouseInBothReferenceSpaces() {
        for(long duration:new long[]{6000,101}) for(AutomatedShot.Space space:AutomatedShot.Space.values()) for(ShotDiagram.View view:ShotDiagram.View.values()) {
            AutomatedShot.Settings s=new AutomatedShot.Settings(); s.space=space;
            s.duration=duration;
            AutomatedShot.Target target=t -> new AutomatedShot.Pose(10+t/200.0,80+t/400.0,20,73);
            s.replayOut=6000;
            AutomatedShot.Plan before=AutomatedShot.generate(s,target,6000);
            ShotDiagram diagram=new ShotDiagram(before,view,414,166,670,340);
            AutomatedShot.Frame original=before.handles.get(2);
            s.curve=diagram.drag(s.curve,2,original,space,27,-19);
            AutomatedShot.Frame moved=AutomatedShot.generate(s,target,6000).handles.get(2);
            assertEquals(27,diagram.x(moved)-diagram.x(original),1e-8);
            assertEquals(-19,diagram.y(moved)-diagram.y(original),1e-8);
            if(view==ShotDiagram.View.TOP) assertEquals(original.y,moved.y,0);
            else assertEquals(original.z,moved.z,1e-8);
            assertEquals(original.replay,moved.replay); assertEquals(original.time,moved.time);
            assertEquals(2,diagram.pick(before,(int)Math.round(diagram.x(original)),(int)Math.round(diagram.y(original))));
            assertEquals(-1,diagram.pick(before,-10000,-10000));
        }
    }
}
