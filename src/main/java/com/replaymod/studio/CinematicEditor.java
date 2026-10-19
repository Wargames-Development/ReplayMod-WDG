package com.replaymod.studio;

import com.replaymod.pathing.properties.CameraProperties;
import com.replaymod.pathing.properties.SpectatorProperty;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.simplepathing.InterpolatorType;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.SPTimeline.SPPath;
import com.replaymod.simplepathing.gui.GuiPathing;
import net.minecraft.client.gui.GuiTextField;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A persistent world view with a shot list, inspector and directly scrubbable clocks. */
public final class CinematicEditor extends StudioScreen {
    private static final int TRACK_LEFT = 94, TRACK_HEIGHT = 22;
    private StudioEditorLayout layout = new StudioEditorLayout(960, 580);
    private final ReplayHandler replay;
    private final ReplayModSimplePathing mod = ReplayModSimplePathing.instance;
    private final GuiPathing pathing;
    private GuiTextField output, source, x, y, z, yaw, pitch, roll, duration, step;
    private long cursor, selected = -1, pendingSeek = -1, lastSeek;
    private long visibleStart;
    private long visibleDuration = 10000;
    private int listOffset;
    private int dragTrack;
    private long dragKey = -1, dragTarget;
    private int dragStartX;
    private boolean actuallyDragging, freezeAction;
    private boolean pendingTimelineSeek;
    private long capturedShot = -1;
    private String[] appliedInspector;
    private ShotTiming.Span draggedRange;
    private int rangeHandle = -1;
    private int rangeFrom, rangeTo;
    private long previewEnd = -1;
    private Boolean originalHideGui;
    private java.util.Set<com.replaymod.replaystudio.data.Marker> markers;
    private InterpolatorType interpolation = InterpolatorType.CATMULL_ROM;

