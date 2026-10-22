package com.replaymod.studio;

import com.replaymod.replay.camera.CameraEntity;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ShotTargetsTest {
    @Test public void closeVehicleBesideOrBelowCameraCanBePicked() throws Exception {
        CameraEntity camera=camera();
        assertEquals(0,ShotTargets.aimed(Collections.singletonList(target(1,3,0,0)),camera));
        assertEquals(0,ShotTargets.aimed(Collections.singletonList(target(1,0,-8,0)),camera));
    }
    @Test public void aimedVehicleTakesPriorityOverNearbyVehicleAndFallbackUsesNearest() throws Exception {
        CameraEntity camera=camera();
        assertEquals(1,ShotTargets.aimed(Arrays.asList(target(1,2,0,0),target(2,0,-2,100)),camera));
        assertEquals(1,ShotTargets.aimed(Arrays.asList(target(1,8,0,0),target(2,3,0,0)),camera));
    }
    @Test public void distantVehicleOutsideAimAndEmptyListRemainUnselected() throws Exception {
        CameraEntity camera=camera();
        assertEquals(-1,ShotTargets.aimed(Collections.singletonList(target(1,13,0,0)),camera));
        assertEquals(-1,ShotTargets.aimed(Collections.emptyList(),camera));
        camera.rotationYaw=90;
        assertEquals(0,ShotTargets.aimed(Collections.singletonList(target(1,-100,-2,0)),camera));
    }
    private static ShotTargets.Target target(int id,double x,double y,double z) { return new ShotTargets.Target(id,"Vehicle",new AutomatedShot.Pose(x,y,z,0)); }
    private static CameraEntity camera() throws Exception {
        Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return (CameraEntity)((Unsafe)f.get(null)).allocateInstance(CameraEntity.class);
    }
}
