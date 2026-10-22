package com.replaymod.studio;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ReplayLibrary extends StudioScreen {
    private final GuiScreen parent;
    private File folder, selected;
    private List<File> files = Collections.emptyList();
    private GuiTextField filter, name;
    private String query = "";
    private int offset;
    private int actionLeft, listWidth, rows, footer;
    public ReplayLibrary(GuiScreen parent) { this.parent = parent; }
    @Override protected StudioViewport createViewport() { return new StudioViewport(width, height, 960, 540, true); }
    @Override protected void beforeResize() { if (filter != null) query = filter.getText(); }
    @Override protected void build() {
        actionLeft = canvasWidth - 326; listWidth = actionLeft - 52;
        footer = canvasHeight - 72; rows = Math.max(1, (canvasHeight - 216) / 24);
        if (folder == null) {
            try { folder = ReplayMod.instance.getReplayFolder().toFile(); }
            catch (IOException failure) { message = failure.getMessage(); return; }
        }
        filter = field(28, 78, canvasWidth - 398, query);
        name = field(actionLeft, 126, 298, selected == null ? "" : selected.getName());
        button(1, canvasWidth - 358, 77, 100, "Search"); button(2, canvasWidth - 248, 77, 100, "Up folder");
        button(3, canvasWidth - 138, 77, 110, "Refresh");
        button(4, actionLeft, 164, 298, "Open replay / folder");
        button(5, actionLeft, 194, 298, "Rename selected");
        button(6, actionLeft, 224, 298, "Open recordings folder");
        button(7, actionLeft, 254, 298, "Replay settings");
        button(11, actionLeft, 294, 298, "Trim / split into edited copy");
        button(12, actionLeft, 324, 298, "Delete selected recording...");
        button(13, actionLeft, 354, 298, "Distant Horizons terrain source...");
        button(8, 28, footer, 100, "Previous"); button(9, 138, footer, 100, "Next");
        button(10, canvasWidth - 128, footer, 100, "Back");
        refresh();
    }
    private void refresh() {
        query = filter.getText();
        File[] entries = folder.listFiles(file -> file.isDirectory() || file.getName().toLowerCase(Locale.ROOT).endsWith(".mcpr"));
        files = entries == null ? Collections.emptyList() : Arrays.stream(entries)
                .filter(file -> file.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .sorted((a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : Long.compare(b.lastModified(), a.lastModified()))
                .collect(Collectors.toList());
        offset = Math.min(offset, Math.max(0, files.size() - 1));
    }
    public void refreshFiles() { if (filter != null) refresh(); }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, canvasWidth, canvasHeight, 0xFF101720);
        panel(16, 16, canvasWidth - 32, canvasHeight - 32, "");
        label("REPLAY LIBRARY", 28, 26, TEXT);
        label("Choose a recording, then create your cinematic shots.", 28, 44, MUTED);
        label("Search recordings", 28, 64, MUTED);
        label("Selected recording / new name", actionLeft, 110, MUTED);
        label(fontRendererObj.trimStringToWidth(folder == null ? "No replay folder" : folder.getAbsolutePath(), canvasWidth - 56), 28, footer - 24, MUTED);
        for (int i = 0; i < rows && offset + i < files.size(); i++) {
            File file = files.get(offset + i);
            int top = 112 + i * 24;
            drawRect(28, top, 28 + listWidth, top + 22, file.equals(selected) ? 0xFF29425A : 0x801B2835);
            label(fontRendererObj.trimStringToWidth((file.isDirectory() ? "[Folder] " : "") + file.getName(), listWidth - 24), 40, top + 7, TEXT);
        }
        if (files.isEmpty()) label("No recordings match. Make a recording first or choose another folder.", 40, 120, MUTED);
    }
    @Override protected void clicked(int x, int y, int button) {
        if (button != 0 || !hit(x, y, 28, 112, listWidth, rows * 24)) return;
        int index = offset + (y - 112) / 24;
        if (index < files.size()) { selected = files.get(index); name.setText(selected.getName()); }
    }
    @Override protected void activate(int id) {
        switch (id) {
            case 1: offset = 0; refresh(); break;
            case 2: if (folder.getParentFile() != null) { folder = folder.getParentFile(); selected = null; offset = 0; refresh(); } break;
            case 3: refresh(); break;
            case 4:
                if (selected == null) throw new IllegalArgumentException("Select a recording first.");
                if (selected.isDirectory()) { folder = selected; selected = null; offset = 0; refresh(); break; }
                if (ReplayModReplay.instance.getReplayHandler() != null && !com.replaymod.render.ReplayModRender.instance.getRenderQueue().isEmpty()) throw new IllegalArgumentException("Finish or remove queued exports before changing recordings.");
                try {
                    if (ReplayModReplay.instance.getReplayHandler() != null) ReplayModReplay.instance.getReplayHandler().endReplay();
                    if (com.replaymod.replay.compat.DistantHorizonsReplay.available()) {
                        com.replaymod.replaystudio.replay.ReplayFile checking = ReplayMod.instance.openReplay(selected.toPath());
                        boolean needsSource;
                        try { needsSource = com.replaymod.replay.compat.DistantHorizonsReplay.read(checking).isEmpty(); }
                        finally { checking.close(); }
                        if (needsSource) { mc.displayGuiScreen(new StudioLodSources(this, selected)); break; }
                    }
                    ReplayModReplay.instance.startReplay(selected);
                } catch (IOException failure) { throw new IllegalStateException("Could not open replay: " + failure.getMessage(), failure); }
                break;
            case 5:
                if (selected == null) throw new IllegalArgumentException("Select a recording first.");
                if (ReplayModReplay.instance.getReplayHandler() != null) throw new IllegalArgumentException("Save and exit the current replay before renaming recordings.");
                String newName = name.getText().trim();
                if (newName.isEmpty() || newName.contains("/") || newName.contains("\\") || newName.equals(".") || newName.equals("..")) throw new IllegalArgumentException("Enter a filename without folder separators.");
                if (!selected.isDirectory() && !newName.endsWith(".mcpr")) newName += ".mcpr";
                try { Files.move(selected.toPath(), new File(folder, newName).toPath()); }
                catch (IOException failure) { throw new IllegalArgumentException("Rename failed: " + failure.getMessage()); }
                selected = null; refresh(); break;
            case 6: MCVer.openFile(folder); break;
            case 7: mc.displayGuiScreen(new StudioSettings(this, ReplayMod.instance.getSettingsRegistry())); break;
            case 8: offset = Math.max(0, offset - rows); break;
            case 9: offset = Math.min(Math.max(0, files.size() - 1), offset + rows); break;
            case 10: escape(); break;
            case 11:
                if (selected == null || !selected.isFile()) throw new IllegalArgumentException("Select a recording first.");
                if (ReplayModReplay.instance.getReplayHandler() != null) throw new IllegalArgumentException("Save and exit the current replay before trimming recordings.");
                try { mc.displayGuiScreen(new StudioReplayTrim(this, selected)); }
                catch (IOException failure) { throw new IllegalStateException("Could not read recording: " + failure.getMessage(), failure); }
                break;
            case 12:
                if (selected == null || !selected.isFile()) throw new IllegalArgumentException("Select a recording first.");
                if (ReplayModReplay.instance.getReplayHandler() != null) throw new IllegalArgumentException("Save and exit the current replay before deleting recordings.");
                File deleting = selected;
                mc.displayGuiScreen(new StudioConfirm(this, "DELETE RECORDING", Arrays.asList("Permanently delete this recording?", deleting.getName()), "Delete recording", () -> {
                    try { Files.delete(deleting.toPath()); selected = null; refresh(); }
                    catch (IOException failure) { message = "Could not delete recording: " + failure.getMessage(); }
                }));
                break;
            case 13:
                if (selected == null || !selected.isFile()) throw new IllegalArgumentException("Select a recording first.");
                if (ReplayModReplay.instance.getReplayHandler() != null) throw new IllegalArgumentException("Save and exit the replay before changing its LOD terrain source.");
                if (!com.replaymod.replay.compat.DistantHorizonsReplay.available()) throw new IllegalArgumentException("Distant Horizons is not installed.");
                try { mc.displayGuiScreen(new StudioLodSources(this, selected)); }
                catch (IOException error) { throw new IllegalStateException(error.getMessage(), error); }
                break;
            default: break;
        }
    }
    @Override protected void submit() { activate(filter.isFocused() ? 1 : 4); }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
}
