# Cinematic studio

WGMap's `MapStyleButton`, fullscreen settings screen and viewport mapping were
read as design references. No WGMap source was changed, copied into its checkout,
compiled, or made a dependency of ReplayMod.

## Interface

The native studio replaces the replay library, settings, editor, saved paths,
scene markers, pause menu, mod-difference warning, output/executable file picker,
video export, render queue, render progress, completed-export screen, screenshot
export, and saved-recording naming dialog. Background recording notifications use
the same panel colours. Existing jGui classes remain as backend/API adapters for
modules that still refer to their timelines or public fields.

The library also exposes native trim/split controls and confirmed recording
deletion. Trimming processes a separate copy through the existing marker backend;
the source recording is preserved. Close active playback before editing files.

The studio draws on a logical canvas with one shared viewport mapping for mouse
clicks, dragging, wheel input and drawing. It does not change Minecraft's global
GUI-scale setting. Editor camera controls stop while text fields own input.
The main editor is an opaque, full-window workspace: shot library at left, shot
inspector beside it, camera viewer at upper right, and timelines across the bottom.
Drag either vertical divider or the horizontal divider above the timeline to
resize those docks. Layout proportions persist in `config/replaymod-workspace.json`;
Reset layout restores the defaults. Minimum dock sizes keep controls accessible.
Wider windows expand the timeline and camera area; taller windows show more shots.
Resize retains inspector, step and zoom-span drafts. Other workflow dialogs retain
their existing proportional layout.

The viewer uses the normal world rendering pipeline at up to 30 frames per second,
including world-render events and DH, into a framebuffer sized to the visible
preview. The final workspace is drawn after render-tick listeners. Game HUD and
GUI screen events are excluded from that preview capture. Vanilla entity camera
post-processing retains its original full-resolution framebuffer and scales the
result into the viewer, preserving its framebuffer references. Export resolution
is independent of dock sizing. Framebuffer support must be enabled for the viewer.
The workspace itself continues drawing and processing held drags at display-frame
frequency while the world viewer is throttled. Fill is the default: it crops the
image centrally to touch the viewer edges without stretching. View: Fit shows the
complete export frame instead. These modes do not change the exported video.
DH's render tree is also ticked while replay time is frozen. This does not advance
the recording or block the interactive editor waiting for LODs.

The preview enters at `EntityRenderer.updateCameraAndRender`, with a capture guard
for its nested normal render. It deliberately does not redirect Minecraft's call
site: Angelica's iconified-window guard already owns that redirect. The texture
composite disables alpha testing, matching vanilla framebuffer display: a world
frame may contain visible RGB while its alpha channel remains zero.

Preview keeps the workspace visible, including through replay restarts. Ctrl+F
explicitly toggles the fullscreen camera viewer; Esc there returns to the editor.
Esc from the editor enters the existing fly-camera mode; T returns to the editor.
The editor retains its step, zoom, freeze mode and drafts when returning from
free-camera movement. Camera properties preview while typing; Apply commits them.
Apply during preview rebuilds playback from the current playhead with the edit.

## Timeline and persistence

- A normal shot stores camera pose and source replay time together. Adding,
  updating, moving, deleting and clearing paired keyframes share one undo step.
- Drag the yellow playhead or numbered ruler to scrub. Camera values follow the
  pointer immediately. Recorded-world seeks are coalesced to the latest requested
  time and processed on the main thread in slices of up to 64 packets / a 3 ms
  budget per frame, including draining the corresponding Minecraft packet handlers.
  A backward drag commits its world rebuild on release, avoiding repeated world
  and DH shutdown/reload cycles while holding the pointer. Camera-path poses can
  still preview against the current world, labelled Rewind on release; recorded
  action catches up when released. Forward scrubs update while dragging. No old
  mouse-target backlog is kept. The paused editor retains the synchronous sender
  between seeks, rather than spawning and stopping a worker for every target.
  The editor and last completed camera texture remain visible while rebuilding;
  its viewer says Updating preview. Loading dialogs and direct dirt-screen drawing
  are suppressed while this replay's editor owns the workspace, including camera
  path restarts as well as scrubs. The export/playback APIs keep
  their exact blocking behaviour. Historical sound events are muted while applying
  a seek; ordinary preview playback and export audio retain their existing path.
  Legacy recordings still require replaying earlier packets to reconstruct a
  backward jump. The frame budget cannot interrupt an individual mod handler,
  world teardown, renderer reload, or JVM collection; it is not an instant random
  access index. Capture/play/export become available once the seek completes.
