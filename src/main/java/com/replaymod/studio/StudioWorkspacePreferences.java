package com.replaymod.studio;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Layout preferences are independent of the recorded camera path and its undo history. */
public final class StudioWorkspacePreferences {
    public boolean fillViewer = true;
    public double library = .17, inspector = .23, timeline = .31;
    public static StudioWorkspacePreferences load(File file) {
        if (file.isFile()) try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StudioWorkspacePreferences value = new Gson().fromJson(reader, StudioWorkspacePreferences.class);
            if (value != null) { value.normalize(); return value; }
        } catch (IOException | RuntimeException ignored) { }
        return new StudioWorkspacePreferences();
    }
    public void save(File file) throws IOException {
        normalize();
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), new Gson().toJson(this).getBytes(StandardCharsets.UTF_8));
    }
    private void normalize() {
        library = fraction(library, .17); inspector = fraction(inspector, .23); timeline = fraction(timeline, .31);
    }
    private static double fraction(double value, double fallback) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback; }
}
