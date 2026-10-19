package com.replaymod.studio;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import net.minecraft.client.gui.GuiTextField;
import java.util.List;
import java.util.Locale;

/** A draft timing diagram and explicit duration/speed controls for a camera move. */
public final class StudioShotTiming extends StudioScreen {
    private final CinematicEditor parent;
    private final ReplayHandler replay;
    private final ShotTiming timing;
    private ShotTiming.Span span;
    private boolean whole, speedDrivesDuration, keepCameraDuration;
    private int transition;
    private GuiTextField replayIn, replayOut, videoDuration, actionSpeed;
    private String inText, outText, durationText, speedText;
    private String previewError = "";
    private ShotTiming.Plan proposal;
    private ShotTiming.Plan dragPlan;
    private int dragHandle = -1, dragX;
    private boolean dragMoved;

    public StudioShotTiming(CinematicEditor parent, ReplayHandler replay, long at) {
        this.parent = parent; this.replay = replay;
        timing = new ShotTiming(ReplayModSimplePathing.instance.getCurrentTimeline());
        ShotTiming.Span selected = timing.at(at);
        if (selected == null) throw new IllegalArgumentException("Capture two camera shots before editing a transition's timing.");
        List<ShotTiming.Span> spans = timing.spans();
        for (int i = 0; i < spans.size(); i++) if (spans.get(i).start == selected.start) transition = i;
        loadScope();
    }
    private void loadScope() {
        List<ShotTiming.Span> spans = timing.spans();
        transition = Math.max(0, Math.min(transition, spans.size() - 1));
        span = whole ? timing.whole() : spans.get(transition);
        inText = StudioTime.format(span.sourceStart); outText = StudioTime.format(span.sourceEnd);
        durationText = StudioTime.format(span.duration()); speedText = String.format(Locale.ROOT, "%.6f", span.speed());
        speedDrivesDuration = false;
    }
    @Override protected void build() {
        dragHandle = -1; dragPlan = null;
        button(1, 90, 64, 470, whole ? "Editing: whole camera sequence" : "Editing: camera move " + (transition + 1) + " -> " + (transition + 2));
        button(2, 570, 64, 120, "Previous move"); button(3, 700, 64, 150, "Next move");
        button(30, 100, 88, 740, keepCameraDuration ? "Speed changes Replay Out; camera duration stays fixed (click to switch)" : "Speed changes video duration; replay In / Out stay fixed (click to switch)");
        replayIn = field(100, 132, 160, inText); replayOut = field(280, 132, 160, outText);
        videoDuration = field(480, 132, 160, durationText); actionSpeed = field(680, 132, 160, speedText);
        double[] rates = {0.25, 0.5, 1, 2, 4};
        for (int i = 0; i < rates.length; i++) button(10 + i, 100 + i * 116, 188, 106, rates[i] + "x");
        button(15, 680, 188, 160, "Freeze recorded action");
        button(20, 100, 436, 160, "Back to editor");
        button(21, 280, 436, 170, "Apply duration"); button(22, 460, 436, 170, "Apply speed");
        button(23, 640, 436, 200, "Apply + preview");
        button(24, 100, 475, 260, "Remove leading gap (whole video)");
        for (Object value : buttonList) {
            net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) value;
            if (button.id == 24) button.enabled = timing.whole() != null && timing.whole().start > 0;
            if (button.id == 2) button.enabled = transition > 0;
            if (button.id == 3) button.enabled = transition + 1 < timing.spans().size();
        }
    }
    private void remember() {
        inText = replayIn.getText(); outText = replayOut.getText(); durationText = videoDuration.getText(); speedText = actionSpeed.getText();
    }
    @Override protected void beforeResize() { remember(); }
    private ShotTiming.Plan proposal() {
        int from = (int) StudioTime.parse(replayIn.getText());
        int to = speedDrivesDuration && keepCameraDuration ? from : (int) StudioTime.parse(replayOut.getText());
        long duration = speedDrivesDuration && !keepCameraDuration ? 0 : StudioTime.parse(videoDuration.getText());
        if (speedDrivesDuration) {
            double speed = StudioTime.number(actionSpeed.getText().trim().replaceAll("[xX]$", ""), "Speed");
            if (keepCameraDuration) to = ShotTiming.sourceEndAtSpeed(from, duration, speed, replay.getReplayDuration());
            else duration = ShotTiming.durationAtSpeed(from, to, speed);
        }
        return timing.plan(span, from, to, duration, replay.getReplayDuration());
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xED101720); panel(70, 20, 800, 494, "TIMING / SPEED - recorded action mapped into your video");
        label("Replay In", 100, 114, MUTED); label("Replay Out", 280, 114, MUTED);
        label("Video duration", 480, 114, TEXT); label("Action speed (1 = normal)", 680, 114, TEXT);
        label("Choose a duration OR a speed. Equal Replay In/Out freezes vehicles while the camera moves.", 100, 162, MUTED);
        proposal = null; previewError = "";
        try { proposal = proposal(); }
        catch (IllegalArgumentException invalid) { previewError = invalid.getMessage(); }
        if (proposal != null) {
            if (speedDrivesDuration) {
                if (keepCameraDuration) replayOut.setText(StudioTime.format(proposal.after.sourceEnd));
                else videoDuration.setText(StudioTime.format(proposal.after.duration()));
            } else if (!actionSpeed.isFocused()) actionSpeed.setText(String.format(Locale.ROOT, "%.6f", proposal.after.speed()));
            drawPlan(proposal);
        }
        else {
            label("Finish the values to see the timing diagram.", 110, 270, TEXT);
            List<String> lines = fontRendererObj.listFormattedStringToWidth(previewError, 720);
            for (int i = 0; i < Math.min(3, lines.size()); i++) label(lines.get(i), 110, 296 + i * 12, 0xFFFFCB82);
        }
        label("Apply sets a constant rate in this range. Later shots shift together; camera poses are preserved.", 100, 412, MUTED);
        label(speedDrivesDuration ? "Apply + preview uses the speed value." : "Apply + preview uses the duration value.", 400, 481, MUTED);
    }
    private void drawPlan(ShotTiming.Plan plan) {
        ShotTiming.Span after = plan.after;
        long fullSource = Math.max(1, replay.getReplayDuration()), fullVideo = Math.max(1, plan.totalDuration);
        int sourceStart = TimelineGraphics.pixel(after.sourceStart, 0, fullSource, 110, 720);
        int sourceEnd = TimelineGraphics.pixel(after.sourceEnd, 0, fullSource, 110, 720);
        int videoStart = TimelineGraphics.pixel(after.start, 0, fullVideo, 110, 720);
        int videoEnd = TimelineGraphics.pixel(after.end, 0, fullVideo, 110, 720);
        label("REPLAY  " + StudioTime.format(after.sourceStart) + " -> " + StudioTime.format(after.sourceEnd), 110, 228, TEXT);
        drawRect(110, 246, 830, 272, 0xFF1B2835);
        TimelineGraphics.range(sourceStart, sourceEnd, 250, 268, 0x996BB28B);
        label("0:00", 110, 276, MUTED); label(StudioTime.format(fullSource), 774, 276, MUTED);
        TimelineGraphics.connect(sourceStart, sourceEnd, 272, videoStart, videoEnd, 340);
        drawRect(106, 292, 834, 310, PANEL);
        label("Drag green range: slide action, keep speed | Red ends: resize | Shift-drag end: slide", 110, 297, MUTED);
        drawRect(110, 340, 830, 366, 0xFF1B2835);
        TimelineGraphics.range(videoStart, videoEnd, 344, 362, 0x9979A9CF);
        for (long time : plan.outputTimes.values()) {
            int px = TimelineGraphics.pixel(time, 0, fullVideo, 110, 720);
            drawRect(px - 1, 354, px + 2, 366, ACCENT);
        }
        label("VIDEO  " + StudioTime.format(after.start) + " -> " + StudioTime.format(after.end) + "   |   Duration " + StudioTime.format(after.duration()), 110, 372, TEXT);
        label(after.rateLabel() + "   |   " + (plan.shift == 0 ? "Later shots keep their times" : "Later shots shift " + (plan.shift > 0 ? "+" : "-") + StudioTime.format(Math.abs(plan.shift))), 110, 390, ACCENT);
        label(speedDrivesDuration ? keepCameraDuration ? "Computed Replay Out: " + StudioTime.format(after.sourceEnd) : "Computed duration: " + StudioTime.format(after.duration()) : "Computed speed: " + String.format(Locale.ROOT, "%.4fx", after.speed()), 480, 173, ACCENT);
    }
    @Override protected void clicked(int x, int y, int button) {
        if (button != 0 || !hit(x, y, 104, 246, 732, 26)) return;
        ShotTiming.Plan draft = proposal();
        int from = TimelineGraphics.pixel(draft.after.sourceStart, 0, replay.getReplayDuration(), 110, 720);
        int to = TimelineGraphics.pixel(draft.after.sourceEnd, 0, replay.getReplayDuration(), 110, 720);
        int handle = TimelineGraphics.rangeHandle(x, from, to, isShiftKeyDown());
        if (handle < 0) return;
        message = ""; dragPlan = draft; dragHandle = handle; dragX = x; dragMoved = false;
        for (GuiTextField field : fields) field.setFocused(false);
    }
    @Override protected void dragged(int x, int y, int button) {
        if (button != 0 || dragHandle < 0 || !dragMoved && Math.abs(x - dragX) < 2) return;
        dragMoved = true;
        int stamp = (int) TimelineGraphics.time(x, 0, replay.getReplayDuration(), 110, 720);
        ShotTiming.Plan moved;
        if (dragHandle == 2) {
            long anchor = TimelineGraphics.time(dragX, 0, replay.getReplayDuration(), 110, 720);
            moved = timing.slide(dragPlan, stamp - anchor, replay.getReplayDuration());
        } else {
            moved = timing.plan(span, dragHandle == 0 ? stamp : dragPlan.after.sourceStart,
                    dragHandle == 1 ? stamp : dragPlan.after.sourceEnd, dragPlan.after.duration(), replay.getReplayDuration());
        }
        replayIn.setText(StudioTime.format(moved.after.sourceStart)); replayOut.setText(StudioTime.format(moved.after.sourceEnd));
        videoDuration.setText(StudioTime.format(moved.after.duration())); actionSpeed.setText(String.format(Locale.ROOT, "%.6f", moved.after.speed()));
        speedDrivesDuration = false;
        message = "";
    }
    @Override protected void released(int x, int y, int button) {
        if (button != 0) return;
        attempt(() -> dragged(x, y, button));
        // Only updates the draft; Apply still controls when the path changes.
        dragHandle = -1; dragPlan = null;
    }
    @Override protected void activate(int id) {
        remember();
        if (id == 1) { whole = !whole; loadScope(); initGui(); }
        if (id == 2 || id == 3) { whole = false; transition += id == 2 ? -1 : 1; loadScope(); initGui(); }
        if (id >= 10 && id <= 14) {
            actionSpeed.setText(String.valueOf(new double[]{0.25, 0.5, 1, 2, 4}[id - 10]));
            if (!keepCameraDuration && StudioTime.parse(replayIn.getText()) == StudioTime.parse(replayOut.getText())) throw new IllegalArgumentException("Choose a later Replay Out first, or switch to keeping camera duration fixed.");
            speedDrivesDuration = true; videoDuration.setFocused(false); actionSpeed.setFocused(true);
        }
        if (id == 15) {
            replayOut.setText(replayIn.getText()); actionSpeed.setText("0");
            speedDrivesDuration = false; actionSpeed.setFocused(false); videoDuration.setFocused(true);
        }
        if (id == 20) escape();
        if (id >= 21 && id <= 23) {
            if (id != 23) speedDrivesDuration = id == 22;
            ShotTiming.Plan plan = proposal(); timing.apply(plan);
            parent.timingApplied(plan.after.start, plan.after.end, id == 23);
        }
        if (id == 24) {
            timing.removeLeadingGap(); loadScope(); initGui();
        }
        if (id == 30) { keepCameraDuration = !keepCameraDuration; speedDrivesDuration = true; initGui(); }
    }
    @Override protected void keyTyped(char character, int key) {
        super.keyTyped(character, key);
        if (mc.currentScreen != this || key == org.lwjgl.input.Keyboard.KEY_RETURN || key == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) return;
        if (actionSpeed.isFocused()) speedDrivesDuration = true;
        else if (replayIn.isFocused() || replayOut.isFocused() || videoDuration.isFocused()) speedDrivesDuration = false;
    }
    @Override protected void submit() { activate(speedDrivesDuration ? 22 : 21); }
    @Override protected void escape() { parent.timingApplied(span.start, span.end, false); }
}
