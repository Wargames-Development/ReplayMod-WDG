package com.replaymod.studio;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ShotCurveTest {
    @Test public void nonuniformPointsHaveExactPositionsAndContinuousTangents() {
        ShotCurve curve=new ShotCurve(Arrays.asList(new ShotCurve.Point(0,0,0,0),new ShotCurve.Point(.1,4,2,-3),
                new ShotCurve.Point(.7,-2,8,10),new ShotCurve.Point(1,1,-4,0)));
        for(ShotCurve.Point p:curve.points) {
            ShotCurve.Point actual=curve.at(p.t);
            assertEquals(p.x,actual.x,1e-10); assertEquals(p.y,actual.y,1e-10); assertEquals(p.z,actual.z,1e-10);
        }
        double epsilon=1e-7;
        for(double t:new double[]{.1,.7}) {
            ShotCurve.Point a=curve.at(t-epsilon),b=curve.at(t),c=curve.at(t+epsilon);
            assertEquals((b.x-a.x)/epsilon,(c.x-b.x)/epsilon,.001);
            assertEquals((b.y-a.y)/epsilon,(c.y-b.y)/epsilon,.001);
            assertEquals((b.z-a.z)/epsilon,(c.z-b.z)/epsilon,.001);
        }
    }
    @Test public void addingStartsOnTheCurveAndEditsDoNotMutatePreviousDrafts() {
        ShotCurve original=ShotCurve.flat(),moved=original.move(2,10,7,-4),added=moved.addAfter(1);
        assertEquals(0,original.points.get(2).x,0);
        ShotCurve.Point expected=moved.at(.375),actual=added.points.get(2);
        assertEquals(.375,actual.t,0); assertEquals(expected.x,actual.x,0); assertEquals(expected.y,actual.y,0); assertEquals(expected.z,actual.z,0);
        ShotCurve removed=added.remove(2);
        assertEquals(moved.points.size(),removed.points.size());
        for(int i=0;i<moved.points.size();i++) assertSame(moved.points.get(i),removed.points.get(i));
    }
    @Test public void endpointsAndPointCountAndSpacingAreProtected() {
        ShotCurve curve=ShotCurve.flat();
        rejected(() -> ShotCurve.flat().remove(0)); rejected(() -> ShotCurve.flat().remove(4));
        rejected(() -> ShotCurve.flat().move(1,Double.NaN,0,0));
        rejected(() -> new ShotCurve(Arrays.asList(new ShotCurve.Point(0,0,0,0),new ShotCurve.Point(.001,0,0,0),new ShotCurve.Point(1,0,0,0))));
        while(curve.points.size()<ShotCurve.MAX_POINTS) {
            int i=0; while(!curve.canAddAfter(i)) i++;
            curve=curve.addAfter(i);
        }
        assertFalse(curve.canAddAfter(0)); final ShotCurve full=curve; rejected(() -> full.addAfter(0));
    }
    private static void rejected(Runnable action) { try { action.run(); fail("Expected invalid curve"); } catch(IllegalArgumentException expected) { } }
}
