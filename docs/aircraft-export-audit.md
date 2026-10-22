# Aircraft export jitter audit (X39)

## X51: residual movement in the six-second orbit export

The supplied `2026_10_21_11_20_48.mp4` is a six-second 2560 x 1440, 60 FPS
export. Its running-client log confirms the aircraft export hook indexed four
tracks. The saved orbit maps output 0–6 seconds to replay 33.273–39.273 seconds
and follows F16 entity 2509 (`09128f04-6b58-3afc-cbda-9d7b39ff94fa`).

The automatic camera already uses monotone Hermite interpolation of recorded
position, whereas the aircraft body used linear interpolation. Reconstructing
the camera's target from the saved orbit gives a maximum 0.352471-block
body/target discrepancy (RMS 0.100595) over all 360 output frames. The saved
camera agrees with the smooth target to within 0.000010 blocks at its keys.
Recorded snapshot intervals include 151 ms followed by 49 ms, making the
different segment velocities particularly visible in a close tracking shot.

The body now uses the camera target's same bounded position curve. Both readers
call `ReplayInterpolation`, retaining identical camera math and existing paths.
All original snapshot positions and times remain exact. Quaternion attitude
interpolation, recorded banking, playback speed, gameplay and packet formats
are unchanged. Invalid/destroyed neighbours, missing batches, identity changes,
gaps over 500 ms and teleports cannot supply interpolation tangents.

New tests cover velocity continuity at uneven arrival intervals, no positional
overshoot, invalid-neighbour rejection, and agreement between the actual body
and camera target at all 360 frames of this orbit. The change applies through
the existing export-only aircraft hook; normal gameplay remains native.

Checking the actual saved camera spline found a second error: its 20 Hz camera
samples undersample the quickest target changes. The old rendered camera/body
discrepancy peaks at 0.412816 blocks. Sharing the target curve alone reduces RMS
error but cannot remove this camera approximation error. New auto shots now use
60 Hz camera samples, retaining the existing 600-sample bound. At all 360 export
frames the regenerated version of the same orbit has maximum framing error
0.006205 blocks, a 98.5% reduction in the maximum discrepancy.

Existing saved keys are preserved. Re-export applies the aircraft correction;
regenerate older coarse auto shots to gain the camera sampling improvement.
The comparison recording contains a regenerated copy of this exact orbit
(same timing, 24-block distance, 10-block height and 360-degree eased sweep).
Film comparison results are recorded after the in-game validation below.

The installed X51 client rendered the regenerated orbit at the original
2560 x 1440 / 60 FPS / 50 Mbps shader settings. It completed all 360 frames and
audio; the client log again confirms the export pose hook indexed four tracks.
The original video and original saved orbit were preserved. The comparison
recording is `Aircraft jitter comparison X51.mcpr`. In `replay_videos`,
`aircraft-jitter-before-after-X51.mp4` provides the labeled comparison and
`aircraft-jitter-fixed-X51.mp4` is the corrected full-resolution export.
The latter decodes without errors; both files contain all 360 frames.

Full Java 8 `build` passed: 195 tests passed and nine optional fixture/GPU tests
were skipped. Installed MCHR X190 and Angelica compatibility checks ran, as did
the actual Plane Fight position and serialized-camera framing regressions.
No MCHR/client-server update or new server recording is required. This removes
the measured position/framing mismatch; it does not remove genuine recorded
banking, missing source data or every possible temporal shader artefact.

## X41: confirmed startup failure in the installed modpack

The X40 client log says `hasMCHeli: false` at 16:55:05, then Forge discovers
the unpacked MCHR X190 directory at 16:55:09. The early mixin plugin had probed
the classpath before Forge added ordinary mod directories. Consequently **all
seven MCHR hooks, including the X39 sampler, were skipped**. The earlier
headless tests verified source data/math and hook bodies, but not this startup
selection. They did not establish that the fix was active in Minecraft.

