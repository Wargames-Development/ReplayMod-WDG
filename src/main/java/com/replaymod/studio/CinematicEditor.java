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
    private static final int TRACK_LEFT = StudioEditorLayout.TRACK_LEFT, TRACK_HEIGHT = StudioEditorLayout.TRACK_HEIGHT;
    private StudioEditorLayout layout = new StudioEditorLayout(1120, 720);
    private StudioWorkspacePreferences preferences;
    private final StudioPreview viewer = new StudioPreview();
    private boolean fullscreenViewer, restoreWindowedViewer;
    private boolean showSamples;
    private boolean leavingWorkspace, settingsLoaded;
    private long nextViewerFrame;
    public boolean ownsWorkspace(ReplayHandler handler) { return replay == handler && !leavingWorkspace; }
    private double viewerAspect = 16.0 / 9;
    private int previewFps = 60, dragSplitter, panTrack, panMouseButton;
    private int panAnchorX;
    private long panAnchorStart, sourceStart, sourceDuration;
    private boolean panMoved;
    private boolean followPlayhead = true;
    private long panDomain;
    private static CinematicEditor previewWorkspace;
    private final ReplayHandler replay;
    private final ReplayModSimplePathing mod = ReplayModSimplePathing.instance;
    private final GuiPathing pathing;
    private GuiTextField output, source, x, y, z, yaw, pitch, roll, duration, step;
    private long cursor, selected = -1, pendingSeek = -1, soughtSource = -1;
    private long timingSelection = -1;
    private boolean seekCameraPath;
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
        canvasWidth = StudioEditorLayout.MIN_WIDTH; canvasHeight = StudioEditorLayout.MIN_HEIGHT;
        this.replay = replay;
        preferences = StudioWorkspacePreferences.load(preferencesFile());
        sourceDuration = Math.max(1, replay.getReplayDuration());
        pathing = mod.getGuiPathing();
        cursor = pathing.timeline.getCursorPosition();
        if (mod.getSelectedPath() != null) selected = mod.getSelectedTime();
        long end = model().times().isEmpty() ? 0 : model().times().last();
        visibleDuration = Math.min(Integer.MAX_VALUE, Math.max(10000, end + 1000));
    }
    private SPTimeline timeline() { return mod.getCurrentTimeline(); }
    @Override protected StudioViewport createViewport() {
        StudioViewport viewport = new StudioViewport(width, height, StudioEditorLayout.MIN_WIDTH, StudioEditorLayout.MIN_HEIGHT, true);
        layout = new StudioEditorLayout(viewport.canvasWidth, viewport.canvasHeight, preferences.library, preferences.inspector, preferences.timeline);
        return viewport;
    }
    private java.io.File preferencesFile() { return new java.io.File(com.replaymod.core.ReplayMod.instance.getMinecraft().mcDataDir, "config/replaymod-workspace.json"); }
    private void saveLayout() { try { preferences.save(preferencesFile()); } catch (java.io.IOException error) { message = "Could not save workspace layout: " + error.getMessage(); } }
    private void relayout() {
        int focus = -1, caret = 0;
        for (int i = 0; i < fields.size(); i++) if (fields.get(i).isFocused()) { focus = i; caret = fields.get(i).getCursorPosition(); }
        initGui();
        if (focus >= 0 && focus < fields.size()) { fields.get(focus).setFocused(true); fields.get(focus).setCursorPosition(caret); }
    }
    public void renderViewer(net.minecraft.client.renderer.EntityRenderer renderer, float partial) {
        updateEditorFrame();
        attempt(replay::tickEditorSeek);
        if (replay.isEditorSeekRunning()) return;
        if (replay.isEditorSeeking() && seekCameraPath) applyCameraAt(cursor);
        if (!replay.isEditorSeeking() && soughtSource >= 0) {
            if (seekCameraPath) applyCameraAt(cursor);
            soughtSource = -1;
        }
        if (mc.currentScreen != this || mc.theWorld == null) return;
        long now = System.nanoTime();
        if (now < nextViewerFrame) return;
        nextViewerFrame = now + 1_000_000_000L / Math.max(1, previewFps);
        if (!net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled()) {
            message = "Enable framebuffer support in Minecraft's video settings to see the camera viewer.";
            return;
        }
        com.replaymod.replay.compat.DistantHorizonsReplay.tickPreview();
        int[] rect = layout.preview(fullscreenViewer, viewerAspect);
        int width = Math.max(1, (int) Math.round(rect[2] * mc.displayWidth / (double) canvasWidth));
        int height = Math.max(1, (int) Math.round(rect[3] * mc.displayHeight / (double) canvasHeight));
        viewer.render(renderer, partial, width, height);
    }
    public static void restorePreviewWorkspace() {
        CinematicEditor editor = previewWorkspace;
        if (editor == null) return;
        net.minecraft.client.Minecraft mc = com.replaymod.core.ReplayMod.instance.getMinecraft();
        if (ReplayModReplay.instance.getReplayHandler() != editor.replay) { previewWorkspace = null; return; }
        if (mc.theWorld != null && mc.thePlayer != null && (mc.currentScreen == null || mc.currentScreen instanceof net.minecraft.client.gui.GuiDownloadTerrain || mc.currentScreen instanceof com.replaymod.replay.gui.screen.GuiOpeningReplay)) mc.displayGuiScreen(editor);
    }
    public static void previewFinished(GuiPathing pathing, long end) {
        CinematicEditor editor = previewWorkspace;
        if (editor == null || editor.pathing != pathing) return;
        editor.cursor = end;
        editor.previewEnd = -1;
        editor.syncCursor();
        if (editor.selected < 0 && editor.output != null) {
            editor.output.setText(StudioTime.format(end));
            editor.source.setText(StudioTime.format(editor.replay.getReplaySender().currentTimeStamp()));
            editor.cameraToFields();
        }
    }
    private ShotModel model() { return new ShotModel(timeline()); }
    private ShotTiming timing() { return new ShotTiming(timeline()); }
    private java.util.SortedSet<Long> visibleShotTimes() { return ShotGroups.visibleTimes(timeline(),showSamples); }
    public long automaticShotStart() { return model().times().isEmpty()?0:Math.min(Integer.MAX_VALUE,model().times().last()+50); }
    private ShotTiming.Span activeSpan() { return draggedRange != null ? new ShotTiming.Span(draggedRange.start, draggedRange.end, rangeFrom, rangeTo) : timing().at(timingSelection >= 0 ? timingSelection : selected >= 0 ? selected : cursor); }
    public void timingApplied(long from, long to, boolean preview) {
        timingSelection = from;
        message = ""; pendingSeek = -1; selected = -1; appliedInspector = null;
        mc.displayGuiScreen(this);
        select(from, true);
        if (preview) { pendingSeek = -1; previewEnd = to; syncCursor(); previewWorkspace = this; pathing.playPauseButton.onClick(); }
    }
    public void focusReplayClock() { for (GuiTextField field : fields) field.setFocused(false); source.setFocused(true); }
    public void goToMarker(com.replaymod.replaystudio.data.Marker marker) {
        seekSource(marker.getTime());
        CameraProperties.POSITION.applyToGame(Triple.of(marker.getX(), marker.getY(), marker.getZ()), replay);
        CameraProperties.ROTATION.applyToGame(Triple.of(marker.getYaw(), marker.getPitch(), marker.getRoll()), replay);
        selected = -1; mod.setSelected(null, 0); cameraToFields();
    }
    public static void captureCurrentShot(ReplayHandler replay) {
        captureCurrentShot(replay, false);
    }
    public static void captureCurrentShot(ReplayHandler replay, boolean ending) {
        ReplayModSimplePathing mod = ReplayModSimplePathing.instance;
        CameraEntity camera = replay.getCameraEntity();
        if (camera == null) return;
        if (replay.isEditorSeeking() || replay.getReplaySender() instanceof com.replaymod.replay.FullReplaySender && ((com.replaymod.replay.FullReplaySender) replay.getReplaySender()).isHurrying()) {
            mod.getCore().printWarningToChat("Wait for the replay seek to finish before capturing a shot."); return;
        }
        long time = mod.getGuiPathing().timeline.getCursorPosition();
        try {
            CinematicEditor editor = replay.getOverlay().getStudioEditor();
            long captured = editor.capturePose(time, ending);
            mod.getGuiPathing().timeline.setCursorPosition((int) captured);
            mod.setSelected(SPPath.POSITION, captured);
            editor.capturedShot = captured; editor.cursor = captured;
            mod.getCore().printInfoToChat("Captured camera and replay time at video " + StudioTime.format(captured));
        } catch (IllegalArgumentException invalid) { mod.getCore().printWarningToChat(invalid.getMessage()); }
    }
    @Override protected void build() {
        if (originalHideGui == null) originalHideGui = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
        markers = replay.getOverlay().timeline.getStudioMarkers();
        if (fullscreenViewer) {
            return;
        }
        String[] draft = output == null ? null : new String[]{output.getText(), source.getText(), x.getText(), y.getText(), z.getText(), yaw.getText(), pitch.getText(), roll.getText(), step.getText(), duration.getText()};
        button(1, 8, 10, 92, "Replay library"); button(2, 108, 10, 100, "Fly camera [Esc]");
        button(6, 216, 10, 92, "Saved paths"); button(4, 316, 10, 56, "Undo"); button(5, 380, 10, 56, "Redo");
        button(37, 444, 10, 108, "Auto shots [G]"); button(25, 560, 10, 88, "Markers"); button(24, 656, 10, 88, "Path lines");
        button(31, 752, 10, 80, "Reset layout"); button(30, 840, 10, 108, "Fullscreen");
        button(7, canvasWidth - 164, 10, 156, "Export video");
        button(36, layout.previewLeft + 134, 47, 90, preferences.fillViewer ? "View: Fill" : "View: Fit");
        button(38, layout.previewLeft + 230, 47, 106, showSamples?"Samples: On":"Samples: Off");
        int left = layout.inspectorLeft + 12, usable = layout.inspectorWidth - 24;
        int column = (usable - 12) / 3, half = (usable - 6) / 2;
        output = field(left, 82, usable - 60, StudioTime.format(cursor)); button(8, left + usable - 54, 81, 54, "Go");
        source = field(left, 124, usable - 60, StudioTime.format(replay.getReplaySender().currentTimeStamp())); button(9, left + usable - 54, 123, 54, "Go");
        x = field(left, 170, column, "0"); y = field(left + column + 6, 170, column, "0"); z = field(left + (column + 6) * 2, 170, column, "0");
        yaw = field(left, 212, column, "0"); pitch = field(left + column + 6, 212, column, "0"); roll = field(left + (column + 6) * 2, 212, column, "0");
        button(10, left, 242, usable, "Use current camera"); button(11, left, 268, usable, "Apply values / update shot");
        button(12, left, 294, usable, "Camera move: Smooth");
        button(13, left, 320, half, "Delete shot"); button(14, left + half + 6, 320, half, "New shot");
        button(27, left, 348, usable, "Timing / speed..."); button(26, left, 374, usable, "Whole path: normal speed 1x");
        button(23, left, 400, usable, freezeAction ? "New steps: frozen action" : "New steps: advancing action");
        int library = layout.libraryWidth - 24, bottom = layout.timelineTop;
        button(20, 12, bottom - 130, (library - 6) / 2, "Previous"); button(21, 15 + library / 2, bottom - 130, (library - 6) / 2, "Next");
        button(22, 12, bottom - 104, library, "Replay: play / pause");
        step = field(12 + library - 64, bottom - 77, 64, "2.000");
        button(15, 12, bottom - 52, (library - 6) / 2, "Add [I]");
        button(35, 18 + (library - 6) / 2, bottom - 52, (library - 6) / 2, "End [O]");
        button(16, 12, bottom - 26, library, "Next slot + step");
        button(3, 14, bottom + 12, 112, "Play / pause [Space]"); button(34, 134, bottom + 12, 64, "Stop [K]");
        button(18, canvasWidth - 438, bottom + 12, 52, "Zoom +"); button(19, canvasWidth - 380, bottom + 12, 52, "Zoom -");
        button(28, canvasWidth - 322, bottom + 12, 82, "Fit [Shift+Z]");
        duration = field(canvasWidth - 232, bottom + 13, 110, StudioTime.format(visibleDuration));
        button(17, canvasWidth - 114, bottom + 12, 100, "Set zoom span");
        cameraToFields();
        if (draft == null && selected >= 0) select(selected, false);
        if (draft != null) {
            GuiTextField[] inputs = {output, source, x, y, z, yaw, pitch, roll, step, duration};
            for (int i = 0; i < inputs.length; i++) inputs[i].setText(draft[i]);
        }
        if (capturedShot >= 0) { select(capturedShot, true); capturedShot = -1; }
        updateMovementButton();
        if (!settingsLoaded) try {
            settingsLoaded = true;
            java.nio.file.Path settings = com.replaymod.render.ReplayModRender.instance.getRenderSettingsPath();
            if (java.nio.file.Files.isRegularFile(settings)) {
                com.google.gson.JsonObject saved = new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(settings), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if (saved.has("videoWidth") && saved.has("videoHeight")) {
                    double aspect = saved.get("videoWidth").getAsDouble() / saved.get("videoHeight").getAsDouble();
                    if (Double.isFinite(aspect) && aspect > 0) viewerAspect = aspect;
                }
                if (saved.has("framesPerSecond")) previewFps = Math.max(1, Math.min(240, saved.get("framesPerSecond").getAsInt()));
            }
        } catch (java.io.IOException | RuntimeException ignored) { }
    }
    private void updateEditorFrame() {
        if (ReplayModReplay.instance.getReplayHandler() != replay) { mc.displayGuiScreen(null); return; }
        if (pathing.isPreviewPlaying()) {
            cursor = Math.max(0, pathing.getPreviewTime());
            if (previewEnd >= 0 && cursor >= previewEnd) { pathing.stopPreview(); cursor = previewEnd; previewEnd = -1; }
            syncCursor();
            if (followPlayhead && panTrack == 0 && (cursor > visibleStart + visibleDuration || cursor < visibleStart)) visibleStart = Math.max(0, cursor - visibleDuration / 10);
            if (selected < 0 && !output.isFocused() && !source.isFocused() && !x.isFocused() && !y.isFocused() && !z.isFocused() && !yaw.isFocused() && !pitch.isFocused() && !roll.isFocused()) {
                output.setText(StudioTime.format(cursor)); source.setText(StudioTime.format(replay.getReplaySender().currentTimeStamp())); cameraToFields();
            }
        }
        previewTypedPose();
        if (pendingSeek >= 0 && panTrack == 0 && rangeHandle < 0) {
            long target = pendingSeek; boolean cameraPath = pendingTimelineSeek; pendingSeek = -1;
            attempt(() -> queueSeek(target, cameraPath, !cameraPath || selected < 0));
        }
        if (replay.isEditorSeeking() && dragTrack == 0 && replay.editorSeekTarget() >= 0) replay.requestEditorSeek(replay.editorSeekTarget(), false, this);
    }
    @Override protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        drawRect(-1, -1, canvasWidth + 1, canvasHeight + 1, 0xFF0D131A);
        if (fullscreenViewer) {
            viewer.draw(layout.viewerArea(true), preferences.fillViewer);
            return;
        }
        panel(0, 0, canvasWidth, 40, "");
        panel(0, 40, layout.libraryWidth, layout.timelineTop - 40, "SHOT LIBRARY");
        panel(layout.inspectorLeft, 40, layout.inspectorWidth, layout.timelineTop - 40, selected >= 0 ? "SHOT INSPECTOR" : "NEW SHOT");
        panel(layout.previewLeft, 40, canvasWidth - layout.previewLeft, layout.timelineTop - 40, "CAMERA VIEWER");
        panel(0, layout.timelineTop, canvasWidth, canvasHeight - layout.timelineTop, "");
        int[] image = layout.viewerArea(false);
        drawRect(image[0], image[1], image[0] + image[2], image[1] + image[3], 0xFF05080C);
        viewer.draw(image, preferences.fillViewer);
        label(replay.isEditorSeeking() ? replay.isEditorSeekRunning() ? "UPDATING PREVIEW..." : "REWIND ON RELEASE" : pathing.isPreviewPlaying() ? "PLAYING" : "PAUSED", canvasWidth - 138, 52, pathing.isPreviewPlaying() ? 0xFF80D4A3 : MUTED);
        int left = layout.inspectorLeft + 12, usable = layout.inspectorWidth - 24, column = (usable - 12) / 3;
        label("Output time (video)", left, 68, MUTED); label("Replay time (recorded action)", left, 110, MUTED);
        label("X", left, 156, MUTED); label("Y", left + column + 6, 156, MUTED); label("Z", left + (column + 6) * 2, 156, MUTED);
        label("Yaw", left, 198, MUTED); label("Pitch", left + column + 6, 198, MUTED); label("Roll", left + (column + 6) * 2, 198, MUTED);
        label("Enter: apply | Ctrl+Z: undo", left, 429, MUTED);
        label("Step (seconds)", 12, layout.timelineTop - 73, MUTED);
        List<Long> times = new ArrayList<>(visibleShotTimes());
        for (int row = 0; row < layout.shotRows && row + listOffset < times.size(); row++) {
            long time = times.get(row + listOffset); int top = 78 + row * 26;
            drawRect(8, top - 2, layout.libraryWidth - 8, top + 23, time == selected ? 0xFF29425A : 0xFF18232E);
            label(String.format(Locale.ROOT, "%02d  %s", row + listOffset + 1, StudioTime.format(time)), 14, top, time == selected ? TEXT : MUTED);
            Integer stamp = timeline().getTimePath().getValue(TimestampProperty.PROPERTY, time).orElse(null);
            label(stamp == null ? "Replay: unset" : "Replay " + StudioTime.format(stamp), 22, top + 12, MUTED);
        }
        if (times.isEmpty()) {
            label("1. Fly to start [Esc]", 12, 84, TEXT); label("2. I: capture start", 12, 104, TEXT);
            label("3. Fly to end, press O", 12, 124, TEXT); label("4. T: play and export", 12, 144, TEXT);
        }
        ShotTiming.Span active = activeSpan();
        label("VIDEO " + StudioTime.format(cursor), 210, layout.timelineTop + 18, TEXT);
        label(active == null ? "Capture two shots" : active.rateLabel(), 380, layout.timelineTop + 18, ACCENT);
        label("REPLAY", 14, layout.replayTop + 18, MUTED); label("CAMERA", 14, layout.videoTop + 18, ACCENT);
        drawTrack(TRACK_LEFT, layout.replayTop, layout.trackWidth, TRACK_HEIGHT, sourceStart, sourceDuration, pendingSeek >= 0 ? pendingSeek : replay.isEditorSeeking() ? Math.max(0, replay.editorSeekTarget()) : replay.getReplaySender().currentTimeStamp(), false);
        drawTrack(TRACK_LEFT, layout.videoTop, layout.trackWidth, TRACK_HEIGHT, visibleStart, visibleDuration, cursor, true);
        drawTimeMapping(active, mouseX, mouseY);
        if (layout.laneBottom > layout.videoTop + TRACK_HEIGHT + 4) {
            drawRect(TRACK_LEFT, layout.videoTop + TRACK_HEIGHT + 2, TRACK_LEFT + layout.trackWidth, layout.laneBottom, 0xFF131F2A);
            for (ShotTiming.Span span : timing().spans()) {
                int from = TimelineGraphics.pixel(span.start, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth), to = TimelineGraphics.pixel(span.end, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth);
                if (to <= from) continue;
                drawRect(from, layout.videoTop + TRACK_HEIGHT + 4, to, layout.laneBottom - 2, span == active || active != null && span.start == active.start ? 0xFF365E7E : 0xFF233D52);
                if (to - from > 100 && layout.laneBottom >= layout.videoTop + TRACK_HEIGHT + 18) label(span.rateLabel(), from + 6, layout.videoTop + TRACK_HEIGHT + 9, TEXT);
            }
        }
        drawOverview();
        label("Drag lane: pan | Ruler: scrub | Keys: move | Green range: slide action | Ctrl+wheel: zoom | Middle-drag: pan", 14, canvasHeight - 14, MUTED);
        int hover = layout.splitter(mouseX, mouseY);
        drawRect(layout.libraryWidth, 40, layout.libraryWidth + 4, layout.timelineTop, hover == 1 || dragSplitter == 1 ? ACCENT : EDGE);
        drawRect(layout.previewLeft - 4, 40, layout.previewLeft, layout.timelineTop, hover == 2 || dragSplitter == 2 ? ACCENT : EDGE);
        drawRect(0, layout.timelineTop - 2, canvasWidth, layout.timelineTop + 2, hover == 3 || dragSplitter == 3 ? ACCENT : EDGE);
        boolean seeking = seekInProgress();
        for (Object value : buttonList) {
            net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) value;
            if (button.id == 15 || button.id == 35) { button.enabled = !seeking; button.displayString = seeking ? "Updating..." : button.id == 15 ? "Add [I]" : "End [O]"; }
            if (button.id == 2 || button.id == 3 || button.id == 7 || button.id == 22) button.enabled = !seeking;
            if (button.id == 3) button.displayString = pathing.isPreviewPlaying() ? "Pause [Space]" : "Play [Space]";
            if (button.id == 24) button.displayString = "Path lines: " + (mod.getCore().getSettingsRegistry().get(com.replaymod.simplepathing.Setting.PATH_PREVIEW) ? "On" : "Off");
        }
    }
    private long overviewDuration() { return Math.max(visibleStart + visibleDuration, Math.max(10000, model().times().isEmpty() ? 0 : model().times().last() + 1000)); }
    private void drawOverview() {
        drawRect(TRACK_LEFT, layout.panTop, TRACK_LEFT + layout.trackWidth, layout.panTop + 12, 0xFF1B2835);
        int from = TimelineGraphics.pixel(visibleStart, 0, overviewDuration(), TRACK_LEFT, layout.trackWidth), to = TimelineGraphics.pixel(visibleStart + visibleDuration, 0, overviewDuration(), TRACK_LEFT, layout.trackWidth);
        drawRect(from, layout.panTop + 1, Math.max(from + 6, to), layout.panTop + 11, ACCENT);
        label("VIEW", 14, layout.panTop + 2, MUTED);
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
        if (fullscreenViewer) return;
        if (hit(x, y, 0, 72, layout.libraryWidth, layout.timelineTop - 204)) listOffset = Math.max(0, Math.min(Math.max(0, visibleShotTimes().size() - 1), listOffset + (amount > 0 ? -1 : 1)));
        else if (hit(x, y, TRACK_LEFT, layout.replayTop, layout.trackWidth, TRACK_HEIGHT)) navigateClock(x, amount, true);
        else if (hit(x, y, TRACK_LEFT, layout.videoTop, layout.trackWidth, layout.laneBottom - layout.videoTop)) navigateClock(x, amount, false);
    }
    private void navigateClock(int x, int amount, boolean recorded) {
        long start = recorded ? sourceStart : visibleStart, span = recorded ? sourceDuration : visibleDuration;
        long limit = recorded ? Math.max(1, replay.getReplayDuration()) : Integer.MAX_VALUE;
        if (isCtrlKeyDown()) {
            long next = StudioTimelineView.zoomDuration(span, amount > 0, limit);
            start = StudioTimelineView.zoomStart(start, span, next, Math.max(0, Math.min(1, (x - TRACK_LEFT) / (double) layout.trackWidth)), limit);
            span = next;
        } else start = StudioTimelineView.bound(start + (amount > 0 ? -1 : 1) * Math.max(1, span / 8), span, limit);
        if (recorded) { sourceStart = start; sourceDuration = span; }
        else { visibleStart = start; visibleDuration = span; followPlayhead = false; duration.setText(StudioTime.format(span)); }
    }
    private void drawTrack(int left, int top, int width, int height, long start, long range, long playhead, boolean shots) {
        drawRect(left, top, left + width, top + height, 0xFF1B2835);
        ShotTiming.Span active = activeSpan();
        if (active != null) {
            int from = TimelineGraphics.pixel(shots ? active.start : active.sourceStart, start, range, left, width);
            int to = TimelineGraphics.pixel(shots ? active.end : active.sourceEnd, start, range, left, width);
            drawRect(from, top + 16, Math.max(from + 1, to), top + height, shots ? 0x8029425A : 0x80395A48);
        }
        for (int i = 0; i <= 8; i++) {
            int px = left + i * width / 8;
            drawRect(px, top + 15, px + 1, top + height, EDGE);
            label(StudioTime.format(start + range * i / 8), Math.min(px + 2, left + width - 44), top + 2, MUTED);
        }
        if (shots) for (long time : visibleShotTimes()) {
            if (time < start || time > start + range) continue;
            int px = left + (int) ((time - start) * width / range);
            drawRect(px - 3, top + 20, px + 4, top + height, time == selected ? 0xFFFFCB82 : ACCENT);
        }
        if (!shots && markers != null) for (com.replaymod.replaystudio.data.Marker marker : markers) {
            if (marker.getTime() < start || marker.getTime() > start + range) continue;
            int mx = TimelineGraphics.pixel(marker.getTime(), start, range, left, width);
            drawRect(mx - 2, top + height - 6, mx + 3, top + height, 0xFF80E080);
        }
        java.util.Set<Long> clockKeys=!shots && !showSamples?visibleShotTimes():null;
        if (!shots) for (Keyframe key : timeline().getTimePath().getKeyframes()) {
            if(clockKeys!=null && !clockKeys.contains(key.getTime())) continue;
            key.getValue(TimestampProperty.PROPERTY).ifPresent(stamp -> {
                if (stamp < start || stamp > start + range) return;
                int kx = TimelineGraphics.pixel(stamp, start, range, left, width);
                drawRect(kx - 1, top + 20, kx + 2, top + height, TimelineGraphics.RANGE);
            });
        }
        int px = left + (int) ((playhead - start) * width / Math.max(1, range));
        if (px >= left && px <= left + width) drawRect(px, top, px + 2, top + height, 0xFFFFCB82);
        if (shots && dragKey >= 0 && actuallyDragging) {
            int dx = left + (int) ((dragTarget - start) * width / range);
            drawRect(dx, top, dx + 2, top + height, 0xFF80E080);
        }
    }
    private void drawTimeMapping(ShotTiming.Span span, int mouseX, int mouseY) {
        if (span == null) return;
        int sourceIn = TimelineGraphics.pixel(span.sourceStart, sourceStart, sourceDuration, TRACK_LEFT, layout.trackWidth);
        int sourceOut = TimelineGraphics.pixel(span.sourceEnd, sourceStart, sourceDuration, TRACK_LEFT, layout.trackWidth);
        int videoIn = TimelineGraphics.pixel(span.start, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth);
        int videoOut = TimelineGraphics.pixel(span.end, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth);
        boolean hover = hit(mouseX, mouseY, TRACK_LEFT, layout.replayTop + 16, layout.trackWidth, TRACK_HEIGHT - 16)
                && TimelineGraphics.rangeHandle(mouseX, sourceIn, sourceOut, false) >= 0;
        drawRect(sourceIn, layout.replayTop + 16, Math.max(sourceIn + 1, sourceOut), layout.replayTop + TRACK_HEIGHT,
                hover || rangeHandle == 2 ? 0xA059996A : 0x50395A48);
        if (sourceOut - sourceIn > 120) label(rangeHandle == 2 ? "Sliding action" : "Drag action", sourceIn + 10, layout.replayTop + 25, hover ? TEXT : 0xFFACD8B8);
        TimelineGraphics.connect(sourceIn, sourceOut, layout.replayTop + TRACK_HEIGHT, videoIn, videoOut, layout.videoTop);
        if (span.sourceStart >= sourceStart && span.sourceStart <= sourceStart + sourceDuration) drawRect(sourceIn - 2, layout.replayTop + 16, sourceIn + 2, layout.replayTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        if (span.sourceEnd >= sourceStart && span.sourceEnd <= sourceStart + sourceDuration) drawRect(sourceOut - 2, layout.replayTop + 16, sourceOut + 2, layout.replayTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        if (span.start >= visibleStart && span.start <= visibleStart + visibleDuration) drawRect(videoIn - 1, layout.videoTop - 2, videoIn + 1, layout.videoTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        if (span.end >= visibleStart && span.end <= visibleStart + visibleDuration) drawRect(videoOut - 1, layout.videoTop - 2, videoOut + 1, layout.videoTop + TRACK_HEIGHT + 2, TimelineGraphics.RANGE);
        label("IN " + StudioTime.format(span.sourceStart) + "    OUT " + StudioTime.format(span.sourceEnd) + "    " + span.rateLabel(), TRACK_LEFT + 4, layout.replayTop - 12, TEXT);
    }
    @Override protected void clicked(int mx, int my, int button) {
        if (fullscreenViewer) return;
        if (button == 0 && layout.splitter(mx, my) != 0) { dragSplitter = layout.splitter(mx, my); return; }
        if (button == 1 && hit(mx, my, TRACK_LEFT, layout.videoTop, layout.trackWidth, layout.laneBottom - layout.videoTop)) {
            requireAppliedInspector(); pathing.stopPreview(); previewWorkspace = null;
            mc.displayGuiScreen(new StudioShotTiming(this, replay, videoTime(mx))); return;
        }
        if (button == 0 && hit(mx, my, 8, 76, layout.libraryWidth - 16, layout.shotRows * 26)) for (int row = 0; row < layout.shotRows; row++) {
            List<Long> times = new ArrayList<>(visibleShotTimes());
            if (row + listOffset < times.size() && hit(mx, my, 8, 76 + row * 26, layout.libraryWidth - 16, 25)) {
                requireAppliedInspector(); message = ""; pathing.stopPreview(); select(times.get(row + listOffset), true); return;
            }
        }
        if (button == 0 && hit(mx, my, TRACK_LEFT, layout.panTop, layout.trackWidth, 12)) {
            panTrack = 3; panMouseButton = button; panAnchorX = mx; panMoved = false; panDomain = overviewDuration();
            int from = TimelineGraphics.pixel(visibleStart, 0, panDomain, TRACK_LEFT, layout.trackWidth), to = TimelineGraphics.pixel(visibleStart + visibleDuration, 0, panDomain, TRACK_LEFT, layout.trackWidth);
            if (mx < from || mx > to) visibleStart = StudioTimelineView.bound(TimelineGraphics.time(mx, 0, panDomain, TRACK_LEFT, layout.trackWidth) - visibleDuration / 2, visibleDuration, Integer.MAX_VALUE);
            panAnchorStart = visibleStart; followPlayhead = false; return;
        }
        boolean sourceLane = hit(mx, my, TRACK_LEFT, layout.replayTop, layout.trackWidth, TRACK_HEIGHT);
        boolean videoLane = hit(mx, my, TRACK_LEFT, layout.videoTop, layout.trackWidth, layout.laneBottom - layout.videoTop);
        if (!sourceLane && !videoLane || button != 0 && button != 2) return;
        int track = sourceLane ? 1 : 2, top = sourceLane ? layout.replayTop : layout.videoTop;
        if (button == 2) { beginPan(mx, button, track); return; }
        long playhead = sourceLane ? pendingSeek >= 0 ? pendingSeek : replay.isEditorSeeking() ? replay.editorSeekTarget() : replay.getReplaySender().currentTimeStamp() : cursor;
        long viewStart = sourceLane ? sourceStart : visibleStart, viewDuration = sourceLane ? sourceDuration : visibleDuration;
        boolean yellowLine = playhead >= viewStart && playhead <= viewStart + viewDuration && Math.abs(mx - TimelineGraphics.pixel(playhead, viewStart, viewDuration, TRACK_LEFT, layout.trackWidth)) <= 3;
        if (my < top + 16 || Keyboard.isCreated() && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU))) {
            requireAppliedInspector(); dragTrack = track; seekAt(mx, track); return;
        }
        if (sourceLane) {
            ShotTiming.Span span = activeSpan();
            if (span != null) {
                int in = TimelineGraphics.pixel(span.sourceStart, sourceStart, sourceDuration, TRACK_LEFT, layout.trackWidth);
                int out = TimelineGraphics.pixel(span.sourceEnd, sourceStart, sourceDuration, TRACK_LEFT, layout.trackWidth);
                int handle = TimelineGraphics.rangeHandle(mx, in, out, Keyboard.isCreated() && isShiftKeyDown());
                if (handle == 0 && (span.sourceStart < sourceStart || span.sourceStart > sourceStart + sourceDuration)) handle = -1;
                if (handle == 1 && (span.sourceEnd < sourceStart || span.sourceEnd > sourceStart + sourceDuration)) handle = -1;
                if (handle >= 0) {
                    requireAppliedInspector(); pathing.stopPreview(); previewEnd = -1; message = "";
                    // Editing this range owns the pointer. An old scrub must not
                    // keep rebuilding the world or change the active move beneath it.
                    replay.cancelEditorSeek(); pendingSeek = soughtSource = -1;
                    dragTrack = 0; panTrack = 0; dragKey = -1; timingSelection = span.start;
                    draggedRange = span; rangeFrom = span.sourceStart; rangeTo = span.sourceEnd; rangeHandle = handle;
                    dragStartX = mx; actuallyDragging = false; return;
                }
            }
        } else if (my < top + TRACK_HEIGHT) {
            long time = videoTime(mx);
            for (long key : visibleShotTimes()) if (key >= visibleStart && key <= visibleStart + visibleDuration && Math.abs(key - time) * layout.trackWidth / visibleDuration <= 7) {
                requireAppliedInspector(); pathing.stopPreview(); previewEnd = -1; message = "";
                select(key, false); dragKey = key; dragTarget = key; dragStartX = mx; actuallyDragging = false; return;
            }
        }
        if (yellowLine) { seekAt(mx, track); return; }
        beginPan(mx, button, track);
    }
    private void beginPan(int x, int button, int track) {
        panTrack = track; panMouseButton = button; panAnchorX = x;
        panAnchorStart = track == 1 ? sourceStart : visibleStart; panMoved = false;
    }
    private void seekAt(int x, int track) {
        requireAppliedInspector(); pathing.stopPreview(); previewWorkspace = null; previewEnd = -1; message = "";
        ShotTiming.Span active = activeSpan();
        if (active != null) timingSelection = active.start;
        selected = -1; mod.setSelected(null, 0); dragTrack = track; scrub(x); updateMovementButton();
    }
    @Override protected void dragged(int mx, int my, int button) {
        if (dragSplitter != 0 && button == 0) {
            if (dragSplitter == 1) preferences.library = mx / (double) canvasWidth;
            if (dragSplitter == 2) preferences.inspector = (mx - layout.inspectorLeft) / (double) canvasWidth;
            if (dragSplitter == 3) preferences.timeline = 1 - my / (double) canvasHeight;
            relayout(); return;
        }
        if (panTrack != 0 && button == panMouseButton) {
            if (!panMoved && Math.abs(mx - panAnchorX) < 4) return;
            panMoved = true; followPlayhead = false;
            if (panTrack == 1) sourceStart = StudioTimelineView.pan(panAnchorStart, sourceDuration, mx - panAnchorX, layout.trackWidth, Math.max(1, replay.getReplayDuration()));
            else if (panTrack == 3) visibleStart = StudioTimelineView.bound(panAnchorStart + Math.round((mx - panAnchorX) * (double) panDomain / layout.trackWidth), visibleDuration, Integer.MAX_VALUE);
            else visibleStart = StudioTimelineView.pan(panAnchorStart, visibleDuration, mx - panAnchorX, layout.trackWidth, Integer.MAX_VALUE);
            return;
        }
        if (button != 0) return;
        if (rangeHandle >= 0) {
            message = "";
            if (actuallyDragging || Math.abs(mx - dragStartX) >= 4) {
                actuallyDragging = true;
                int stamp = (int) sourceTime(mx);
                if (rangeHandle == 2) {
                    long anchor = sourceTime(dragStartX);
                    ShotTiming.Span shifted = timing().slide(draggedRange, stamp - anchor, replay.getReplayDuration()).after;
                    rangeFrom = shifted.sourceStart; rangeTo = shifted.sourceEnd;
                } else if (rangeHandle == 0) rangeFrom = stamp; else rangeTo = stamp;
            }
        } else if (dragKey >= 0) {
            if (actuallyDragging || Math.abs(mx - dragStartX) >= 4) { actuallyDragging = true; dragTarget = videoTime(mx); }
        } else if (dragTrack != 0) scrub(mx);
    }
    @Override protected void cancelledDrag() {
        dragTrack = dragSplitter = panTrack = 0;
        rangeHandle = -1; draggedRange = null; dragKey = -1; actuallyDragging = false;
    }
    @Override protected void released(int mx, int my, int button) {
        if (dragSplitter != 0 && button == 0) { dragged(mx, my, button); dragSplitter = 0; saveLayout(); return; }
        if (panTrack != 0 && button == panMouseButton) {
            attempt(() -> dragged(mx, my, button));
            int track = panTrack; boolean click = !panMoved && button == 0; panTrack = 0;
            if (click && track != 3) attempt(() -> seekAt(mx, track));
            dragTrack = 0; return;
        }
        if (button != 0) return;
        if (rangeHandle >= 0 || dragKey >= 0) attempt(() -> dragged(mx, my, button));
        if (rangeHandle >= 0) {
            ShotTiming.Span span = draggedRange; int handle = rangeHandle, from = rangeFrom, to = rangeTo; boolean changed = actuallyDragging;
            rangeHandle = -1; draggedRange = null; actuallyDragging = false;
            attempt(() -> {
                if (changed && (from != span.sourceStart || to != span.sourceEnd)) {
                    timing().apply(handle == 2 ? timing().slide(span, (long) from - span.sourceStart, replay.getReplayDuration()) : timing().plan(span, from, to, span.duration(), replay.getReplayDuration()));
                    select(handle == 1 ? span.end : span.start, true);
                }
            });
        }
        if (dragKey >= 0) {
            message = ""; long from = dragKey, to = dragTarget; dragKey = -1; actuallyDragging = false;
            attempt(() -> { if (from != to) move(from, to); select(to, true); });
        }
        dragTrack = 0;
    }
    private long sourceTime(int x) { return TimelineGraphics.time(x, sourceStart, sourceDuration, TRACK_LEFT, layout.trackWidth); }
    private long videoTime(int mx) { return TimelineGraphics.time(mx, visibleStart, visibleDuration, TRACK_LEFT, layout.trackWidth); }
    private void scrub(int mx) {
        pathing.stopPreview();
        if (dragTrack == 1) {
            pendingSeek = sourceTime(mx);
            pendingTimelineSeek = false;
        }
        else { cursor = videoTime(mx); output.setText(StudioTime.format(cursor)); previewAt(cursor); cameraToFields(); }
    }
    private void seekSource(long time) {
        seekSource(time, true);
    }
    private void seekSource(long time, boolean updateInspector) {
        pendingSeek = -1;
        queueSeek(time, false, updateInspector);
    }
    private void queueSeek(long time, boolean cameraPath, boolean updateInspector) {
        if (time < 0 || time > replay.getReplayDuration()) throw new IllegalArgumentException("Replay time is outside this recording.");
        replay.getReplaySender().setReplaySpeed(0);
        replay.requestEditorSeek((int) time, dragTrack != 0, this);
        soughtSource = time; seekCameraPath = cameraPath;
        if (updateInspector) source.setText(StudioTime.format(time));
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
        timingSelection = time;
        selected = cursor = time;
        if (time < visibleStart || time > visibleStart + visibleDuration) visibleStart = Math.max(0, time - visibleDuration / 10);
        List<Long> times = new ArrayList<>(visibleShotTimes());
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
    private long capturePose(long time, boolean ending) {
        CameraEntity camera = replay.getCameraEntity();
        if (camera == null) throw new IllegalStateException("Wait for the replay camera to finish loading.");
        long advance = step == null ? 2000 : StudioTime.parse(step.getText());
        return model().capture(time, advance, replay.getReplaySender().currentTimeStamp(), ending, !freezeAction, replay.getReplayDuration(),
                camera.posX, camera.posY, camera.posZ, camera.rotationYaw, camera.rotationPitch, camera.roll);
    }
    private void captureNewShot(boolean ending) {
        if (seekInProgress()) throw new IllegalStateException("Wait for the replay seek to finish before capturing a shot.");
        CameraEntity camera = replay.getCameraEntity();
        if (camera == null) throw new IllegalStateException("Wait for the replay camera to finish loading.");
        long time = capturePose(StudioTime.parse(output.getText()), ending);
        // A failed capture leaves selection and all inspector drafts untouched.
        select(time, true); syncCursor();
        message = "Camera saved at video " + StudioTime.format(time) + ", replay " + source.getText() + ". Fly to another view and press O for the ending camera, or I to add another point.";
    }
    private boolean seekInProgress() {
        return replay.isEditorSeeking() || replay.getReplaySender() instanceof com.replaymod.replay.FullReplaySender && ((com.replaymod.replay.FullReplaySender) replay.getReplaySender()).isHurrying();
    }
    private void move(long from, long to) {
        if(!showSamples && ShotGroups.boundary(timeline(),from,"end")) {
            ShotTiming.Span span=timing().at(Math.max(0,from-1));
            timing().apply(timing().plan(span,span.sourceStart,span.sourceEnd,to-span.start,replay.getReplayDuration()));
            return;
        }
        if(!showSamples && ShotGroups.boundary(timeline(),from,"start"))
            throw new IllegalArgumentException("Use Timing / speed to retime this generated shot. Enable Samples to edit individual camera points.");
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
        if (replay.isEditorSeeking() && (id == 1 || id == 2 || id == 3 || id == 6 || id == 7 || id == 15 || id == 16 || id == 22 || id == 25 || id == 27 || id == 35 || id == 37)) throw new IllegalStateException("The preview is catching up. You can keep scrubbing; playback, capture and export are available once it finishes.");
        if (id == 1 || id == 6 || id == 14 || id == 15 || id == 16 || id == 25 || id == 26 || id == 27 || id == 34 || id == 35 || id == 37) requireAppliedInspector();
        boolean resumePreview = id == 11 && pathing.isPreviewPlaying();
        long resumeTime = cursor;
        boolean navigation = id == 17 || id == 18 || id == 19 || id == 20 || id == 21 || id == 24 || id == 28 || id == 30 || id == 31 || id == 36 || id == 38;
        if (id != 3 && !navigation) { pathing.stopPreview(); previewEnd = -1; previewWorkspace = null; }
        pendingSeek = -1;
        switch (id) {
            case 37: mc.displayGuiScreen(new StudioAutoShot(this,replay,automaticShotStart())); break;
            case 38: showSamples=!showSamples; listOffset=0; relayout(); break;
            case 1: mc.displayGuiScreen(new ReplayLibrary(this)); break;
            case 2: escape(); break;
            case 3:
                if (!pathing.isPreviewPlaying()) requireAppliedInspector();
                if (timeline().getPositionPath().getSegments().isEmpty() || timeline().getTimePath().getSegments().isEmpty()) throw new IllegalArgumentException("Add at least two shots before previewing.");
                if (pathing.isPreviewPlaying()) cursor = pathing.getPreviewTime();
                previewEnd = -1; syncCursor();
                if (pathing.isPreviewPlaying()) { pathing.stopPreview(); previewWorkspace = null; }
                else { previewWorkspace = this; followPlayhead = true; pathing.playPauseButton.onClick(); }
                break;
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
                if (resumePreview) { pendingSeek = -1; cursor = resumeTime; syncCursor(); previewWorkspace = this; pathing.playPauseButton.onClick(); }
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
            case 13: if (selected >= 0 && (showSamples || !ShotGroups.removeGenerated(timeline(),selected))) model().remove(selected); selected = -1; mod.setSelected(null, 0); break;
            case 14: if (model().times().contains(cursor)) activate(16); selected = -1; cameraToFields(); source.setText(StudioTime.format(replay.getReplaySender().currentTimeStamp())); break;
            case 15:
                captureNewShot(false); break;
            case 35:
                captureNewShot(true); break;
            case 16:
                long advance = StudioTime.parse(step.getText()); if (advance < 10) throw new IllegalArgumentException("Step must be at least 0.01 seconds.");
                cursor = Math.min(Integer.MAX_VALUE, cursor + advance); selected = -1; mod.setSelected(null, 0); syncCursor(); output.setText(StudioTime.format(cursor));
                if (cursor > visibleStart + visibleDuration) visibleStart = Math.max(0, cursor - visibleDuration / 10);
                if (!freezeAction) seekSource(Math.min(replay.getReplayDuration(), replay.getReplaySender().currentTimeStamp() + advance)); break;
            case 17: visibleDuration = Math.max(100, StudioTime.parse(duration.getText())); visibleStart = StudioTimelineView.bound(visibleStart, visibleDuration, Integer.MAX_VALUE); break;
            case 18: visibleDuration = Math.max(100, visibleDuration / 2); visibleStart = StudioTimelineView.bound(cursor - visibleDuration / 2, visibleDuration, Integer.MAX_VALUE); duration.setText(StudioTime.format(visibleDuration)); break;
            case 19: visibleDuration = Math.min(Integer.MAX_VALUE, visibleDuration * 2); visibleStart = StudioTimelineView.bound(cursor - visibleDuration / 2, visibleDuration, Integer.MAX_VALUE); duration.setText(StudioTime.format(visibleDuration)); break;
            case 20: listOffset = Math.max(0, listOffset - layout.shotRows); break;
            case 21: listOffset = Math.min(Math.max(0, visibleShotTimes().size() - 1), listOffset + layout.shotRows); break;
            case 22: replay.releaseEditorSender(); replay.getReplaySender().setReplaySpeed(replay.getReplaySender().paused() ? 1 : 0); break;
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
                sourceStart = 0; sourceDuration = Math.max(1, replay.getReplayDuration()); followPlayhead = true;
                duration.setText(StudioTime.format(visibleDuration)); break;
            case 30: toggleViewer(); break;
            case 31: preferences = new StudioWorkspacePreferences(); relayout(); saveLayout(); break;
            case 34:
                // The shared action preamble stops playback before applying the
                // first frame, so a finishing player cannot overwrite the rewind.
                selected = -1; timingSelection = -1; appliedInspector = null;
                mod.setSelected(null, 0);
                previewAt(0);
                output.setText(StudioTime.format(0));
                timeline().getTimePath().getValue(TimestampProperty.PROPERTY, 0).ifPresent(value -> source.setText(StudioTime.format(value)));
                cameraToFields();
                visibleStart = 0; followPlayhead = true;
                break;
            case 36: preferences.fillViewer = !preferences.fillViewer; relayout(); saveLayout(); break;
            default: break;
        }
        if (mc.currentScreen == this) updateMovementButton();
    }
    private void toggleViewer() {
        fullscreenViewer = !fullscreenViewer;
        if (fullscreenViewer) {
            restoreWindowedViewer = !mc.isFullScreen();
            if (restoreWindowedViewer) mc.toggleFullscreen();
        } else restoreViewerWindow();
        relayout();
    }
    private void restoreViewerWindow() {
        boolean restore = restoreWindowedViewer; restoreWindowedViewer = false;
        if (restore && mc.isFullScreen()) mc.toggleFullscreen();
    }
    @Override protected void submit() { activate(11); }
    @Override protected void shortcut(char c, int key) {
        if(key==Keyboard.KEY_G) attempt(() -> activate(37));
        if ((key == Keyboard.KEY_I || key == Keyboard.KEY_O) && Keyboard.isCreated() && Keyboard.isRepeatEvent()) return;
        if (key == Keyboard.KEY_F && isCtrlKeyDown()) { attempt(() -> activate(30)); return; }
        if (key == Keyboard.KEY_SPACE) attempt(() -> activate(3));
        if (key == Keyboard.KEY_K) attempt(() -> activate(34));
        if (key == Keyboard.KEY_I) attempt(() -> activate(15));
        if (key == Keyboard.KEY_O) attempt(() -> activate(35));
        if (key == Keyboard.KEY_Z && isCtrlKeyDown()) attempt(() -> activate(isShiftKeyDown() ? 5 : 4));
        if (key == Keyboard.KEY_Y && isCtrlKeyDown()) attempt(() -> activate(5));
        if (key == Keyboard.KEY_Z && isShiftKeyDown() && !isCtrlKeyDown()) attempt(() -> activate(28));
        if (key == Keyboard.KEY_EQUALS || key == Keyboard.KEY_ADD) attempt(() -> activate(18));
        if (key == Keyboard.KEY_MINUS || key == Keyboard.KEY_SUBTRACT) attempt(() -> activate(19));
        if (key == Keyboard.KEY_DELETE) attempt(() -> activate(13));
        if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) attempt(() -> {
            requireAppliedInspector(); pathing.stopPreview(); previewEnd = -1;
            int direction = key == Keyboard.KEY_LEFT ? -1 : 1;
            cursor = isShiftKeyDown() ? Math.max(0, Math.min(Integer.MAX_VALUE, cursor + direction * 1000)) : StudioTimelineView.stepFrame(cursor, previewFps, direction);
            selected = -1; mod.setSelected(null, 0); output.setText(StudioTime.format(cursor)); previewAt(cursor); cameraToFields();
            if (cursor < visibleStart || cursor > visibleStart + visibleDuration) visibleStart = Math.max(0, cursor - visibleDuration / 2);
        });
        if (key == Keyboard.KEY_HOME || key == Keyboard.KEY_END) attempt(() -> {
            requireAppliedInspector(); pathing.stopPreview(); previewEnd = -1;
            cursor = key == Keyboard.KEY_HOME || model().times().isEmpty() ? 0 : model().times().last();
            selected = -1; mod.setSelected(null, 0); output.setText(StudioTime.format(cursor)); previewAt(cursor); cameraToFields();
            visibleStart = Math.max(0, cursor - visibleDuration / 2);
        });
    }
    @Override protected void escape() {
        if (fullscreenViewer) { toggleViewer(); return; }
        if (replay.isEditorSeeking()) { message = "The preview is catching up. You can keep scrubbing while it updates."; return; }
        previewWorkspace = null;
        pathing.stopPreview();
        pendingSeek = -1;
        syncCursor();
        replay.getOverlay().setMouseVisible(false);
        leavingWorkspace = true;
        try { mc.displayGuiScreen(null); } finally { leavingWorkspace = false; }
    }
    @Override public void onGuiClosed() {
        if (restoreWindowedViewer) { fullscreenViewer = false; restoreViewerWindow(); }
        replay.releaseEditorSender(); settingsLoaded = false;
        viewer.release(); saveLayout();
        if (originalHideGui != null) { mc.gameSettings.hideGUI = originalHideGui; originalHideGui = null; }
        super.onGuiClosed();
    }
}