- Click a keyframe to select it. A small drag threshold prevents retiming from an
  ordinary click. Dragging empty lane space pans its visible time range, and a
  click without dragging seeks. Drag the numbered ruler to scrub immediately.
  Camera-key and red-range grips take priority over an overlapping playhead.
  Grabbing a camera key does not seek; release commits its retime and preview.
  Middle-drag pans anywhere on a lane, including on keys or a selected range.
  Wheel pans horizontally; Ctrl+wheel zooms around the pointer. Each clock has
  independent navigation. The bottom VIEW scrollbar pans the video range, and
  Shift+Z fits the path and resets the replay view. Navigation does not retime
  shots, alter replay In/Out, change selection or add undo entries.
- Hover over a button or input field for half a second to see its description
  and a usage example. Help also works on disabled controls. The popup follows
  the editor's GUI scale and moves inward at window edges. Append's help explains
  that it finds empty video space without changing the replay In/Out times.
- The replay library fills the window, including wide and tall layouts. Recording
  names appear in a compact top-right popup. Click a default name to select it,
  scroll through the stacked entries, and use Save all to apply every row. Red
  trash icons turn from gray to red when marked for deletion; click again to
  restore a row. Escape leaves the
  original files unchanged. New recordings join an already-open naming popup.
- In free flight, P toggles recorded action while keeping the mouse captured.
  Capture is checked again after input processing, just before camera rendering,
  even when replay time is paused. A stale legacy ReplayMod input screen is
  closed; actual menus, other overlays and inactive windows retain their mouse.
  T opens the editor; the command-chat key (normally /) does not. Holding either
  Ctrl key boosts free-camera movement by eight times in Classic and Vanilla
  modes. Releasing Ctrl restores the selected speed.
- Export has a separate Output file name box in addition to the full output
  path. Clicking the name box selects its text for replacement. It shows the name
  without the format extension. A name such as Takeoff keeps the selected folder and adds the current
  video format's extension. PNG output uses the name for its image-sequence
  folder. The replay pause screen uses one compact central panel.
- Space plays/pauses at the current position; K stops and returns the camera and
  replay to video time zero, ready to play the scene again. Left/Right steps one frame at the configured
  export frame rate; Shift+Left/Right steps one second. Home/End seek to video
  zero/the last shot. +/- zoom the camera timeline. Ctrl+Z undoes, Ctrl+Shift+Z
  or Ctrl+Y redoes. Text fields retain ordinary typing and editing shortcuts.
- Time fields accept seconds (`12.5`), minutes (`1:12.500`), or hours
  (`1:02:03.500`). Invalid input remains editable and reports an inline error.
- The first camera capture automatically starts at output zero, including when
  Add Shot, Apply, or free-camera I is used after browsing a later empty output
  slot. Its chosen replay moment and pose are retained. The editor returns the
  output cursor, visible range and shot-list selection to the captured key.
  Subsequent shots use their requested output times. Source time must stay within the
  recording and move forward or remain equal. Equal source times freeze action.
- I captures the start/additional camera; O captures an ending camera. Both work
  while flying and in the editor, with visible Add [I] / End [O] buttons. Capturing
  on an occupied cursor appends after the last camera by Step instead of updating
  its pose. O also advances when the cursor is before the last point. An empty
  later output slot is honoured. In advancing mode, a paused source clock advances
  by the same output duration; frozen mode retains its source time. Updating a
  saved camera remains an explicit Apply operation. O requires a starting camera.
- **Whole path: normal speed 1x** keeps the first shot's recorded moment,
  then maps every subsequent clock key to source-start plus elapsed output time.
  It preserves all camera poses, selects the first shot for preview, is one undo
  step, and rejects paths extending past the recording. It changes the current
  sequence only when clicked. The timeline timing label identifies frozen action.
- **Timing / speed** edits the current camera transition or the whole sequence.
  It provides exact replay In/Out, video duration, two explicit speed behaviours,
  presets, Freeze, a live draft range diagram and Apply + preview. Selected moves
  have red replay handles and red connectors to their video range in the editor.
  Drag a red replay handle to change the recorded-action range; drag a blue video
  key to move that camera/clock key. Right-click the video opens timing controls.
  Timing edits are one undo step; shortening that collapses keys or reverses a
  neighbouring replay clock is rejected before mutation. See
  [workflow audit](timing-workflow-audit.md) for precise semantics and validation.
- **New steps** describes future step creation, rather than claiming that existing
  path clocks are already advancing. Preview/export reject unapplied inspector
  changes. Deferred timeline seeks do not overwrite the selected shot's draft
  replay-time field. No-op drag events and deferred seeks retain validation errors.
- Position, yaw, pitch and roll must be finite; pitch stays between -90 and 90.
- A player-follow keyframe can be explicitly detached with Use current camera.
- Named paths retain ReplayStudio's existing JSON format and `.mcpr` storage.
  Import adds distinct names instead of overwriting existing paths or autosave.
