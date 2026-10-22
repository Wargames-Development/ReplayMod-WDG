package com.replaymod.replay.compat;

/** Main and shadow terrain must converge before capturing an output frame. */
public final class ReplayTerrainReadiness {
    private static long uploads;
    private static long previousUploads;
    private static int passes;
    private ReplayTerrainReadiness() { }
    public static void meshUploaded() { uploads++; }
    public static void beginFrame() { passes=0; previousUploads=uploads; }
    public static boolean afterPass() {
        passes++;
        boolean stable=uploads==previousUploads;
        previousUploads=uploads;
        // Shadow rendering precedes main terrain uploads. A second pass is
        // necessary even if the first only discovered the new visibility graph.
        return passes>=2 && stable;
    }
}
