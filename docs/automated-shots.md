# Automated shots (X31)

Open the cinematic editor with T, then **Auto shots [G]**. The modal fills the
window and generates original camera paths inside the existing saved-path format.
It does not depend on ReForgedPlay or ReplayMod Cinematics.

1. Fly toward the vehicle before opening Auto shots. An aimed MCHR vehicle is
   selected automatically where possible. Click the target button to cycle nearby
   loaded vehicles and MCHR's distant tracked vehicles, or choose a fixed point.
   **Pick aimed / nearby** refreshes the list and uses the camera's centre, falling
   back to the nearest vehicle origin within 12 blocks when nothing is aimed at.
2. Choose **Orbit**, **Follow**, or **Fly-by**. Orbit defaults to a full turn.
   Follow keeps a distance/bearing relative to the recorded vehicle heading.
   Fly-by travels from a negative along-track offset to a positive one while
   looking at the vehicle, with a separately editable sideways offset.
3. Set video duration and replay In/Out. **Set action 1x** advances the action by
   exactly the video duration; **Freeze action** holds the recorded moment while
   the camera moves. The draft reports the resulting action speed.
4. Adjust distance/sideways offset, height, bearing or sweep, focus height and
   roll. Every start/end value is editable. Follow bearing 0 is ahead of the
   vehicle and 180 behind it. Fixed-point bearings use world heading zero (+Z).
   Easing changes camera offsets, independently of recorded-action speed.
5. The top-down draft updates after typing pauses briefly. Blue is camera travel;
   green is the target's recorded route. Fixed points can be entered as XYZ.
   Vehicle motion is read in the background and cached for the selected vehicle.
6. **Place shot** inserts the reviewed path; **Place & preview** also plays it in
   the main camera viewer. Placement refuses occupied ranges, including ranges
   inside an existing interpolated segment, and refuses backwards replay clocks.
   The default output location is zero for an empty path or 50 ms after its last
   key. **Append** restores that output location.
7. Placement is one Ctrl+Z undo step. The generated path appears as one timing
   range, with its start/end listed. **Samples: On** exposes individual camera
   samples for manual edits. Dragging the ending boundary retimes the whole
   generated move; Timing / speed also controls its entire range. Deleting a
   boundary with Samples off removes the generated shot as one undoable edit.

Tracking uses the chosen entity's MCHR server snapshots in `recording.tmcpr`.
Snapshots include vehicles outside vanilla tracking distance. The reader validates
packet framing and bounds, skips chunk bodies without retaining them, caps retained
samples, checks missing/destroyed entities and rejects gaps over 500 ms. Heading
interpolation crosses +/-180 degrees by the short route. It never drives a vehicle
or advances a hidden second world. Ordinary source-time interpolation keeps slow
motion, freeze and export deterministic. Cancellation interrupts background reads.

Generated cameras use up to 600 samples at a nominal 60 samples/second, with a
shared smooth spline between the already eased samples. Short 60 FPS trailer
shots retain fast target motion more accurately than the older 20 Hz sampling.
The 600-sample bound reduces sampling density on shots longer than about ten
seconds. Regenerate older coarse auto shots to benefit from the higher detail;
existing saved camera keys remain unchanged. They aim at the target's focus
height; no first-person mounting or spectator keyframes are created. Paths do not
perform terrain or obstacle avoidance; inspect the main viewer before exporting.
Grouping is editor-only metadata saved on camera keys, and survives normal saved
paths, clipboard export/import, timing edits and undo/redo. Broken group boundaries
reveal the remaining samples rather than hide them.

Choosing Follow creates a draft. Use **Place & preview** to see it follow during
path playback. Flying close to a vehicle or clicking it in free flight does not
attach the camera; the free-flight spectator controls currently accept players.
For moving follow shots, select a vehicle, keep Target motion On, and choose Replay
Out after In. See the [follow vehicle audit](follow-vehicle-audit.md) for findings
and the remaining in-game checks.

Automated verification covers orbit radius/focus, all three shot types, moving
target headings, camera easing independent of action speed, frozen action, angle
wrap, bounds and invalid parameters, collision rejection against existing timeline
segments, atomic placement/undo/redo/delete, grouping serialization/retiming, parser
framing, cancellation, missing/destroyed entities, and actual PlaneTest F-16 motion.
Headless native-control tests check layout and fixed-point entry at four window
sizes. In-game picking, diagram appearance and preview/export still require a
visual check in the complete Wargames instance.
