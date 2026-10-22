package com.replaymod.studio;

/** Center crop in texture space; filling a dock must not stretch the world. */
public final class StudioViewerGeometry {
    private StudioViewerGeometry() { }
    public static float[] crop(double sourceAspect, int width, int height) {
        double targetAspect = Math.max(1, width) / (double) Math.max(1, height);
        double u = Math.min(1, targetAspect / sourceAspect), v = Math.min(1, sourceAspect / targetAspect);
        return new float[]{(float)((1-u)/2), (float)((1-v)/2), (float)((1+u)/2), (float)((1+v)/2)};
    }
}