- Export settings and the render queue use the existing configuration files.
  Existing output files are preserved; choose a distinct filename for a new job.
  Complete queued jobs before switching recordings.
- Recording-name dialogs keep existing files when dismissed. A partial filesystem
  rename failure can be retried without repeating completed moves.

## Review fixes

- B1: Clicking a button also dispatched a blank canvas click, clearing validation
  errors or scrubbing the timeline. Button and canvas dispatch are now exclusive.
- B2: Different GUI scales could misalign input with the drawn timeline. All input
  uses the same viewport mapping; minimised dimensions remain nonzero.
- B3: Camera inertia could move the view while editing values. User camera
  controller updates are suspended inside studio screens.
- B4: The realtime player could expose an epoch-sized time between its first two
  ticks. Its clock now stays at the start offset until the second tick starts it.
- B5: Image/frame preview GL resources could outlive the render screen. Preview
  textures are released on screen close and explicit render cleanup.
- B6: Cancelling a mod-difference warning leaked the opened recording. Cancel now
  closes it and returns to the native library.
- B7: Entity tracking scanned the whole recording eagerly even for ordinary
  free-camera shots. That legacy operation starts only when actually requested.
- B8: Backward seeks could restore a screen by assigning `currentScreen`, leaving
  mouse capture and native input in the wrong state. The studio restores through
  Minecraft's screen lifecycle and reapplies the camera pose after seeking.
- B9: Advancing the next shot now synchronizes the cursor used by free-camera
  capture. Capturing with I refreshes the cached inspector when reopening it.
- B10: Legacy preview callbacks cannot replace the native editor with the old
  overlay input screen. Pending scrubs cannot interrupt a newly started preview.
- B11: Render previews use the dimensions of their delivered pixel buffer.
  Cancelling returns to the native export screen.

No new server packets, server authority, shader settings, vehicle controls, replay
packet formats or camera-path serialization identities were introduced. The new
UI is client code; existing common/client boundaries are retained.

## Validation

The render progress screen also fills the window. Its opaque background covers
the window edges, its progress/actions stay at the bottom, and its frame preview
fits the available space without changing the exported frame's aspect ratio.
Replay ranges can slide as a pair at fixed speed/duration in the main timeline
and the timing diagram. Sliding preserves intermediate clock detail, clamps at
recording/neighbour limits, and commits as one undoable change. See the timing
workflow document for the drag/resize/scrub controls.

Automated tests exercise paired edits, grouped undo/redo, moves, deletion,
collision/order rejection, frozen action, rollback, finite pose validation,
player-follow detachment, clock parsing, GUI-scale coordinate mapping, and native
Minecraft button dispatch. Existing packet-order, player-spawn and frame-integrity
tests remain part of the suite.

These require a running Minecraft client and are not established by compilation:

1. Open a recording at each GUI scale, including an ultrawide window and a small
   window. Press T, fly with Escape, return to the editor, and resize while typing.
   Check the layout, click targets, Tab navigation and value retention.
2. Create three shots. Scrub both clocks, drag a keyframe, change its time and pose
   numerically, undo/redo, clear/undo, and export a short sequence.
3. Freeze action over a moving tank while orbiting it, then restore normal action.
   Preview, edit during preview, stop, seek backward and resume.
4. Save/load/rename/delete a named path. Copy/import it. Reject invalid clipboard
   JSON without losing the current timeline. Check autosave after reopening.
5. Add/edit/delete scene markers and jump to their saved camera views.
6. Choose an output and FFmpeg executable through Browse. Export MP4 and a PNG
   sequence. Check render pause, cancel, retry/error visibility and completed files.
7. Queue two uniquely named exports; cancel one, remove it, and render the other.
   Check that switching recordings with unfinished queue items is prevented.
8. Export a still image with F2. Cancel a mod warning, then reopen the same file.
   Make a fresh recording and check its save/naming workflow.
9. Close the replay, join a live world and check normal chat, camera, inventory and
   MCHR controls. No UI or server runtime test is claimed without running it.
10. From the library, create an edited copy with split/cut points and check the
    resulting recordings. Confirm the source is retained. Cancel recording
    deletion and check the file is retained.
11. Drag each dock divider to its minimum and maximum, resize the game window,
    close/reopen the editor and verify layout persistence. Pan both clocks with
    left-drag and middle-drag; scrub their rulers, use Ctrl+wheel, then Shift+Z.
    Confirm pan/zoom never changes saved keys or the selected source range.
12. Preview across a backward seek/restart with the workspace visible. Toggle
    Ctrl+F during playback and return without stopping it. Check the preview
    aspect, DH terrain, effects and absence of GUI inside the camera texture;
    export a short clip and confirm export dimensions ignore dock resizing.
