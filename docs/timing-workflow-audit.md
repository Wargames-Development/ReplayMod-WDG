# Cinematic timing: workflow and audit

## What the clocks mean

Replay time selects an event inside the recording. Video time places a camera
view inside the exported clip. The red In/Out handles and two connecting red
lines show the selected camera move's mapping between those clocks. Green shading
is recorded action; blue shading is the camera move in the video. Gold lines are
the live playheads. Saved shot times appear separately in the shot inspector.

Action speed is `(Replay Out - Replay In) / Video duration`.

| Replay In -> Out | Video duration | Result |
| --- | --- | --- |
| 0:20 -> 0:24 | 4 seconds | 1x normal action |
| 0:20 -> 0:24 | 8 seconds | 0.5x slow action and a longer camera move |
| 0:20 -> 0:22 | 4 seconds | 0.5x action with the same camera duration |
| 0:20 -> 0:24 | 2 seconds | 2x fast action |
| 0:20 -> 0:20 | Any duration | Frozen action while the camera moves |

**Fit (Shift+Z)**, Ctrl+wheel zoom and **Set zoom span** change the visible ruler only.
Ordinary wheel and empty-lane left-drag pan the view; middle-drag pans over keys
and ranges as well. Drag a numbered ruler to scrub. These navigation gestures do
not move shot keys or modify the action range.
**Timing / speed** changes the exported clip. Camera smooth/straight movement
changes its interpolation independently of recorded-action speed.

## A repeatable shot workflow

1. Scrub the Replay ruler to an event and fly to the opening view. Add Shot or I
   captures a paired camera/time key. The first camera capture starts at video 0.
2. Choose a step, click Next slot + step, fly to the ending view and capture it.
   Wait for any replay seek to finish before capture. Advancing/frozen New steps
   controls whether this step advances recorded action.
3. Select either endpoint of the camera move. The last key selects its preceding
   move. Each shot row shows both its video time and its recorded-action time.
4. Open Timing / speed. Choose this camera move or Whole camera sequence. Type
   Replay In/Out and video duration, or choose an action-speed preset.
5. The speed-mode button states which quantity changes. One mode keeps replay
   In/Out and computes video duration. The other keeps camera duration and computes
   Replay Out. Typing a replay range or duration returns to direct duration/range
   editing. Typing a speed or selecting a speed preset uses the chosen speed mode.
6. Inspect the draft diagram, calculated speed, duration and shift of later shots.
   Apply duration uses the duration/range values. Apply speed uses the speed and
   chosen speed mode. Apply + preview uses the active mode, commits the change,
   and previews the selected move from its start to its end. Back discards drafts.
7. Drag the **green replay range** to slide both red endpoints together, keeping
   action speed and camera duration fixed. For example, a 1x range of 0:20–0:24
   can slide to 0:30–0:34 while the camera move stays four seconds long. Shift-drag
   either red endpoint does the same, including narrow or frozen selections.
   Sliding stops at the recording ends or neighbouring replay times; select Whole
   sequence in Timing / speed to slide the whole path. Both the main Replay ruler
   and the timing diagram support this. The diagram edits drafts until Apply;
   the main ruler commits on release as one undoable change. Drag a red endpoint
   without Shift to resize the range and change its rate. Alt-click the main Replay
   ruler to scrub inside a selected range. Blue camera keys retime a paired
   camera/clock key. Right-click Video opens the timing panel.
8. Type exact pose or shot-clock values in the inspector and click Apply values.
   Pose previews while typing. Apply before switching shots, previewing, exporting,
   or opening timing controls. Left/Right steps one frame at the configured export
   frame rate; Shift uses 1 s.
9. Fit path, preview the result and export. Undo/redo restores a timing operation
   together with all shifted camera and clock keys. Existing recordings keep their
   storage format and are not rewritten by the development tools.

Timing duration edits scale camera/clock keys within the edited range and shift
later keys together. Camera poses and their order are preserved. Apply establishes
a constant action rate within that range; existing variable clock data is labelled
with its average before editing. Use multiple camera moves for different speeds.
The whole-path 1x shortcut retains the first recorded moment and maps elapsed
video time directly to elapsed replay time.

An existing path can have a leading gap if its first key was moved away from zero.
The editor identifies this. **Remove leading gap (whole video)** shifts its camera
sequence to zero and trims preceding clock-only keys as one undoable edit. It
commits immediately; other timing-panel values remain drafts until Apply.

## Audit findings and changes

| Workflow problem | Change | Verification |
| --- | --- | --- |
| Replay and video clocks appeared unrelated | Shaded ranges, clock-key ticks, red endpoint handles and two connecting red lines | Shared geometry mapping tests; in-game appearance pending |
| Zoom looked like clip duration | Set zoom span and Fit path are separate from Timing / speed | Controls inspected in source; runtime layout pending |
| Slow motion required timestamp arithmetic | Exact duration/range fields and two explicit speed behaviours | Slow/normal/fast timing and speed tests |
| Frozen action was easy to miss | FROZEN rate label, equal In/Out, explicit Freeze preset | Interpolated frozen clock test |
| Resizing a move could collide with later keys | Rescale inside the range; ripple later keys with direction-safe moves | Expansion, compression, intermediate-key and undo/redo tests |
| Range edits could reverse neighbouring action | Validate all resulting timestamps before applying | Neighbour ordering and recording-end rejection tests |
| Last shot had disabled motion controls | Last endpoint selects its preceding move | Span selection test; runtime control check pending |
| Inspector drafts disappeared during shot changes | Require Apply before selecting another key or opening timing/export | Guarded event paths inspected; in-game typing check pending |
| Add Shot failure changed selection before validation | Capture first, update selection only on success | Existing collision/atomicity tests; GUI selection check pending |
| Capture could race an asynchronous replay seek | Capture is disabled with a Seeking replay label until the sender finishes | Source review; live seek check pending |
| New-shot fields lagged the playing view | Unfocused new-shot fields follow preview playheads; saved-key inspector stays fixed | Runtime check pending |
| Leading gaps obscured clip start | Visible warning and undoable Remove leading gap | Paired and clock-only leading-gap tests |
| Extra clock keys could make a displayed rate misleading | Label variable mappings as an average | Variable-rate detection and undo test |

No WGMap or MCHR source was modified for this audit. No game, shader, server,
packet-format or playback-authority changes are part of the timing UI work.

## Remaining runtime checks

Compilation and unit tests do not establish OpenGL or mouse behaviour in a modded
Minecraft client. Check at several GUI scales: capture two shots, scrub each
ruler, drag both red handles, drag a blue key, set 0.5x in each speed mode, Freeze,
Apply + preview, undo/redo and export. Check a multi-move sequence and the leading
gap shown in the reported screenshot. In particular, verify red-line visibility
with Angelica, input after backward seeks and preview stopping at the selected
move's endpoint. Retain the previous installed jar until this game check is done.
