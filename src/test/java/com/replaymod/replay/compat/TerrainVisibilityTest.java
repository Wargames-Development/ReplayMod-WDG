package com.replaymod.replay.compat;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.replaymod.replay.compat.TerrainVisibility.Result.*;

public class TerrainVisibilityTest {
    @Test public void mountainBlocksAPlaneEvenAfterMissingChunksButFlyingAboveItStaysVisible() {
        TerrainVisibility.Columns terrain=(x,z,low,high) -> x<0?UNKNOWN:TerrainVisibility.intervals(x>=30 && x<=40?new int[]{0,120}:new int[0],low,high);
        assertEquals(BLOCKED,TerrainVisibility.trace(terrain,-20,80,5,200,80,5));
        assertEquals(CLEAR,TerrainVisibility.trace(terrain,5,160,5,200,160,5));
        assertEquals(UNKNOWN,TerrainVisibility.trace(terrain,-20,160,5,200,160,5));
    }
    @Test public void verticalClimbingNegativeAndDiagonalRaysVisitTheTerrainTheyCross() {
        TerrainVisibility.Columns wall=(x,z,low,high) -> TerrainVisibility.intervals(x==-20 && z==-20?new int[]{64,80}:new int[0],low,high);
        assertEquals(BLOCKED,TerrainVisibility.trace(wall,-20.5,20,-20.5,-19.5,100,-19.5));
        assertEquals(BLOCKED,TerrainVisibility.trace(wall,-19.5,60,-19.5,-19.5,90,-19.5));
        assertEquals(CLEAR,TerrainVisibility.trace(wall,-19.5,80,-19.5,-19.5,120,-19.5));
        assertEquals(BLOCKED,TerrainVisibility.trace(wall,-40.5,70,-40.5,-.5,70,-.5));
        assertEquals(BLOCKED,TerrainVisibility.trace(wall,-.5,70,-.5,-40.5,70,-40.5));
    }
    @Test public void unknownDataAndTraversalLimitsNeverInventAMountain() {
        assertEquals(UNKNOWN,TerrainVisibility.trace((x,z,low,high) -> UNKNOWN,0,80,0,20,80,0));
        assertEquals(UNKNOWN,TerrainVisibility.trace((x,z,low,high) -> CLEAR,0,80,0,20000,80,0));
        assertEquals(UNKNOWN,TerrainVisibility.trace((x,z,low,high) -> BLOCKED,Double.NaN,80,0,20,80,0));
        assertEquals(UNKNOWN,TerrainVisibility.trace(new TerrainVisibility.Columns() {
            public boolean exhausted() { return true; }
            public TerrainVisibility.Result test(int x,int z,double low,double high) { fail("Exhausted frame must not query terrain"); return BLOCKED; }
        },0,80,0,20,80,0));
    }
}
