package com.replaymod.studio;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replaystudio.data.Marker;
import net.minecraft.client.gui.GuiTextField;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StudioMarkers extends StudioScreen {
    private final ReplayHandler replay;
    private Marker selected;
    private List<Marker> markers;
    private GuiTextField name, time;
    private int offset;
    public StudioMarkers(ReplayHandler replay) { this.replay = replay; }
    @Override protected void build() {
        name = field(530, 132, 310, name != null ? name.getText() : selected == null || selected.getName() == null ? "Scene marker" : selected.getName());
        time = field(530, 190, 310, time != null ? time.getText() : StudioTime.format(selected == null ? replay.getReplaySender().currentTimeStamp() : selected.getTime()));
        button(1, 530, 232, 310, "Save marker at current camera");
        button(2, 530, 264, 310, "Update name / replay time");
        button(3, 530, 296, 310, "Go to selected marker");
        button(4, 530, 328, 310, "Delete selected marker");
        button(5, 530, 370, 310, "New marker");
        button(6, 110, 450, 110, "Previous"); button(7, 230, 450, 110, "Next"); button(8, 700, 450, 140, "Back");
        markers = new ArrayList<>(replay.getOverlay().timeline.getStudioMarkers()); markers.sort(Comparator.comparingInt(Marker::getTime));
        offset = Math.min(offset, Math.max(0, markers.size() - 1));
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xB0101720); panel(90, 40, 770, 460, "SCENE MARKERS");
        label("Bookmark an event or camera view. Markers do not change the cinematic path.", 110, 76, MUTED);
        label("Name", 530, 114, MUTED); label("Replay time", 530, 172, MUTED);
        for (int i = 0; i < 13 && offset + i < markers.size(); i++) {
            Marker marker = markers.get(offset + i);
            drawRect(110, 104 + i * 24, 510, 126 + i * 24, marker.equals(selected) ? 0xFF29425A : 0x801B2835);
            label(fontRendererObj.trimStringToWidth(StudioTime.format(marker.getTime()) + "   " + (marker.getName() == null ? "Marker" : marker.getName()), 380), 120, 111 + i * 24, TEXT);
        }
    }
    @Override protected void clicked(int x, int y, int button) {
        if (button == 0 && hit(x, y, 110, 104, 400, 312)) {
            int index = offset + (y - 104) / 24;
            if (index < markers.size()) { selected = markers.get(index); name.setText(selected.getName() == null ? "" : selected.getName()); time.setText(StudioTime.format(selected.getTime())); }
        }
    }
    @Override protected void activate(int id) {
        if (id == 6) { offset = Math.max(0, offset - 13); return; }
        if (id == 7) { offset = Math.min(Math.max(0, markers.size() - 1), offset + 13); return; }
        if (id == 8) { escape(); return; }
        if (id == 5) { selected = null; name = null; time = null; initGui(); return; }
        if (id == 3) {
            if (selected == null) throw new IllegalArgumentException("Select a marker first.");
            CinematicEditor editor = replay.getOverlay().getStudioEditor();
            mc.displayGuiScreen(editor); editor.goToMarker(selected); return;
        }
        if (id == 2 || id == 4) if (selected == null) throw new IllegalArgumentException("Select a marker first.");
        Marker replacement = null;
        if (id != 4) {
            long stamp = StudioTime.parse(time.getText());
            if (stamp > replay.getReplayDuration()) throw new IllegalArgumentException("Marker time is outside this recording.");
            replacement = new Marker(); replacement.setTime((int) stamp); replacement.setName(name.getText().trim());
            CameraEntity camera = replay.getCameraEntity();
            replacement.setX(id == 2 ? selected.getX() : camera.posX); replacement.setY(id == 2 ? selected.getY() : camera.posY); replacement.setZ(id == 2 ? selected.getZ() : camera.posZ);
            replacement.setYaw(id == 2 ? selected.getYaw() : camera.rotationYaw); replacement.setPitch(id == 2 ? selected.getPitch() : camera.rotationPitch); replacement.setRoll(id == 2 ? selected.getRoll() : camera.roll);
        }
        try { replay.getOverlay().timeline.updateStudioMarker(selected, replacement); selected = replacement; initGui(); }
        catch (IOException failure) { throw new IllegalStateException("Could not save markers: " + failure.getMessage(), failure); }
    }
    @Override protected void escape() { mc.displayGuiScreen(replay.getOverlay().getStudioEditor()); }
}
