package com.replaymod.studio;

import com.google.gson.Gson;
import com.replaymod.core.ReplayMod;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.compat.DistantHorizonsReplay;
import com.replaymod.replay.compat.LodCacheFolders;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit cache selection for recordings made before LOD source metadata existed. */
public final class StudioLodSources extends StudioScreen {
    private final ReplayLibrary parent;
    private final File recording;
    private final List<File> candidates = new ArrayList<>();
    private final Map<Integer, String> selected;
    private int offset;
    public StudioLodSources(ReplayLibrary parent, File recording) throws IOException {
        this.parent = parent; this.recording = recording;
        ReplayFile file = ReplayMod.instance.openReplay(recording.toPath());
        try { selected = DistantHorizonsReplay.read(file); } finally { file.close(); }
        File root = new File(ReplayMod.instance.getMinecraft().mcDataDir, "Distant_Horizons_server_data");
        candidates.addAll(LodCacheFolders.candidates(root));
    }
    public static Integer dimension(File folder) {
        return LodCacheFolders.dimension(folder);
    }
    @Override protected void build() {
        button(1, 36, 450, 120, "Previous"); button(2, 166, 450, 120, "Next");
        button(3, 36, 488, 180, "Back to library"); button(4, 594, 488, 330, "Save LOD source + open replay");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xFF101720); panel(16, 16, 928, 504, "DISTANT HORIZONS - recording terrain source");
        label(recording.getName(), 36, 48, TEXT);
        label("Choose the real world's LOD cache for each dimension. Never combine caches from different worlds.", 36, 72, MUTED);
        label("The replay reads this cache. Terrain updates are disabled; cached LODs are not embedded in the recording.", 36, 90, MUTED);
        for (int row = 0; row < 10 && offset + row < candidates.size(); row++) {
            File folder = candidates.get(offset + row); int top = 122 + row * 30;
            boolean chosen = folder.getPath().equals(new File(mc.mcDataDir, selected.getOrDefault(dimension(folder), "")).getPath());
            drawRect(36, top, 924, top + 28, chosen ? 0xFF29425A : 0xFF1B2835);
            String title = "Dim " + dimension(folder) + " | " + folder.getParentFile().getName() + " / " + folder.getName();
            label(fontRendererObj.trimStringToWidth((chosen ? "[Selected] " : "") + title, 860), 44, top + 3, TEXT);
            File db = new File(folder, "DistantHorizons.sqlite");
            label(String.format(Locale.ROOT, "%,d MB | Modified %s", db.length() / 1024 / 1024, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(db.lastModified()))), 44, top + 16, MUTED);
        }
        if (candidates.isEmpty()) label("No server LOD caches found. Visit the world with Distant Horizons enabled first.", 36, 140, TEXT);
        label("If several world IDs exist, choose the one for the recorded world. File size alone does not identify a world.", 300, 456, MUTED);
    }
    @Override protected void clicked(int x, int y, int button) {
        if (button != 0 || !hit(x, y, 36, 122, 888, 300)) return;
        int index = offset + (y - 122) / 30;
        if (index < candidates.size()) {
            File folder = candidates.get(index);
            try { selected.put(dimension(folder), DistantHorizonsReplay.relativeFolder(folder)); }
            catch (IOException error) { throw new IllegalArgumentException(error.getMessage()); }
        }
    }
    @Override protected void activate(int id) {
        if (id == 1) offset = Math.max(0, offset - 10);
        if (id == 2) offset = Math.min(Math.max(0, candidates.size() - 10), offset + 10);
        if (id == 3) escape();
        if (id == 4) {
            if (selected.isEmpty()) throw new IllegalArgumentException("Select the recorded world's LOD cache before opening.");
            try {
                for (String relative : selected.values()) DistantHorizonsReplay.resolve(relative);
                ReplayFile file = ReplayMod.instance.openReplay(recording.toPath());
                try {
                    try (OutputStream out = file.write(DistantHorizonsReplay.MANIFEST)) { out.write(new Gson().toJson(selected).getBytes(StandardCharsets.UTF_8)); }
                    file.save();
                } finally { file.close(); }
                ReplayModReplay.instance.startReplay(recording);
            } catch (IOException error) { throw new IllegalStateException("Could not save/open LOD source: " + error.getMessage(), error); }
        }
    }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
}
