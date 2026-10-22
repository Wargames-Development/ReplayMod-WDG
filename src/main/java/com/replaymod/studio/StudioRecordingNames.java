package com.replaymod.studio;

import com.replaymod.core.utils.Utils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.Minecraft;
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
    private int offset, left, top, popupHeight, rows, listTop;
    private boolean scrolling;
    private final java.util.Set<Path> deleted = new java.util.HashSet<>();
    private final java.util.Set<Path> completed = new java.util.HashSet<>();
    public StudioRecordingNames(GuiScreen parent, List<Path> recordings, BiConsumer<Path, String> rename) {
        this.parent = parent; this.recordings = new java.util.ArrayList<>(recordings); this.rename = rename;
        for (Path path : recordings) drafts.put(path, defaultName(path));
    }
    private String defaultName(Path path) { return Utils.fileNameToReplayName(path.getFileName().toString()); }
    @Override protected StudioViewport createViewport() { return new StudioViewport(width, height, 960, 540, true); }
    public void addRecordings(List<Path> paths) {
        remember();
        for (Path path : paths) if (!drafts.containsKey(path)) { recordings.add(path); drafts.put(path, defaultName(path)); }
        initGui();
    }
    @Override protected void build() {
        left = canvasWidth - 352; top = 12;
        rows = Math.min(recordings.size(), Math.max(1, (canvasHeight - 116) / 32));
        popupHeight = 92 + rows * 32; listTop = top + 48;
        offset = Math.max(0, Math.min(offset, recordings.size() - rows));
        for (int i = 0; i < rows; i++) {
            Path path = recordings.get(offset + i);
            GuiTextField field = field(left + 38, listTop + i * 32 + 4, 248, drafts.get(path));
            field.setEnabled(!deleted.contains(path) && !completed.contains(path));
            GuiButton trash = new TrashButton(100 + offset + i, left + 294, listTop + i * 32 + 3, deleted.contains(path));
            trash.enabled = !completed.contains(path); buttonList.add(trash);
        }
        button(4, left + 12, top + popupHeight - 32, 316, "Save all");
    }
    private void remember() { for (int i = 0; i < fields.size(); i++) drafts.put(recordings.get(offset + i), fields.get(i).getText().trim()); }
    @Override protected void beforeResize() { remember(); }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        panel(left, top, 340, popupHeight, "RECORDING SAVED");
        label("Rename below. Trash marks deletion on Save all.", left + 12, top + 30, MUTED);
        for (int i = 0; i < rows; i++) label(Integer.toString(offset + i + 1), left + 12, listTop + i * 32 + 9, ACCENT);
        if (recordings.size() > rows) {
            int height = rows * 32, thumb = Math.max(16, height * rows / recordings.size());
            int thumbTop = listTop + (height - thumb) * offset / Math.max(1, recordings.size() - rows);
            drawRect(left + 328, listTop, left + 334, listTop + height, EDGE);
            drawRect(left + 328, thumbTop, left + 334, thumbTop + thumb, ACCENT);
        }
    }
    @Override protected void drawBackdrop(int x, int y, float partialTicks) {
        if (parent != null) {
            if (parent.width != width || parent.height != height) parent.setWorldAndResolution(mc, width, height);
            parent.drawScreen(x, y, partialTicks);
        }
    }
    @Override protected void drawMessage() {
        if (message.isEmpty()) return;
        List<String> lines = fontRendererObj.listFormattedStringToWidth(message, 316);
        int count = Math.min(5, lines.size()), y = Math.min(canvasHeight - count * 11 - 12, top + popupHeight + 6);
        drawRect(left, y - 4, left + 340, y + count * 11 + 4, 0xFF111B24);
        for (int i = 0; i < count; i++) label(lines.get(i), left + 12, y + i * 11, 0xFFFFCB82);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        int lx = logicalX(x), ly = logicalY(y), select = -1;
        if (button == 0) for (int i = 0; i < fields.size(); i++) {
            GuiTextField field = fields.get(i);
            if (!field.isFocused() && hit(lx, ly, field.xPosition, field.yPosition, field.width, field.height)
                    && field.getText().equals(defaultName(recordings.get(offset + i)))) select = i;
        }
        super.mouseClicked(x, y, button);
        if (select >= 0 && select < fields.size()) {
            GuiTextField field = fields.get(select); field.setCursorPositionEnd(); field.setSelectionPos(0);
        }
    }
    @Override protected void scrolled(int x, int y, int amount) {
        if (hit(x, y, left, listTop, 340, rows * 32)) scrollTo(offset + (amount > 0 ? -1 : 1));
    }
    private void scrollTo(int next) {
        remember(); offset = Math.max(0, Math.min(next, recordings.size() - rows)); initGui();
    }
    private void scrollAt(int y) {
        int height = rows * 32, thumb = Math.max(16, height * rows / Math.max(1, recordings.size()));
        double fraction = (y - listTop - thumb / 2.0) / Math.max(1, height - thumb);
        scrollTo((int) Math.round(fraction * (recordings.size() - rows)));
    }
    @Override protected void clicked(int x, int y, int button) {
        if (button == 0 && recordings.size() > rows && hit(x, y, left + 326, listTop, 10, rows * 32)) { scrolling = true; scrollAt(y); }
    }
    @Override protected void dragged(int x, int y, int button) { if (scrolling) scrollAt(y); }
    @Override protected void released(int x, int y, int button) { scrolling = false; }
    @Override protected void cancelledDrag() { scrolling = false; }
    void saveAll() {
        remember();
        for (Map.Entry<Path, String> entry : drafts.entrySet()) if (!deleted.contains(entry.getKey())) {
            String text = entry.getValue();
            if (text.isEmpty() || text.contains("/") || text.contains("\\")) throw new IllegalArgumentException("Use nonempty recording names without folder separators, or use the trash icon.");
        }
        for (Map.Entry<Path, String> entry : drafts.entrySet()) if (!completed.contains(entry.getKey())) {
            rename.accept(entry.getKey(), deleted.contains(entry.getKey()) ? "" : entry.getValue()); completed.add(entry.getKey());
        }
    }
    @Override protected void activate(int id) {
        remember();
        if (id >= 100) {
            Path path = recordings.get(id - 100);
            if (!completed.contains(path) && !deleted.remove(path)) deleted.add(path);
            initGui();
        }
        if (id == 4) {
            saveAll(); escape();
        }
    }
    @Override protected void submit() { activate(4); }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
    private static final class TrashButton extends GuiButton {
        private final boolean deleting;
        TrashButton(int id, int x, int y, boolean deleting) { super(id, x, y, 22, 20, deleting ? "Restore recording" : "Delete recording on save"); this.deleting = deleting; }
        @Override public void drawButton(Minecraft mc, int x, int y) {
            int color = enabled ? deleting ? 0xFFE46B67 : 0xFF9A9A9A : EDGE;
            boolean hover = x >= xPosition && x < xPosition + width && y >= yPosition && y < yPosition + height;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, hover ? EDGE : 0xFF1B2835);
            int cx = xPosition + 7, cy = yPosition + 6;
            drawRect(cx + 2, cy - 2, cx + 6, cy - 1, color);
            drawRect(cx - 1, cy, cx + 9, cy + 1, color);
            drawRect(cx, cy + 2, cx + 1, cy + 9, color); drawRect(cx + 7, cy + 2, cx + 8, cy + 9, color);
            drawRect(cx, cy + 9, cx + 8, cy + 10, color);
        }
    }
}
