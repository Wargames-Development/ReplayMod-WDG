package com.replaymod.studio;

import com.replaymod.core.versions.MCVer;
import com.replaymod.replay.ReplayModReplay;
import java.io.File;

public final class StudioRenderDone extends StudioScreen {
    private final File file;
    private final int frames;
    public StudioRenderDone(File file, int frames) { this.file = file; this.frames = frames; }
    @Override protected void build() {
        button(1, 170, 330, 200, file.isDirectory() ? "Open image sequence" : file.getName().endsWith(".png") ? "Open image" : "Play video"); button(2, 380, 330, 200, "Open output folder"); button(3, 590, 330, 200, "Back to editor");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xC0101720); panel(140, 150, 680, 240, file.getName().endsWith(".png") ? "IMAGE EXPORTED" : "VIDEO EXPORTED");
        label(frames + " frames exported successfully.", 170, 200, TEXT);
        label(fontRendererObj.trimStringToWidth(file.getAbsolutePath(), 620), 170, 230, MUTED);
        label("Your shot sequence is saved inside the recording.", 170, 264, MUTED);
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
