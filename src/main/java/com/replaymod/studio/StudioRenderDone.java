package com.replaymod.studio;

import com.replaymod.core.versions.MCVer;
import com.replaymod.replay.ReplayModReplay;
import java.io.File;

public final class StudioRenderDone extends StudioScreen {
    private final File file;
    private final int frames;
    public StudioRenderDone(File file, int frames) { this.file = file; this.frames = frames; }
    @Override protected void build() {
        button(1, 236, 294, 156, file.isDirectory() ? "Open image sequence" : file.getName().endsWith(".png") ? "Open image" : "Play video");
        button(2, 402, 294, 156, "Open output folder"); button(3, 568, 294, 156, "Back to editor");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        panel(220, 194, 520, 138, file.getName().endsWith(".png") ? "IMAGE EXPORTED" : "VIDEO EXPORTED");
        label(frames + " frames exported successfully.", 236, 226, TEXT);
        label(fontRendererObj.trimStringToWidth(file.getAbsolutePath(), 488), 236, 246, MUTED);
        label("Your shot sequence is saved inside the recording.", 236, 270, MUTED);
    }
    @Override protected void activate(int id) {
        if (id == 1) MCVer.openFile(file);
        if (id == 2) MCVer.openFile(file.getParentFile());
        if (id == 3) escape();
    }
    @Override protected void escape() {
        if (ReplayModReplay.instance.getReplayHandler() != null) mc.displayGuiScreen(ReplayModReplay.instance.getReplayHandler().getOverlay().getStudioEditor());
        else mc.displayGuiScreen(null);
    }
}
