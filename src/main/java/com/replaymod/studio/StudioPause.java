package com.replaymod.studio;

import com.replaymod.core.ReplayMod;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import java.io.IOException;

public final class StudioPause extends StudioScreen {
    private final ReplayHandler replay;
    public StudioPause(ReplayHandler replay) {
        this.replay = replay;
        ReplayModSimplePathing.instance.getGuiPathing().stopPreview();
        replay.getReplaySender().setReplaySpeed(0);
    }
    @Override protected void build() {
        button(1, 320, 192, 320, "Cinematic editor [T]");
        button(2, 320, 218, 320, "Fly camera");
        button(3, 320, 244, 320, "Replay library");
        button(4, 320, 270, 320, "Replay settings");
        button(5, 320, 306, 320, "Save and exit replay");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        panel(304, 154, 352, 202, "REPLAY PAUSED");
        label("Camera paths save inside the recording.", 320, 340, MUTED);
    }
    @Override protected void activate(int id) {
        if (id == 1) mc.displayGuiScreen(replay.getOverlay().getStudioEditor());
        if (id == 2) escape();
        if (id == 3) mc.displayGuiScreen(new ReplayLibrary(this));
        if (id == 4) mc.displayGuiScreen(new StudioSettings(this, ReplayMod.instance.getSettingsRegistry()));
        if (id == 5) {
            try { replay.endReplay(); mc.displayGuiScreen(new ReplayLibrary(null)); }
            catch (IOException failure) { throw new IllegalStateException("Could not close replay: " + failure.getMessage(), failure); }
        }
    }
}