    public CinematicEditor(ReplayHandler replay) {
        canvasHeight = 580;
        this.replay = replay;
        pathing = mod.getGuiPathing();
        cursor = pathing.timeline.getCursorPosition();
        if (mod.getSelectedPath() != null) selected = mod.getSelectedTime();
        long end = model().times().isEmpty() ? 0 : model().times().last();
        visibleDuration = Math.min(Integer.MAX_VALUE, Math.max(10000, end + 1000));
    }
    private SPTimeline timeline() { return mod.getCurrentTimeline(); }
    @Override protected StudioViewport createViewport() {
        StudioViewport viewport = new StudioViewport(width, height, StudioEditorLayout.MIN_WIDTH, StudioEditorLayout.MIN_HEIGHT, true);
        layout = new StudioEditorLayout(viewport.canvasWidth, viewport.canvasHeight);
        return viewport;
    }
    private int right(int x) { return layout.right(x); }
    private int bottom(int y) { return layout.bottom(y); }
    private ShotModel model() { return new ShotModel(timeline()); }
    private ShotTiming timing() { return new ShotTiming(timeline()); }
    private ShotTiming.Span activeSpan() { return draggedRange != null ? new ShotTiming.Span(draggedRange.start, draggedRange.end, rangeFrom, rangeTo) : timing().at(selected >= 0 ? selected : cursor); }
    public void timingApplied(long from, long to, boolean preview) {
        message = ""; pendingSeek = -1; selected = -1; appliedInspector = null;
        mc.displayGuiScreen(this);
        select(from, true);
        if (preview) { pendingSeek = -1; previewEnd = to; syncCursor(); pathing.playPauseButton.onClick(); }
    }
    public void focusReplayClock() { for (GuiTextField field : fields) field.setFocused(false); source.setFocused(true); }
    public void goToMarker(com.replaymod.replaystudio.data.Marker marker) {
        seekSource(marker.getTime());
        CameraProperties.POSITION.applyToGame(Triple.of(marker.getX(), marker.getY(), marker.getZ()), replay);
        CameraProperties.ROTATION.applyToGame(Triple.of(marker.getYaw(), marker.getPitch(), marker.getRoll()), replay);
        selected = -1; mod.setSelected(null, 0); cameraToFields();
    }
    public static void captureCurrentShot(ReplayHandler replay) {
        ReplayModSimplePathing mod = ReplayModSimplePathing.instance;
        CameraEntity camera = replay.getCameraEntity();
        if (camera == null) return;
        if (replay.getReplaySender() instanceof com.replaymod.replay.FullReplaySender && ((com.replaymod.replay.FullReplaySender) replay.getReplaySender()).isHurrying()) {
            mod.getCore().printWarningToChat("Wait for the replay seek to finish before capturing a shot."); return;
        }
        long time = mod.getGuiPathing().timeline.getCursorPosition();
        try {
            long captured = new ShotModel(mod.getCurrentTimeline()).save(time, time, replay.getReplaySender().currentTimeStamp(),
                    camera.posX, camera.posY, camera.posZ, camera.rotationYaw, camera.rotationPitch, camera.roll);
            mod.getGuiPathing().timeline.setCursorPosition((int) captured);
            mod.setSelected(SPPath.POSITION, captured);
            replay.getOverlay().getStudioEditor().capturedShot = captured;
            mod.getCore().printInfoToChat("Captured camera and replay time at video " + StudioTime.format(captured));
        } catch (IllegalArgumentException invalid) { mod.getCore().printWarningToChat(invalid.getMessage()); }
    }
    @Override protected void build() {
        if (originalHideGui == null) originalHideGui = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
        markers = replay.getOverlay().timeline.getStudioMarkers();
        String[] draft = output == null ? null : new String[]{output.getText(), source.getText(), x.getText(), y.getText(), z.getText(), yaw.getText(), pitch.getText(), roll.getText(), step.getText(), duration.getText()};
        button(1, 16, 12, 126, "Replay library");
        button(2, 150, 12, 120, "Fly camera [Esc]");
        button(3, 278, 12, 116, "Preview [Space]");
        button(4, 402, 12, 84, "Undo"); button(5, 494, 12, 84, "Redo");
        button(6, 586, 12, 118, "Saved paths");
        button(7, right(800), 12, 144, "Export video");
        output = field(right(720), 82, 138, StudioTime.format(cursor)); button(8, right(864), 81, 68, "Go");
        source = field(right(720), 124, 138, StudioTime.format(replay.getReplaySender().currentTimeStamp()));
        button(9, right(864), 123, 68, "Go");
        x = field(right(720), 170, 64, "0"); y = field(right(790), 170, 64, "0"); z = field(right(860), 170, 72, "0");
        yaw = field(right(720), 212, 64, "0"); pitch = field(right(790), 212, 64, "0"); roll = field(right(860), 212, 72, "0");
        button(10, right(720), 242, 212, "Use current camera");
        button(11, right(720), 268, 212, "Apply values / update shot");
        button(12, right(720), 294, 212, "Movement: Smooth");
        button(13, right(720), 320, 102, "Delete shot");
        button(14, right(830), 320, 102, "New shot");
        button(15, 18, bottom(348), 120, "Add shot [I]");
        step = field(148, bottom(349), 48, "2.000");
        button(16, 18, bottom(377), 178, "Next slot + step");
        duration = field(right(720), bottom(365), 92, StudioTime.format(visibleDuration));
        button(17, right(820), bottom(364), 112, "Set zoom span");
        button(18, right(720), bottom(391), 102, "Zoom in"); button(19, right(830), bottom(391), 102, "Zoom out");
        button(20, 18, bottom(319), 84, "Previous"); button(21, 110, bottom(319), 86, "Next");
        button(22, 18, bottom(290), 178, "Replay: play / pause");
        button(23, 220, bottom(367), 212, freezeAction ? "New steps: frozen action" : "New steps: advancing action");
        button(24, layout.center(440), bottom(367), 128, "Path lines: " + (mod.getCore().getSettingsRegistry().get(com.replaymod.simplepathing.Setting.PATH_PREVIEW) ? "On" : "Off"));
        button(25, right(576), bottom(367), 116, "Scene markers");
        button(26, right(480), bottom(337), 190, "Whole path: normal speed 1x");
        button(27, 230, bottom(337), 240, "Timing / speed...");
        button(28, right(710), 12, 82, "Fit path");
        cameraToFields();
        if (draft == null && selected >= 0) select(selected, false);
        if (draft != null) {
            GuiTextField[] inputs = {output, source, x, y, z, yaw, pitch, roll, step, duration};
            for (int i = 0; i < inputs.length; i++) inputs[i].setText(draft[i]);
        }
        if (capturedShot >= 0) { select(capturedShot, false); capturedShot = -1; }
        updateMovementButton();
    }
    @Override protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (ReplayModReplay.instance.getReplayHandler() != replay) { mc.displayGuiScreen(null); return; }
        if (pathing.isPreviewPlaying()) {
            cursor = Math.max(0, pathing.getPreviewTime());
            if (previewEnd >= 0 && cursor >= previewEnd) { pathing.stopPreview(); cursor = previewEnd; previewEnd = -1; }
            syncCursor();
            if (cursor > visibleStart + visibleDuration || cursor < visibleStart) visibleStart = Math.max(0, cursor - visibleDuration / 10);
            if (selected < 0 && !output.isFocused() && !source.isFocused() && !x.isFocused() && !y.isFocused() && !z.isFocused() && !yaw.isFocused() && !pitch.isFocused() && !roll.isFocused()) {
                output.setText(StudioTime.format(cursor)); source.setText(StudioTime.format(replay.getReplaySender().currentTimeStamp())); cameraToFields();
            }
        }
        previewTypedPose();
        // Camera interpolation follows the mouse immediately. Rebuild replay
        // state once on release instead of repeatedly reloading it during a drag.
        if (pendingSeek >= 0 && dragTrack == 0 && rangeHandle < 0 && System.currentTimeMillis() - lastSeek > 120) {
            long target = pendingSeek;
            boolean cameraPath = pendingTimelineSeek;
            pendingSeek = -1;
            attempt(() -> { seekSource(target, !cameraPath || selected < 0); if (cameraPath) applyCameraAt(cursor); });
        }
        panel(8, 4, canvasWidth - 16, 36, "");
        panel(8, 48, 196, 374 + layout.extraHeight, "SHOT LIST");
        panel(right(708), 48, 236, 374 + layout.extraHeight, selected >= 0 ? "SHOT INSPECTOR (saved key)" : "NEW SHOT (capture here)");
        panel(220, 48, 472 + layout.extraWidth, 52, "");
        panel(8, bottom(430), canvasWidth - 24, 118, "");
        panel(220, bottom(286), 472 + layout.extraWidth, 74, "");
        boolean frozen = model().isActionFrozen();
        ShotTiming.Span active = activeSpan();
        label(active == null ? "Capture two camera shots to create a moving sequence" : "Selected camera move: " + active.rateLabel(), 230, bottom(299), frozen ? 0xFFFFCB82 : TEXT);
        label(active == null ? "Replay time chooses the action; video time places the camera." : "Replay " + StudioTime.format(active.sourceStart) + " -> " + StudioTime.format(active.sourceEnd) + "  /  Video duration " + StudioTime.format(active.duration()), 230, bottom(317), MUTED);
        label("Output time (in your video)", right(720), 66, MUTED);
        label("Shot replay time (recorded action)", right(720), 108, MUTED);
        label("X", right(720), 156, MUTED); label("Y", right(790), 156, MUTED); label("Z", right(860), 156, MUTED);
        label("Yaw", right(720), 198, MUTED); label("Pitch", right(790), 198, MUTED); label("Roll", right(860), 198, MUTED);
        label("Pose edits preview live; Apply saves.", right(720), bottom(351), MUTED);
        label("Step (s)", 150, bottom(339), MUTED);
        label("Live camera view", 220, 55, TEXT);
        label("Esc: fly  |  T: editor  |  Enter: apply  |  Ctrl+Z: undo", 220, 69, MUTED);
        ShotTiming.Span whole = timing().whole();
        label(whole != null && whole.start > 0 ? "Leading gap " + StudioTime.format(whole.start) + ": remove it in Timing / speed." : "Select a blue key; Timing / speed edits its camera move.", 230, 86, whole != null && whole.start > 0 ? 0xFFFFCB82 : MUTED);
        List<Long> times = new ArrayList<>(model().times());
        for (int row = 0; row < layout.shotRows && row + listOffset < times.size(); row++) {
            long time = times.get(row + listOffset);
            int top = 80 + row * 22;
            if (time == selected) drawRect(16, top - 2, 196, top + 20, 0xFF29425A);
            label(String.format(Locale.ROOT, "%02d  Video %s", row + listOffset + 1, StudioTime.format(time)), 24, top, time == selected ? TEXT : MUTED);
            Integer stamp = timeline().getTimePath().getValue(TimestampProperty.PROPERTY, time).orElse(null);
            label(stamp == null ? "Replay time: unset" : "Replay " + StudioTime.format(stamp), 42, top + 10, MUTED);
        }
        if (times.isEmpty()) {
            label("1. Fly to your first view", 20, 88, TEXT);
            label("2. Add shot", 20, 106, TEXT);
            label("3. Next slot, fly, add", 20, 124, TEXT);
            label("4. Preview and export", 20, 142, TEXT);
        }
        label("REPLAY", 18, layout.replayTop + 6, MUTED);
        label("VIDEO", 18, layout.videoTop + 6, ACCENT);
        drawTrack(TRACK_LEFT, layout.replayTop, layout.trackWidth, TRACK_HEIGHT, 0, Math.max(1, replay.getReplayDuration()), pendingSeek >= 0 ? pendingSeek : replay.getReplaySender().currentTimeStamp(), false);
        drawTrack(TRACK_LEFT, layout.videoTop, layout.trackWidth, TRACK_HEIGHT, visibleStart, visibleDuration, cursor, true);
        drawTimeMapping(active);
        label("Green: slide (keeps speed) | Red ends: resize | Shift-drag: slide | Alt-click: scrub | Right-click video: timing", 96, bottom(535), MUTED);
        label("Live replay " + StudioTime.format(replay.getReplaySender().currentTimeStamp()), 220, bottom(404), TEXT);
        label("Playhead video " + StudioTime.format(cursor), layout.center(402), bottom(404), TEXT);
        if (selected >= 0) label("Editing shot " + StudioTime.format(selected), right(550), bottom(404), ACCENT);
        boolean seeking = seekInProgress();
        for (Object value : buttonList) {
            net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) value;
            if (button.id == 15) { button.enabled = !seeking; button.displayString = seeking ? "Seeking replay..." : "Add shot [I]"; }
        }
    }
    private void previewTypedPose() {
        if (pathing.isPreviewPlaying() || !(x.isFocused() || y.isFocused() || z.isFocused() || yaw.isFocused() || pitch.isFocused() || roll.isFocused())) return;
        try {
            double px = StudioTime.number(x.getText(), "X"), py = StudioTime.number(y.getText(), "Y"), pz = StudioTime.number(z.getText(), "Z");
            float ry = (float) StudioTime.number(yaw.getText(), "Yaw"), rp = (float) StudioTime.number(pitch.getText(), "Pitch"), rr = (float) StudioTime.number(roll.getText(), "Roll");
            if (!Float.isFinite(ry) || !Float.isFinite(rp) || !Float.isFinite(rr) || rp < -90 || rp > 90) return;
            CameraProperties.POSITION.applyToGame(Triple.of(px, py, pz), replay);
            CameraProperties.ROTATION.applyToGame(Triple.of(ry, rp, rr), replay);
        } catch (IllegalArgumentException incomplete) { /* Allow partial values while typing. */ }
    }
    @Override protected void scrolled(int x, int y, int amount) {
        if (hit(x, y, 8, 48, 196, 274 + layout.extraHeight)) listOffset = Math.max(0, Math.min(Math.max(0, model().times().size() - 1), listOffset + (amount > 0 ? -1 : 1)));
        else if (hit(x, y, TRACK_LEFT, layout.videoTop, layout.trackWidth, TRACK_HEIGHT)) {
            long anchor = videoTime(x);
            double fraction = Math.max(0, Math.min(1, (x - TRACK_LEFT) / (double) layout.trackWidth));
            visibleDuration = Math.max(100, Math.min(Integer.MAX_VALUE, Math.round(visibleDuration * (amount > 0 ? 0.8 : 1.25))));
            visibleStart = Math.max(0, anchor - Math.round(fraction * visibleDuration));
            duration.setText(StudioTime.format(visibleDuration));
        }
    }
    private void drawTrack(int left, int top, int width, int height, long start, long range, long playhead, boolean shots) {
        drawRect(left, top, left + width, top + height, 0xFF1B2835);
        ShotTiming.Span active = activeSpan();
        if (active != null) {
            int from = TimelineGraphics.pixel(shots ? active.start : active.sourceStart, start, range, left, width);
            int to = TimelineGraphics.pixel(shots ? active.end : active.sourceEnd, start, range, left, width);
            drawRect(from, top, Math.max(from + 1, to), top + height, shots ? 0x8029425A : 0x80395A48);
        }
        for (int i = 0; i <= 8; i++) {
            int px = left + i * width / 8;
            drawRect(px, top + 15, px + 1, top + height, EDGE);
            label(StudioTime.format(start + range * i / 8), Math.min(px + 2, left + width - 44), top + 2, MUTED);
        }
        if (shots) for (long time : model().times()) {
            if (time < start || time > start + range) continue;
            int px = left + (int) ((time - start) * width / range);
            drawRect(px - 3, top + 12, px + 4, top + height, time == selected ? 0xFFFFCB82 : ACCENT);
        }
        if (!shots && markers != null) for (com.replaymod.replaystudio.data.Marker marker : markers) {
            int mx = left + (int) ((long) marker.getTime() * width / range);
            drawRect(mx - 2, top + height - 6, mx + 3, top + height, 0xFF80E080);
        }
        if (!shots) for (Keyframe key : timeline().getTimePath().getKeyframes()) {
            key.getValue(TimestampProperty.PROPERTY).ifPresent(stamp -> {
                int kx = TimelineGraphics.pixel(stamp, start, range, left, width);
                drawRect(kx - 1, top + 13, kx + 2, top + height, TimelineGraphics.RANGE);
            });
        }
        int px = left + (int) ((playhead - start) * width / Math.max(1, range));
        if (px >= left && px <= left + width) drawRect(px, top, px + 2, top + height, 0xFFFFCB82);
        if (shots && dragKey >= 0 && actuallyDragging) {
            int dx = left + (int) ((dragTarget - start) * width / range);
            drawRect(dx, top, dx + 2, top + height, 0xFF80E080);
        }
    }
    private void drawTimeMapping(ShotTiming.Span span) {
        if (span == null) return;
        int sourceIn = TimelineGraphics.pixel(span.sourceStart, 0, replay.getReplayDuration(), TRACK_LEFT, layout.trackWidth);
        int sourceOut = TimelineGraphics.pixel(span.sourceEnd, 0, replay.getReplayDuration(), TRACK_LEFT, layout.trackWidth);
        int videoIn = TimelineGraphics.pixel(span.start, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth);
        int videoOut = TimelineGraphics.pixel(span.end, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth);
        TimelineGraphics.connect(sourceIn, sourceOut, layout.replayTop + TRACK_HEIGHT, videoIn, videoOut, layout.videoTop);
        drawRect(sourceIn - 2, layout.replayTop - 2, sourceIn + 2, layout.replayTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        drawRect(sourceOut - 2, layout.replayTop - 2, sourceOut + 2, layout.replayTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        if (span.start >= visibleStart && span.start <= visibleStart + visibleDuration) drawRect(videoIn - 1, layout.videoTop - 2, videoIn + 1, layout.videoTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        if (span.end >= visibleStart && span.end <= visibleStart + visibleDuration) drawRect(videoOut - 1, layout.videoTop - 2, videoOut + 1, layout.videoTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        label("IN " + StudioTime.format(span.sourceStart), Math.max(96, Math.min(right(790), sourceIn + 5)), bottom(464), TEXT);
        label("OUT " + StudioTime.format(span.sourceEnd), Math.max(96, Math.min(right(790), sourceOut + 5)), bottom(479), TEXT);
    }
    @Override protected void clicked(int mx, int my, int button) {
        if (button == 1 && hit(mx, my, TRACK_LEFT, layout.videoTop, layout.trackWidth, TRACK_HEIGHT)) {
            requireAppliedInspector(); pathing.stopPreview();
            mc.displayGuiScreen(new StudioShotTiming(this, replay, videoTime(mx))); return;
        }
        if (button != 0) return;
        for (int row = 0; row < layout.shotRows; row++) {
            List<Long> times = new ArrayList<>(model().times());
            if (row + listOffset < times.size() && hit(mx, my, 16, 78 + row * 22, 180, 22)) { requireAppliedInspector(); message = ""; pathing.stopPreview(); select(times.get(row + listOffset), true); return; }
        }
        if (hit(mx, my, TRACK_LEFT, layout.replayTop - 2, layout.trackWidth, TRACK_HEIGHT + 4)) {
            requireAppliedInspector(); message = ""; pathing.stopPreview(); previewEnd = -1;
            ShotTiming.Span span = activeSpan();
            if (span != null) {
                int in = TimelineGraphics.pixel(span.sourceStart, 0, replay.getReplayDuration(), TRACK_LEFT, layout.trackWidth);
                int out = TimelineGraphics.pixel(span.sourceEnd, 0, replay.getReplayDuration(), TRACK_LEFT, layout.trackWidth);
                int handle = TimelineGraphics.rangeHandle(mx, in, out, isShiftKeyDown());
                // Alt-click always scrubs, even within the selected action range.
                if (!Keyboard.isKeyDown(Keyboard.KEY_LMENU) && !Keyboard.isKeyDown(Keyboard.KEY_RMENU) && handle >= 0) {
                    draggedRange = span; rangeFrom = span.sourceStart; rangeTo = span.sourceEnd;
                    rangeHandle = handle;
                    dragStartX = mx; actuallyDragging = false; return;
                }
            }
            selected = -1; mod.setSelected(null, 0); dragTrack = 1; scrub(mx);
        }
        if (hit(mx, my, TRACK_LEFT, layout.videoTop, layout.trackWidth, TRACK_HEIGHT)) {
            requireAppliedInspector(); message = ""; previewEnd = -1;
            pathing.stopPreview();
            dragTrack = 2;
            long time = videoTime(mx);
            for (long key : model().times()) if (Math.abs(key - time) * layout.trackWidth / visibleDuration <= 7) {
                select(key, true); dragKey = key; dragTarget = key; dragStartX = mx; actuallyDragging = false; return;
            }
            selected = -1; mod.setSelected(null, 0); scrub(mx); updateMovementButton();
        }
    }
    @Override protected void dragged(int mx, int my, int button) {
        if (button != 0) return;
        if (rangeHandle >= 0) {
            message = "";
            if (actuallyDragging || Math.abs(mx - dragStartX) >= 4) {
                actuallyDragging = true;
                int stamp = (int) TimelineGraphics.time(mx, 0, replay.getReplayDuration(), TRACK_LEFT, layout.trackWidth);
                if (rangeHandle == 2) {
                    long anchor = TimelineGraphics.time(dragStartX, 0, replay.getReplayDuration(), TRACK_LEFT, layout.trackWidth);
                    ShotTiming.Span shifted = timing().slide(draggedRange, stamp - anchor, replay.getReplayDuration()).after;
                    rangeFrom = shifted.sourceStart; rangeTo = shifted.sourceEnd;
                } else if (rangeHandle == 0) rangeFrom = stamp; else rangeTo = stamp;
            }
        } else if (dragKey >= 0) {
            if (actuallyDragging || Math.abs(mx - dragStartX) >= 4) { actuallyDragging = true; dragTarget = videoTime(mx); }
        } else if (dragTrack != 0) scrub(mx);
    }
    @Override protected void released(int mx, int my, int button) {
        if (button != 0) return;
        if (rangeHandle >= 0 || dragKey >= 0) attempt(() -> dragged(mx, my, button));
        if (rangeHandle >= 0) {
            ShotTiming.Span span = draggedRange;
            int handle = rangeHandle, from = rangeFrom, to = rangeTo;
            boolean changed = actuallyDragging;
            rangeHandle = -1; draggedRange = null; actuallyDragging = false;
            attempt(() -> {
                if (changed) timing().apply(handle == 2 ? timing().slide(span, (long) from - span.sourceStart, replay.getReplayDuration()) : timing().plan(span, from, to, span.duration(), replay.getReplayDuration()));
                select(handle == 1 ? span.end : span.start, true);
            });
        }
        if (dragKey >= 0) {
            message = "";
            long from = dragKey, to = dragTarget;
            dragKey = -1;
            actuallyDragging = false;
            if (from != to) attempt(() -> { move(from, to); select(to, true); });
        }
        dragTrack = 0;
    }
    private long videoTime(int mx) { return TimelineGraphics.time(mx, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth); }
    private void scrub(int mx) {
        pathing.stopPreview();
        if (dragTrack == 1) {
            pendingSeek = TimelineGraphics.time(mx, 0, replay.getReplayDuration(), TRACK_LEFT, layout.trackWidth);
            pendingTimelineSeek = false;
        }
        else { cursor = videoTime(mx); output.setText(StudioTime.format(cursor)); previewAt(cursor); cameraToFields(); }
    }
    private void seekSource(long time) {
        seekSource(time, true);
    }
    private void seekSource(long time, boolean updateInspector) {
        if (time < 0 || time > replay.getReplayDuration()) throw new IllegalArgumentException("Replay time is outside this recording.");
        pendingSeek = -1;
        replay.getReplaySender().setReplaySpeed(0);
        replay.doJump((int) time, true);
        // Backward seeks close the screen while rebuilding the world. Restore
        // through Minecraft so the mouse is released and native input reinitialises.
        if (mc.currentScreen != this) mc.displayGuiScreen(this);
        if (updateInspector) source.setText(StudioTime.format(time));
        lastSeek = System.currentTimeMillis();
    }
    private void previewAt(long time) {
        cursor = time;
        syncCursor();
        timeline().getTimeline().getPaths().forEach(path -> path.updateAll());
        timeline().getTimePath().getValue(TimestampProperty.PROPERTY, time).ifPresent(value -> { pendingSeek = (long) value; pendingTimelineSeek = true; });
        applyCameraAt(time);
    }
    private void applyCameraAt(long time) {
        timeline().getPositionPath().getValue(CameraProperties.POSITION, time).ifPresent(value -> CameraProperties.POSITION.applyToGame(value, replay));
        timeline().getPositionPath().getValue(CameraProperties.ROTATION, time).ifPresent(value -> CameraProperties.ROTATION.applyToGame(value, replay));
        timeline().getPositionPath().getValue(SpectatorProperty.PROPERTY, time).ifPresent(value -> SpectatorProperty.PROPERTY.applyToGame(value, replay));
    }
    private void syncCursor() {
        int bounded = (int) Math.min(Integer.MAX_VALUE, Math.max(0, cursor));
        if (pathing.timeline.getLength() < bounded) pathing.timeline.setLength(bounded);
        pathing.timeline.setCursorPosition(bounded);
    }
    private void select(long time, boolean preview) {
        selected = cursor = time;
        if (time < visibleStart || time > visibleStart + visibleDuration) visibleStart = Math.max(0, time - visibleDuration / 10);
        List<Long> times = new ArrayList<>(model().times());
        int index = times.indexOf(time);
        if (index >= 0 && (index < listOffset || index >= listOffset + layout.shotRows)) listOffset = index / layout.shotRows * layout.shotRows;
        mod.setSelected(timeline().isPositionKeyframe(time) ? SPPath.POSITION : SPPath.TIME, time);
        output.setText(StudioTime.format(time));
        Keyframe position = timeline().getPositionPath().getKeyframe(time);
        if (position != null) {
            position.getValue(CameraProperties.POSITION).ifPresent(value -> { x.setText(number(value.getLeft())); y.setText(number(value.getMiddle())); z.setText(number(value.getRight())); });
            position.getValue(CameraProperties.ROTATION).ifPresent(value -> { yaw.setText(number(value.getLeft())); pitch.setText(number(value.getMiddle())); roll.setText(number(value.getRight())); });
        }
        Keyframe stamp = timeline().getTimePath().getKeyframe(time);
        if (stamp != null) stamp.getValue(TimestampProperty.PROPERTY).ifPresent(value -> source.setText(StudioTime.format(value)));
        if (preview) { previewAt(time); if (position == null) cameraToFields(); }
        appliedInspector = inspectorValues();
        updateMovementButton();
    }
    private String[] inspectorValues() {
        return new String[]{output.getText(), source.getText(), x.getText(), y.getText(), z.getText(), yaw.getText(), pitch.getText(), roll.getText()};
    }
    private void requireAppliedInspector() {
        if (selected < 0 || appliedInspector == null) return;
        String[] current = inspectorValues();
        try {
            for (int i = 0; i < current.length; i++) {
                double value = i < 2 ? StudioTime.parse(current[i]) : StudioTime.number(current[i], "Camera value");
                double saved = i < 2 ? StudioTime.parse(appliedInspector[i]) : StudioTime.number(appliedInspector[i], "Camera value");
                if (Double.compare(value, saved) != 0) throw new IllegalArgumentException("A field differs from the saved shot.");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Shot edits are not saved. Click Apply values / update shot before changing shots, timing, previewing or exporting. " + invalid.getMessage());
        }
    }
    private void updateMovementButton() {
        ShotTiming.Span active = activeSpan();
        com.replaymod.replaystudio.pathing.path.PathSegment segment = timeline().getPositionPath().getSegments().stream()
                .filter(value -> active != null && value.getStartKeyframe().getTime() == active.start).findFirst().orElse(null);
        if (segment != null && segment.getInterpolator() != null) interpolation = InterpolatorType.fromClass(segment.getInterpolator().getClass());
        for (Object value : buttonList) {
            net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) value;
            if (button.id == 12) {
                button.enabled = segment != null;
                button.displayString = segment == null ? "Capture two shots for camera motion" : interpolation == InterpolatorType.LINEAR ? "Camera move: Straight" : "Camera move: Smooth";
            }
            if (button.id == 26 || button.id == 27) button.enabled = timing().whole() != null;
        }
    }
    private String number(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private void cameraToFields() {
        CameraEntity camera = replay.getCameraEntity();
        if (camera == null) return;
        x.setText(number(camera.posX)); y.setText(number(camera.posY)); z.setText(number(camera.posZ));
        yaw.setText(number(camera.rotationYaw)); pitch.setText(number(camera.rotationPitch)); roll.setText(number(camera.roll));
    }
    private void save() {
        long time = StudioTime.parse(output.getText()), sourceTime = StudioTime.parse(source.getText());
        if (sourceTime > replay.getReplayDuration()) throw new IllegalArgumentException("Replay time is outside this recording.");
        time = model().save(selected, time, (int) sourceTime, StudioTime.number(x.getText(), "X"), StudioTime.number(y.getText(), "Y"), StudioTime.number(z.getText(), "Z"),
                (float) StudioTime.number(yaw.getText(), "Yaw"), (float) StudioTime.number(pitch.getText(), "Pitch"), (float) StudioTime.number(roll.getText(), "Roll"));
        select(time, true);
    }
    private void captureNewShot() {
        if (seekInProgress()) throw new IllegalStateException("Wait for the replay seek to finish before capturing a shot.");
        CameraEntity camera = replay.getCameraEntity();
        if (camera == null) throw new IllegalStateException("Wait for the replay camera to finish loading.");
        long time = model().save(-1, StudioTime.parse(output.getText()), replay.getReplaySender().currentTimeStamp(),
                camera.posX, camera.posY, camera.posZ, camera.rotationYaw, camera.rotationPitch, camera.roll);
        // A failed capture leaves selection and all inspector drafts untouched.
        select(time, false); syncCursor();
        message = "Shot added at video " + StudioTime.format(time) + ", replay " + source.getText() + ". Use Next slot + step for another shot.";
    }
    private boolean seekInProgress() {
        return replay.getReplaySender() instanceof com.replaymod.replay.FullReplaySender && ((com.replaymod.replay.FullReplaySender) replay.getReplaySender()).isHurrying();
    }
    private void move(long from, long to) {
        if (model().times().contains(to)) throw new IllegalArgumentException("A shot already exists at that time.");
        // Moving a paired time key must preserve forward playback.
        Keyframe stamp = timeline().getTimePath().getKeyframe(from);
        if (stamp != null) {
            int value = stamp.getValue(TimestampProperty.PROPERTY).orElse(0);
            for (Keyframe other : timeline().getTimePath().getKeyframes()) if (other.getTime() != from) {
                int otherValue = other.getValue(TimestampProperty.PROPERTY).orElse(0);
                if ((other.getTime() < to && otherValue > value) || (other.getTime() > to && otherValue < value)) throw new IllegalArgumentException("That move would reverse replay time.");
            }
        }
        timeline().editAtomically(() -> { for (SPPath path : SPPath.values()) if (timeline().getKeyframe(path, from) != null) timeline().recordAppliedChange(timeline().moveKeyframe(path, from, to)); });
    }
    @Override protected void activate(int id) {
        message = "";
        if (id == 1 || id == 6 || id == 14 || id == 15 || id == 16 || id == 25 || id == 26 || id == 27) requireAppliedInspector();
        boolean resumePreview = id == 11 && pathing.isPreviewPlaying();
        long resumeTime = cursor;
        if (id != 3) { pathing.stopPreview(); previewEnd = -1; }
        pendingSeek = -1;
        switch (id) {
            case 1: mc.displayGuiScreen(new ReplayLibrary(this)); break;
            case 2: escape(); break;
            case 3:
                if (!pathing.isPreviewPlaying()) requireAppliedInspector();
                if (timeline().getPositionPath().getSegments().isEmpty() || timeline().getTimePath().getSegments().isEmpty()) throw new IllegalArgumentException("Add at least two shots before previewing.");
                previewEnd = -1; syncCursor(); if (pathing.isPreviewPlaying()) pathing.stopPreview(); else pathing.playPauseButton.onClick(); break;
            case 4: if (timeline().getTimeline().peekUndoStack() != null) timeline().getTimeline().undoLastChange(); selected = -1; mod.setSelected(null, 0); previewAt(cursor); cameraToFields(); break;
            case 5: if (timeline().getTimeline().peekRedoStack() != null) timeline().getTimeline().redoLastChange(); selected = -1; mod.setSelected(null, 0); previewAt(cursor); cameraToFields(); break;
            case 6: mc.displayGuiScreen(new StudioPaths(this, replay)); break;
            case 7: requireAppliedInspector(); mc.displayGuiScreen(new StudioExport(this, replay, pathing)); break;
            case 8: cursor = StudioTime.parse(output.getText()); previewAt(cursor); if (selected < 0) cameraToFields(); break;
            case 9: seekSource(StudioTime.parse(source.getText())); break;
            case 10:
                if (selected >= 0 && timeline().isSpectatorKeyframe(selected)) timeline().recordAppliedChange(timeline().detachSpectatorKeyframe(selected));
                replay.spectateCamera(); cameraToFields(); break;
            case 11:
                save();
                if (resumePreview) { pendingSeek = -1; cursor = resumeTime; syncCursor(); pathing.playPauseButton.onClick(); }
                break;
            case 12:
                requireAppliedInspector();
                ShotTiming.Span movement = activeSpan();
                if (movement == null) throw new IllegalArgumentException("Capture two camera shots first.");
                updateMovementButton();
                interpolation = interpolation == InterpolatorType.LINEAR ? InterpolatorType.CATMULL_ROM : InterpolatorType.LINEAR;
                timeline().recordAppliedChange(timeline().setInterpolator(movement.start, interpolation.newInstance()));
                updateMovementButton();
                previewAt(cursor); break;
            case 13: if (selected >= 0) model().remove(selected); selected = -1; mod.setSelected(null, 0); break;
            case 14: if (model().times().contains(cursor)) activate(16); selected = -1; cameraToFields(); source.setText(StudioTime.format(replay.getReplaySender().currentTimeStamp())); break;
            case 15:
                captureNewShot(); break;
            case 16:
                long advance = StudioTime.parse(step.getText()); if (advance < 10) throw new IllegalArgumentException("Step must be at least 0.01 seconds.");
                cursor = Math.min(Integer.MAX_VALUE, cursor + advance); selected = -1; mod.setSelected(null, 0); syncCursor(); output.setText(StudioTime.format(cursor));
                if (cursor > visibleStart + visibleDuration) visibleStart = Math.max(0, cursor - visibleDuration / 10);
                if (!freezeAction) seekSource(Math.min(replay.getReplayDuration(), replay.getReplaySender().currentTimeStamp() + advance)); break;
            case 17: visibleDuration = Math.max(100, StudioTime.parse(duration.getText())); visibleStart = 0; break;
            case 18: visibleDuration = Math.max(100, visibleDuration / 2); visibleStart = Math.max(0, cursor - visibleDuration / 2); duration.setText(StudioTime.format(visibleDuration)); break;
            case 19: visibleDuration = Math.min(Integer.MAX_VALUE, visibleDuration * 2); visibleStart = Math.max(0, cursor - visibleDuration / 2); duration.setText(StudioTime.format(visibleDuration)); break;
            case 20: listOffset = Math.max(0, listOffset - layout.shotRows); break;
            case 21: listOffset = Math.min(Math.max(0, model().times().size() - 1), listOffset + layout.shotRows); break;
            case 22: replay.getReplaySender().setReplaySpeed(replay.getReplaySender().paused() ? 1 : 0); break;
            case 23:
                freezeAction = !freezeAction;
                for (Object value : buttonList) { net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) value; if (button.id == 23) button.displayString = freezeAction ? "New steps: frozen action" : "New steps: advancing action"; }
                break;
            case 24:
                com.replaymod.core.SettingsRegistry settings = mod.getCore().getSettingsRegistry();
                settings.set(com.replaymod.simplepathing.Setting.PATH_PREVIEW, !settings.get(com.replaymod.simplepathing.Setting.PATH_PREVIEW)); settings.save(); initGui(); break;
            case 25: mc.displayGuiScreen(new StudioMarkers(replay)); break;
            case 26:
                model().playActionAtNormalSpeed(replay.getReplayDuration());
                freezeAction = false;
                for (Object value : buttonList) { net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) value; if (button.id == 23) button.displayString = "New steps: advancing action"; }
                select(model().times().first(), true);
                break;
            case 27: mc.displayGuiScreen(new StudioShotTiming(this, replay, selected >= 0 ? selected : cursor)); break;
            case 28:
                visibleStart = 0;
                visibleDuration = Math.min(Integer.MAX_VALUE, Math.max(1000, model().times().isEmpty() ? 10000 : model().times().last() + 500));
                duration.setText(StudioTime.format(visibleDuration)); break;
            default: break;
        }
        if (mc.currentScreen == this) updateMovementButton();
    }
    @Override protected void submit() { activate(11); }
    @Override protected void shortcut(char c, int key) {
        if (key == Keyboard.KEY_SPACE) attempt(() -> activate(3));
        if (key == Keyboard.KEY_I) attempt(() -> activate(15));
        if (key == Keyboard.KEY_Z && isCtrlKeyDown()) attempt(() -> activate(isShiftKeyDown() ? 5 : 4));
        if (key == Keyboard.KEY_DELETE) attempt(() -> activate(13));
        if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) attempt(() -> {
            requireAppliedInspector(); pathing.stopPreview(); previewEnd = -1;
            long step = isShiftKeyDown() ? 1000 : 50;
            cursor = Math.max(0, Math.min(Integer.MAX_VALUE, cursor + (key == Keyboard.KEY_LEFT ? -step : step)));
            selected = -1; mod.setSelected(null, 0); output.setText(StudioTime.format(cursor)); previewAt(cursor); cameraToFields();
            if (cursor < visibleStart || cursor > visibleStart + visibleDuration) visibleStart = Math.max(0, cursor - visibleDuration / 2);
        });
    }
    @Override protected void escape() {
        pathing.stopPreview();
        pendingSeek = -1;
        syncCursor();
        replay.getOverlay().setMouseVisible(false);
        mc.displayGuiScreen(null);
    }
    @Override public void onGuiClosed() {
        if (originalHideGui != null) { mc.gameSettings.hideGUI = originalHideGui; originalHideGui = null; }
        super.onGuiClosed();
    }
}
