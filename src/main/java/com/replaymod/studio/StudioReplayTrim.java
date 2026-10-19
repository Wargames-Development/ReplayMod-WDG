package com.replaymod.studio;

import com.replaymod.core.ReplayMod;
import com.replaymod.editor.gui.MarkerProcessor;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.replay.ReplayFile;
import net.minecraft.client.gui.GuiTextField;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Native controls for ReplayMod's existing packet-level trim/split processor. */
public final class StudioReplayTrim extends StudioScreen {
    private final ReplayLibrary parent;
    private final File input;
    private final Set<Marker> markers;
    private final int recordingDuration;
    private GuiTextField time, output;
    private String timeDraft = "0:00.000", outputDraft;
    private long cursor;
    private int offset;
    private Marker selected;
    private volatile boolean working;
    private volatile float progress;

    public StudioReplayTrim(ReplayLibrary parent, File input) throws IOException {
        this.parent = parent; this.input = input;
        try (ReplayFile replay = ReplayMod.instance.openReplay(input.toPath())) {
            markers = new HashSet<>(replay.getMarkers().or(HashSet::new));
            recordingDuration = replay.getMetaData().getDuration();
        }
        String name = input.getName().replaceFirst("(?i)\\.mcpr$", "") + "-edited";
        File candidate = new File(input.getParentFile(), name + ".mcpr");
        for (int i = 2; candidate.exists(); i++) candidate = new File(input.getParentFile(), name + "-" + i + ".mcpr");
        outputDraft = candidate.getAbsolutePath();
    }
    private List<Marker> edits() {
        List<Marker> list = new ArrayList<>();
        for (Marker marker : markers) if (marker.getName() != null && marker.getName().startsWith("_RM_")) list.add(marker);
        list.sort(Comparator.comparingInt(Marker::getTime));
        return list;
    }
    @Override protected void build() {
        time = field(120, 112, 190, timeDraft); button(1, 320, 111, 100, "Go to time");
        button(2, 120, 196, 190, "Split recording here");
        button(3, 320, 196, 190, "Start removed section");
        button(4, 520, 196, 190, "End removed section");
        button(5, 720, 196, 120, "Remove edit");
        output = field(120, 392, 600, outputDraft); button(6, 730, 391, 110, "Browse...");
        button(7, 120, 438, 110, "Previous"); button(8, 240, 438, 110, "Next");
        button(9, 450, 438, 180, "Back"); button(10, 640, 438, 200, "Create edited copy");
    }
    private void remember() { timeDraft = time.getText(); outputDraft = output.getText(); }
    @Override protected void beforeResize() { remember(); }
    @Override public void onGuiClosed() { remember(); super.onGuiClosed(); }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xC0101720); panel(90, 32, 780, 468, "TRIM / SPLIT RECORDING");
        label("Create a separate replay file. Your source recording stays unchanged.", 120, 66, TEXT);
        label("Replay time (click or drag the timeline, or type an exact time)", 120, 94, MUTED);
        drawRect(120, 148, 840, 180, 0xFF1B2835);
        for (int i = 0; i <= 6; i++) label(StudioTime.format((long) recordingDuration * i / 6), Math.min(120 + i * 120, 786), 151, MUTED);
        for (Marker marker : edits()) {
            int px = 120 + (int) ((long) marker.getTime() * 720 / Math.max(1, recordingDuration));
            drawRect(px, 165, px + 2, 179, marker == selected ? TEXT : ACCENT);
        }
        int head = 120 + (int) (cursor * 720 / Math.max(1, recordingDuration));
        drawRect(head, 148, head + 2, 180, 0xFFFFCB82);
        List<Marker> list = edits();
        for (int i = 0; i < 6 && offset + i < list.size(); i++) {
            Marker marker = list.get(offset + i);
            int top = 228 + i * 22;
            drawRect(120, top, 840, top + 20, marker == selected ? 0xFF29425A : 0x801B2835);
            String operation = MarkerProcessor.MARKER_NAME_SPLIT.equals(marker.getName()) ? "Split" : MarkerProcessor.MARKER_NAME_START_CUT.equals(marker.getName()) ? "Start removed section" : "End removed section";
            label(StudioTime.format(marker.getTime()) + "   " + operation, 130, top + 6, TEXT);
        }
        label("Output replay filename", 120, 374, MUTED);
        if (working) label("Processing edited copy: " + Math.round(progress * 100) + "%", 120, 477, ACCENT);
        for (Object value : buttonList) ((net.minecraft.client.gui.GuiButton) value).enabled = !working;
    }
    private void scrub(int x) {
        cursor = Math.round(Math.max(0, Math.min(720, x - 120)) * recordingDuration / 720.0);
        time.setText(StudioTime.format(cursor));
    }
    @Override protected void clicked(int x, int y, int button) {
        if (working || button != 0) return;
        if (hit(x, y, 120, 148, 720, 32)) { scrub(x); return; }
        List<Marker> list = edits();
        int index = offset + (y - 228) / 22;
        if (hit(x, y, 120, 228, 720, 132) && index < list.size()) { selected = list.get(index); cursor = selected.getTime(); time.setText(StudioTime.format(cursor)); }
    }
    @Override protected void dragged(int x, int y, int button) { if (!working && button == 0 && hit(x, y, 120, 148, 720, 32)) scrub(x); }
    @Override protected void activate(int id) {
        if (working) return;
        remember();
        if (id >= 1 && id <= 4) {
            cursor = StudioTime.parse(timeDraft);
            if (cursor > recordingDuration) throw new IllegalArgumentException("Choose a time inside this recording.");
            if (id != 1) {
                String name = id == 2 ? MarkerProcessor.MARKER_NAME_SPLIT : id == 3 ? MarkerProcessor.MARKER_NAME_START_CUT : MarkerProcessor.MARKER_NAME_END_CUT;
                for (Marker marker : edits()) if (marker.getTime() == cursor) throw new IllegalArgumentException("There is already an edit at that time. Remove it before adding a replacement.");
                Marker marker = new Marker(); marker.setTime((int) cursor); marker.setName(name); markers.add(marker); selected = marker;
            }
        }
        if (id == 5) { markers.remove(selected); selected = null; offset = Math.min(offset, Math.max(0, edits().size() - 1)); }
        if (id == 6) mc.displayGuiScreen(new StudioFilePicker(this, new File(outputDraft), file -> { outputDraft = file.getAbsolutePath(); output.setText(outputDraft); }));
        if (id == 7) offset = Math.max(0, offset - 6);
        if (id == 8) offset = Math.min(Math.max(0, edits().size() - 1), offset + 6);
        if (id == 9) escape();
        if (id == 10) process();
    }
    private void process() {
        if (edits().isEmpty()) throw new IllegalArgumentException("Add a split or removed section first.");
        boolean cutting = false;
        for (Marker marker : edits()) {
            if (MarkerProcessor.MARKER_NAME_START_CUT.equals(marker.getName())) {
                if (cutting) throw new IllegalArgumentException("End the removed section before starting another.");
                cutting = true;
            } else if (MarkerProcessor.MARKER_NAME_END_CUT.equals(marker.getName())) {
                if (!cutting) throw new IllegalArgumentException("A removed section needs a start before its end.");
                cutting = false;
            }
        }
        File target = new File(outputDraft).getAbsoluteFile();
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".mcpr")) throw new IllegalArgumentException("The output filename must end in .mcpr.");
        if (target.exists()) throw new IllegalArgumentException("Choose a new output filename; that file already exists.");
        String stem = target.getName().substring(0, target.getName().length() - 5);
        long splits = edits().stream().filter(marker -> MarkerProcessor.MARKER_NAME_SPLIT.equals(marker.getName())).count();
        for (int i = 0; i <= splits; i++) if (new File(target.getParentFile(), stem + "_" + i + ".mcpr").exists()) {
            throw new IllegalArgumentException("A split output already exists for that name. Choose a different output filename.");
        }
        working = true;
        Set<Marker> snapshot = new HashSet<>(markers);
        new Thread(() -> {
            try {
                Files.createDirectories(target.toPath().getParent());
                Files.copy(input.toPath(), target.toPath());
                try (ReplayFile replay = ReplayMod.instance.openReplay(target.toPath())) {
                    replay.writeMarkers(snapshot); replay.save();
                }
                MarkerProcessor.apply(target.toPath(), value -> progress = value);
                ReplayMod.instance.runLater(() -> { working = false; parent.refreshFiles(); mc.displayGuiScreen(parent); });
            } catch (Throwable error) {
                com.replaymod.editor.ReplayModEditor.LOGGER.error("Could not create edited replay copy", error);
                ReplayMod.instance.runLater(() -> { working = false; message = "Editing failed: " + error.getMessage() + ". Source recording is unchanged."; });
            }
        }, "ReplayMod-StudioTrim").start();
    }
    @Override protected void submit() { activate(1); }
    @Override protected void escape() { if (!working) { parent.refreshFiles(); mc.displayGuiScreen(parent); } }
}
