# Follow vehicle audit — 2026-10-20

Scope: vehicle discovery/picking, Auto shots controls, asynchronous track loading,
recorded movement and heading, path placement, preview entry, and free-flight
spectator input. This audits the current working tree, including existing local
changes. The reported in-game failure has not been reproduced in a running client.

## Findings

| Finding | Evidence / effect | Result |
| --- | --- | --- |
| B1: close-range picking fails | `ShotTargets.aimed` required a vehicle's origin plus two blocks to fall within a 20-degree cone. Flying beside or above a large vehicle could select nothing. | Added nearest-target fallback within 12 blocks of its origin when no aimed target exists. Aimed selection keeps priority. |
| B2: target button silently does nothing | Cycling an empty list returned to the same fixed-point selection without feedback. | Now explains that no MCHR vehicles are available and how to retry. |
| B3: re-picking can retain another vehicle's draft | Draft identity used the target list index. A fresh list could put a different entity at the same index with identical other settings. | Identity now uses entity ID; picking explicitly discards the proposal. |
| B4: previous scan failure blocks another target | Changing target or turning motion off left the previous worker active. Its later error could prevent generation for the new selection. | Target/motion changes cancel and discard that worker and its draft state. |
| D1: insufficient feedback | Shot-type buttons had no selected marker. Choosing Follow only updated a draft; fixed-point and frozen-action states could look like failed following. | Selected mode is marked; persistent guidance explains placement/playback, fixed targets, disabled motion and frozen action. |
| D2: free-flight vehicle attachment is unavailable | `CameraEntity.canSpectate` accepts visible `EntityOtherPlayerMP` only. MCHR vehicles extend `Entity`, not that player type. Proximity alone has no attachment handler. | Documented limitation; this audit does not introduce a separate live chase-camera system. |

## Verified behavior and limits

Follow generation uses recorded vehicle position and heading at each replay time.
The GUI converts start/end bearings into a bearing delta; equal bearings retain
the selected angle relative to the vehicle. Default distance is 24 blocks, height
10, bearing 180 (behind). This math is covered by moving-target tests.

Motion data comes from MCHR full server snapshots in `recording.tmcpr`, rather
than the vehicle's current position. The reader's field order matches the sibling
MCHR source's `PacketEntityInfoSync` base records; trailing extensions are ignored.
Absent/destroyed vehicles and gaps over 500 ms cause explicit errors. Recordings
without MCHR snapshots cannot generate moving vehicle paths through this feature.

`Place shot` inserts the path. `Place & preview` also enters playback through
`CinematicEditor.timingApplied`. Occupied output ranges are rejected. Target
motion Off, a fixed point, or equal replay In/Out can legitimately make a Follow
camera stationary when the framing values are also constant. The target list is
captured when the panel opens; Pick aimed / nearby refreshes it.

## Changes and validation

- `ShotTargets.java`: nearby selection fallback.
- `StudioAutoShot.java`: draft identity, worker cancellation, errors and feedback.
- `ShotTargetsTest.java`: nearby, aimed-priority, nearest, distant and empty cases.
- `StudioAutoShotTest.java`: changed entity identity, empty-list feedback and an
  old asynchronous failure after switching motion off.
- `automated-shots.md`: updated picking instructions and workflow limits.

Java 8 Gradle `test` completed successfully with filters for `ShotTargetsTest`,
`StudioAutoShotTest`, `AutomatedShotTest`, `RecordedVehicleTrackTest`,
`PreviewPlaybackTest`, and `StudioInputTest`. Production and test compilation passed.
34 tests passed; one real-recording fixture test was skipped because
`REPLAYMOD_TEST_PLANE_RECORDING` was unset. `git diff --check` passed.

No release JAR was built or installed. Existing local changes were preserved.
No packet or saved-path formats changed.

## In-game acceptance check (still required)

1. Open a replay containing moving MCHR vehicles. Move to a time with continuous
   recorded snapshots and at least six seconds of action remaining.
2. Fly beside/above a vehicle within 12 blocks of its origin. Open T → Auto shots
   [G]; confirm the selected vehicle name/ID. At long range, aim at its centre.
3. Choose Follow; confirm `> Follow`, Target motion On, and replay Out after In.
   Set action 1x if needed. Wait for the draft to finish loading.
4. Click Place & preview in an empty output range. Confirm movement and heading
   changes keep the vehicle framed. Scrub backward and forward through the shot.
5. Reopen Auto shots, re-pick a different vehicle, and verify its route replaces
   the old draft. Switch targets/motion while a large recording is loading.
6. Test no vehicles, missing snapshots, motion Off and Freeze action. Confirm
   explanations remain visible and placement is disabled for invalid drafts.
7. Check button feedback at the actual GUI scale, undo/redo placement, then export
   a short sample and compare its framing with preview.

The 12-block fallback uses the vehicle origin, not model bounds. Very large
vehicles may still require aiming at their centre or selecting by name. Actual
rendered framing, click feel, and export are unverified until the above client
check is completed.
