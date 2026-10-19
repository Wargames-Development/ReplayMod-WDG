package com.replaymod.studio;

import java.util.Locale;

/** Shared clock parsing for the replay clock and the output clock. */
public final class StudioTime {
    private StudioTime() { }
    public static long parse(String text) {
        try {
            String[] parts = text.trim().split(":", -1);
            if (parts.length > 3) throw new NumberFormatException();
            double seconds = 0;
            for (int i = 0; i < parts.length; i++) {
                double part = Double.parseDouble(parts[i]);
                if (!Double.isFinite(part) || part < 0 || (i > 0 && part >= 60)) throw new NumberFormatException();
                if (i < parts.length - 1 && part != Math.floor(part)) throw new NumberFormatException();
                seconds = seconds * 60 + part;
            }
            if (seconds * 1000 > Integer.MAX_VALUE) throw new NumberFormatException();
            return Math.round(seconds * 1000);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Enter seconds (12.5) or minutes:seconds (1:12.500).");
        }
    }
    public static String format(long milliseconds) {
        return String.format(Locale.ROOT, "%d:%06.3f", milliseconds / 60000, milliseconds % 60000 / 1000.0);
    }
    public static double number(String value, String name) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + " must be a finite number."); }
    }
}
