package com.replaymod.replay;

import com.replaymod.replay.compat.ReplayRiders;
import com.replaymod.replay.compat.ReplayEffects;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReplayRidersTest {
    @Test public void seatStaysRigidlyAttachedThroughRapidBankPitchAndHeadingChanges() {
        for (int frame=0;frame<600;frame++) {
            double[] body={frame*7.5,80+frame*.1,-frame*2.0,frame*2.3,70*Math.sin(frame*.07),170*Math.sin(frame*.13)};
            double[] seat=ReplayRiders.seatPosition(body,1.2,2.7,-4.1);
            double distance=0; for(int i=0;i<3;i++) distance+=(seat[i]-body[i])*(seat[i]-body[i]);
            assertEquals(1.2*1.2+2.7*2.7+4.1*4.1,distance,1e-9);
            assertArrayEquals(seat,ReplayRiders.seatPosition(body,1.2,2.7,-4.1),0);
        }
        assertArrayEquals(new double[]{13,22,31},ReplayRiders.seatPosition(new double[]{10,20,30,90,0,0},1,2,-3),1e-9);
    }
    @Test public void distantImpactRetainsDirectionAndRemainsInsideAudibleRadiusOfTheCamera() {
        assertArrayEquals(new double[]{20,0,0},ReplayEffects.explosionSource(20,0,0,0,0,0),0);
        for (double distance:new double[]{64,256,1000,2047}) {
            double[] source=ReplayEffects.explosionSource(100+distance,80,200,100,80,200);
            assertTrue(source[0]>100); assertTrue(source[0]-100<64);
            assertEquals(80,source[1],0); assertEquals(200,source[2],0);
        }
        assertNull(ReplayEffects.explosionSource(2048,0,0,0,0,0));
    }
}
