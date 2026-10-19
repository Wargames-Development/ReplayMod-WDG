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
The main editor expands that canvas to fill the full window, keeping a uniform
control/font scale. The inspector anchors right, shot list left, and timelines
bottom; wider windows expand the timeline and camera area, while taller windows
show more shot rows. Resize retains inspector, step and zoom-span drafts. Other
workflow dialogs retain their existing proportional layout.
The editor retains its step, zoom, freeze mode and drafts when returning from
free-camera movement. Camera properties preview while typing; Apply commits them.
Apply during preview rebuilds playback from the current playhead with the edit.

## Timeline and persistence

- A normal shot stores camera pose and source replay time together. Adding,
  updating, moving, deleting and clearing paired keyframes share one undo step.
- The video timeline previews the interpolated camera as the mouse moves. Replay
  state seeks once after release, avoiding repeated expensive backward reloads.
- Click a keyframe to select it. A small drag threshold prevents retiming from an
  ordinary click. Wheel over the video timeline to zoom around the pointer.
- Time fields accept seconds (`12.5`), minutes (`1:12.500`), or hours
  (`1:02:03.500`). Invalid input remains editable and reports an inline error.
- The first camera capture automatically starts at output zero, including when
  Add Shot, Apply, or free-camera I is used after browsing a later empty output
  slot. Its chosen replay moment and pose are retained. The editor returns the
  output cursor, visible range and shot-list selection to the captured key.
  Subsequent shots use their requested output times. Source time must stay within the
  recording and move forward or remain equal. Equal source times freeze action.
- **Whole path: normal speed 1x** keeps the first shot's recorded moment,
  then maps every subsequent clock key to source-start plus elapsed output time.
  It preserves all camera poses, selects the first shot for preview, is one undo
  step, and rejects paths extending past the recording. It changes the current
  sequence only when clicked. A persistent banner identifies a fully frozen path.
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
