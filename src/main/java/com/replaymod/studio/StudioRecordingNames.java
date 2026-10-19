package com.replaymod.studio;

import com.replaymod.core.utils.Utils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class StudioRecordingNames extends StudioScreen {
    private final GuiScreen parent;
    private final List<Path> recordings;
    private final BiConsumer<Path, String> rename;
    private final Map<Path, String> drafts = new LinkedHashMap<>();
    private int offset;
    private final java.util.Set<Path> completed = new java.util.HashSet<>();
    public StudioRecordingNames(GuiScreen parent, List<Path> recordings, BiConsumer<Path, String> rename) {
        this.parent = parent; this.recordings = recordings; this.rename = rename;
        for (Path path : recordings) drafts.put(path, Utils.fileNameToReplayName(path.getFileName().toString()));
    }
    @Override protected void build() {
        for (int i = 0; i < 10 && offset + i < recordings.size(); i++) field(120, 118 + i * 26, 720, drafts.get(recordings.get(offset + i)));
        button(1, 120, 430, 110, "Previous"); button(2, 240, 430, 110, "Next");
        button(3, 370, 430, 220, "Keep existing filenames"); button(4, 610, 430, 230, "Use these names");
    }
    private void remember() { for (int i = 0; i < fields.size(); i++) drafts.put(recordings.get(offset + i), fields.get(i).getText().trim()); }
    @Override protected void beforeResize() { remember(); }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xC0101720); panel(90, 40, 780, 450, "RECORDING SAVED");
        label("Give your recordings useful names, or keep their existing filenames.", 120, 78, MUTED);
        label("Every recording is already saved. Escape keeps the existing filenames.", 120, 98, MUTED);
    }
    @Override protected void activate(int id) {
        remember();
        if (id == 1) { offset = Math.max(0, offset - 10); initGui(); }
        if (id == 2) { offset = Math.min(Math.max(0, recordings.size() - 1), offset + 10); initGui(); }
        if (id == 3) escape();
        if (id == 4) {
            for (String text : drafts.values()) if (text.isEmpty() || text.contains("/") || text.contains("\\")) throw new IllegalArgumentException("Use nonempty recording names without folder separators.");
            for (Map.Entry<Path, String> entry : drafts.entrySet()) if (!completed.contains(entry.getKey())) {
                rename.accept(entry.getKey(), entry.getValue()); completed.add(entry.getKey());
            }
            mc.displayGuiScreen(new ReplayLibrary(null));
        }
    }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
}