X41 moves MCHR hooks to UniMixins' GTNH `@LateMixin` loader, after ordinary mod
discovery. Selection uses Forge's loaded mod IDs and does not probe/load MCHR
classes early or change the classpath/discovery order. The existing installed
UniMixins 0.2.1 supports this loader. The MCHR export pose mixin adds a marker
interface; export checks that marker before capture and reports an error if it
is missing, instead of silently rendering without the fix. An export log line
confirms the active hook and number of indexed aircraft tracks.

`ReplayModLateMixinsTest` covers absent/present MCHR, late config/annotation,
removal from early selection, and the hook marker. The actual source-track
regressions remain in place. A new in-game export still needs visual validation.

## Original X39 audit

## Evidence

Inspected the user's 15-second, 1920x1080, 60 FPS MP4 and its PlaneTest recording.
The saved timeline maps video 0–15 seconds to replay 30.955–45.955 seconds.
Consecutive exported frames show large aircraft attitude changes and an initial
stale runway pose. The recorded MCHR server snapshots for that aircraft are
continuous through this interval; its spawn AID1 UUID matches the VID1 snapshot
identity. The installed MCHR X190 already includes the spawn identity fix.

Audited MCHR's snapshot decoder/tracker, aircraft network interpolation, and
unified loaded/distant renderer, plus ReplayMod's synchronous sender, timeline
timer, warmup, frame capture and existing compatibility mixins. The old export
path used mutable client tick interpolation and stateful MCHR smoothing. The
earlier nano-clock regression exercised SnapshotRotation alone; it did not
guarantee the final render pose or initial export position. A clock correction
alone was insufficient in the supplied MP4. The exact running-client transition
responsible for each old bad frame was not instrumented.

## Fix and boundaries

At export startup, stream the native 1.7.10 recording once and index precise
plane/helicopter snapshots by UUID. At the end of both loaded and distant MCHR
pose updates, sample position and orientation at the frame's replay timestamp.
Position is linear; orientation uses quaternion SLERP in MCHR's actual
`Ry(-yaw) Rx(pitch) Rz(roll)` render order. The displayed body pose now depends
on source data/time, not previous rendered frames, client tick phase, wall time,
or capture-pass order. Real recorded banking and rolls are retained.

This applies only during offline export, only to matching UUID/class/type, and
only where continuous finite snapshots exist. It does not mutate entities,
physics, gameplay/network packets, vehicle controls, models or file formats.
Live play and ordinary replay preview retain their existing rendering. No MCHR
client/server update or new recording is needed for this recording.

Unknown identities, old recordings without VID1, unsupported protocols, missing
or destroyed vehicles, gaps over 500 ms, and teleports retain native rendering.
There is no promise to reconstruct missing/bad source data. The scan is bounded
to 500,000 aircraft samples; malformed native data or exceeding this bound stops
export with an error. Data is released on success, cancellation and failure.

## Verification

`RecordedAircraftFramesTest` reads the actual recording and checks all 900 poses
in the saved export interval, including the initial altitude, repeated capture
passes, angular wraps and vertical Euler inversions. Its maximum consecutive
orientation change is 3.572864 degrees at 60 FPS, consistent with the recorded
manoeuvre, rather than the artificial large reversals in the MP4.

`MCHReplayCompatibilityTest` checks the installed X190 target methods/fields and
passes the actual track through the export render hook while injecting arbitrary
native attitude reversals and stale runway positions. It also checks that the
override stops after export and outside replay. Additional tests cover absence,
destruction, identity reuse, teleports, invalid/truncated input and cancellation.

These are source-data and render-hook regressions, not an end-to-end Minecraft
capture. In-game acceptance: export the existing saved shot again at 60 FPS;
check the first second and the banking section, then repeat with another FPS or
antialiasing setting. Pause/freeze should hold exactly the same attitude, and
live observation, native replay preview and other vehicle types should behave
as before. A new MP4 still needs visual confirmation in the running modpack.
