package com.replaymod.studio;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Native file selection: no Swing window, render-thread blocking dialog or hidden launcher window. */
public final class StudioFilePicker extends StudioScreen {
    private final GuiScreen parent;
    private final Consumer<File> accept;
    private File directory;
    private GuiTextField location, filename;
    private String filenameDraft, locationDraft;
    private List<File> entries = java.util.Collections.emptyList();
    private int offset;
    public StudioFilePicker(GuiScreen parent, File file, Consumer<File> accept) {
        this.parent = parent; this.accept = accept;
        directory = file.isDirectory() ? file.getAbsoluteFile() : file.getAbsoluteFile().getParentFile();
        filenameDraft = file.isDirectory() ? "video.mp4" : file.getName();
    }
    @Override protected StudioViewport createViewport() { return new StudioViewport(width, height, 80, 30, 800, 480, true); }
    @Override protected void drawBackdrop(int x, int y, float partialTicks) { drawRect(0, 0, width, height, 0xFF111B24); }
    @Override protected void build() {
        location = field(100, 80, 640, directory.getAbsolutePath());
        filename = field(100, 426, 640, filenameDraft);
        button(1, 752, 79, 108, "Go"); button(2, 752, 112, 108, "Up folder");
        button(3, 100, 464, 108, "Previous"); button(4, 218, 464, 108, "Next");
        button(5, 632, 464, 108, "Cancel"); button(6, 752, 464, 108, "Choose file");
        attempt(this::refresh);
        if (locationDraft != null) location.setText(locationDraft);
    }
    private void refresh() {
        File[] list = directory.listFiles();
        if (list == null) { entries = java.util.Collections.emptyList(); throw new IllegalArgumentException("Folder cannot be read. Type an existing folder path."); }
        entries = Arrays.stream(list).sorted((a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName())).collect(Collectors.toList());
        location.setText(directory.getAbsolutePath()); locationDraft = null; offset = 0;
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        panel(80, 30, canvasWidth - 80, canvasHeight - 30, "CHOOSE OUTPUT / EXECUTABLE");
        label("Folder path (you can type a drive or network path)", 100, 62, MUTED);
        label("Filename", 100, 410, MUTED);
        for (int i = 0; entries != null && i < 11 && offset + i < entries.size(); i++) {
            File entry = entries.get(offset + i);
            drawRect(100, 110 + i * 25, 740, 133 + i * 25, 0x801B2835);
            label(fontRendererObj.trimStringToWidth((entry.isDirectory() ? "[Folder] " : "") + entry.getName(), 620), 110, 118 + i * 25, TEXT);
        }
    }
    @Override protected void clicked(int x, int y, int button) {
        if (button != 0 || entries == null || !hit(x, y, 100, 110, 640, 275)) return;
        int row = offset + (y - 110) / 25;
        if (row >= entries.size()) return;
        File entry = entries.get(row);
        if (entry.isDirectory()) { directory = entry; refresh(); }
        else filename.setText(entry.getName());
    }
    @Override protected void activate(int id) {
        switch (id) {
            case 1: directory = new File(location.getText()); refresh(); break;
            case 2: if (directory.getParentFile() != null) { directory = directory.getParentFile(); refresh(); } break;
            case 3: offset = Math.max(0, offset - 11); break;
            case 4: offset = Math.min(Math.max(0, entries.size() - 1), offset + 11); break;
            case 5: escape(); break;
            case 6:
                String name = filename.getText().trim();
                if (name.isEmpty()) throw new IllegalArgumentException("Enter a filename.");
                accept.accept(new File(directory, name).getAbsoluteFile()); escape(); break;
            default: break;
        }
    }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
    @Override protected void beforeResize() { filenameDraft = filename.getText(); locationDraft = location.getText(); }
    @Override protected void submit() { activate(location.isFocused() ? 1 : 6); }
}
