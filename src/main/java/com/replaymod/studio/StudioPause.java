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
        button(1, 300, 140, 360, "Cinematic editor [T]");
        button(2, 300, 174, 360, "Fly camera");
        button(3, 300, 208, 360, "Replay library");
        button(4, 300, 242, 360, "Replay settings");
        button(5, 300, 300, 360, "Save and exit replay");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0x90101720); panel(270, 92, 420, 270, "REPLAY PAUSED");
        label("Your camera path autosaves inside this recording.", 300, 342, MUTED);
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
