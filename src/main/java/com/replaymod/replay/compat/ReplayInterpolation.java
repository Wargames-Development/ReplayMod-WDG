package com.replaymod.replay.compat;

/** One bounded position curve for recorded aircraft and cameras tracking them. */
public final class ReplayInterpolation {
    private ReplayInterpolation() { }

    public static double smooth(double p, double a, double b, double n,
            double h0, double h, double h1, double t, boolean previous, boolean next) {
        double d = (b - a) / h;
        double m0 = previous ? tangent((a - p) / h0, d, h0, h) : d;
        double m1 = next ? tangent(d, (n - b) / h1, h, h1) : d;
        double t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * a + (t3 - 2 * t2 + t) * h * m0
                + (-2 * t3 + 3 * t2) * b + (t3 - t2) * h * m1;
    }

    private static double tangent(double a, double b, double ha, double hb) {
        if (a * b <= 0) return 0;
        double w1 = 2 * hb + ha, w2 = hb + 2 * ha;
        return (w1 + w2) / (w1 / a + w2 / b);
    }
}
