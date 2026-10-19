package com.replaymod.studio;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.SPTimeline;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StudioPaths extends StudioScreen {
    private final GuiScreen parent;
    private final ReplayHandler replay;
    private GuiTextField name;
    private List<String> names = new ArrayList<>();
    private String selected;
    private int offset;
    public StudioPaths(GuiScreen parent, ReplayHandler replay) { this.parent = parent; this.replay = replay; }
    private Map<String, Timeline> paths() throws IOException { return replay.getReplayFile().getTimelines(new SPTimeline()); }
    @Override protected void build() {
        name = field(520, 104, 350, name == null ? "Shot sequence" : name.getText());
        button(1, 520, 140, 350, "Save current sequence as new path");
        button(2, 520, 172, 350, "Load selected path");
        button(3, 520, 204, 350, "Replace selected with current sequence");
        button(7, 520, 244, 350, "Copy selected path to clipboard");
        button(8, 520, 276, 350, "Copy current sequence to clipboard");
        button(9, 520, 308, 350, "Import paths from clipboard");
        button(10, 520, 350, 170, "Delete selected"); button(11, 700, 350, 170, "Rename selected");
        button(4, 90, 452, 110, "Previous"); button(5, 210, 452, 110, "Next"); button(6, 760, 452, 110, "Back");
        try { synchronized (replay.getReplayFile()) { names = new ArrayList<>(paths().keySet()); names.remove(""); } }
        catch (IOException failure) { message = "Could not read saved paths: " + failure.getMessage(); }
        names.sort(String::compareToIgnoreCase);
        offset = Math.min(offset, Math.max(0, names.size() - 1));
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xB0101720);
        panel(70, 40, 820, 460, "SAVED CAMERA PATHS");
        label("Saved inside this recording. Your current sequence also autosaves.", 90, 68, MUTED);
        label("Path name", 520, 88, MUTED);
        for (int i = 0; i < 13 && offset + i < names.size(); i++) {
            String path = names.get(offset + i);
            drawRect(90, 98 + i * 24, 500, 120 + i * 24, path.equals(selected) ? 0xFF29425A : 0x801B2835);
            label(fontRendererObj.trimStringToWidth(path, 390), 100, 105 + i * 24, TEXT);
        }
    }
    @Override protected void clicked(int x, int y, int button) {
        int row = (y - 98) / 24 + offset;
        if (button == 0 && hit(x, y, 90, 98, 410, 312) && row < names.size()) { selected = names.get(row); name.setText(selected); }
    }
    @Override protected void activate(int id) {
        if (id == 6) { escape(); return; }
        if (id == 4) { offset = Math.max(0, offset - 13); return; }
        if (id == 5) { offset = Math.min(Math.max(0, names.size() - 1), offset + 13); return; }
        ReplayFile file = replay.getReplayFile();
        try { synchronized (file) {
            Map<String, Timeline> saved = paths();
            if (id == 7 || id == 8) {
                Timeline path = id == 8 ? ReplayModSimplePathing.instance.getCurrentTimeline().getTimeline() : saved.get(selected);
                if (path == null) throw new IllegalArgumentException("Select a saved path first.");
                String label = id == 8 ? "Camera sequence" : selected;
                net.minecraft.client.gui.GuiScreen.setClipboardString(new com.replaymod.replaystudio.pathing.serialize.TimelineSerialization(new SPTimeline(), null).serialize(java.util.Collections.singletonMap(label, path)));
                return;
            }
            if (id == 9) {
                String text = net.minecraft.client.gui.GuiScreen.getClipboardString();
                if (text.length() > 1024 * 1024) throw new IllegalArgumentException("Clipboard path data exceeds 1 MB.");
                Map<String, Timeline> imported;
                try { imported = new com.replaymod.replaystudio.pathing.serialize.TimelineSerialization(new SPTimeline(), null).deserialize(text); }
                catch (RuntimeException invalid) { throw new IllegalArgumentException("The clipboard does not contain valid camera-path JSON."); }
                if (imported.size() > 100) throw new IllegalArgumentException("Import at most 100 paths at a time.");
                for (Map.Entry<String, Timeline> entry : imported.entrySet()) {
                    String base = entry.getKey().isEmpty() ? "Imported path" : entry.getKey(), key = base;
                    for (int suffix = 2; saved.containsKey(key); suffix++) key = base + " (" + suffix + ")";
                    saved.put(key, entry.getValue());
                }
                file.writeTimelines(new SPTimeline(), saved); initGui(); return;
            }
            if (id == 10 || id == 11) {
                if (selected == null || !saved.containsKey(selected)) throw new IllegalArgumentException("Select a saved path first.");
                String renamed = name.getText().trim();
                if (id == 11 && (renamed.isEmpty() || (!renamed.equals(selected) && saved.containsKey(renamed)))) throw new IllegalArgumentException("Choose a new, nonempty path name.");
                Timeline path = saved.remove(selected);
                if (id == 11) { saved.put(renamed, path); selected = renamed; } else selected = null;
                file.writeTimelines(new SPTimeline(), saved); initGui(); return;
            }
            if (id == 2) {
                if (selected == null || !saved.containsKey(selected)) throw new IllegalArgumentException("Select a saved path first.");
                ReplayModSimplePathing.instance.setCurrentTimeline(new SPTimeline(saved.get(selected)));
                replay.getOverlay().resetStudioEditor();
                escape(); return;
            }
            String key = id == 3 ? selected : name.getText().trim();
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("Enter a path name.");
            if (id == 1 && saved.containsKey(key)) throw new IllegalArgumentException("That name already exists. Use Replace or choose a different name.");
            saved.put(key, ReplayModSimplePathing.instance.getCurrentTimeline().getTimeline());
            file.writeTimelines(new SPTimeline(), saved);
            selected = key;
            initGui();
        } } catch (IOException failure) { throw new IllegalStateException("Could not save camera paths: " + failure.getMessage(), failure); }
    }
    @Override protected void escape() { mc.displayGuiScreen(replay.getOverlay().getStudioEditor()); }
}
