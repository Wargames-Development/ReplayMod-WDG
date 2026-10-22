# MCHR / ReplayMod movement audit — 2026-10-22

Scope: the current ReplayMod working tree, sibling MCHR aircraft/network/render
sources, installed MCHR X190 class contracts, and the existing `Plane Fight.mcpr`
recording. The user reports small jitter in both replay viewing and exported video.
Existing uncommitted changes were preserved. No MCHR source was changed.

## Findings and changes

| ID | Evidence and effect | Resolution |
| --- | --- | --- |
| B1 | The recording contains snapshots spaced by exactly two server ticks but arriving 151 ms then 49 ms apart. Aircraft 2257 moves at 40.5 blocks per server second, while dividing its snapshot displacement by arrival time gives 26.821–82.652 blocks/s. Hermite position interpolation softens the corners but preserves this false acceleration. | Both the aircraft reader and automatic camera reader now share a bounded snapshot clock filter. |
| B2 | Only offline export used the indexed position/quaternion pose. Replay viewing still used loaded-entity tick interpolation or distant-contact extrapolation and smoothing, with different delays at handoff. | Index once when opening a supported replay and reuse that immutable data for viewing and export, through the existing loaded/distant render hooks. Pause, seeks and repeated render passes sample the same recorded-time pose. Closing a replay releases the index. |
| B3 | `FullReplaySender.processPacketSync` called `entity.onUpdate()` four times when unloading a chunk to finish vanilla interpolation. For MCHR this also advances flight simulation, animations and effects, and four updates need not exhaust its longer interpolation. | MCHR vehicles now use their existing `ReplayVehiclePose` target-settling hook. Vanilla entities retain the four-update behavior. |
| B4 | The wider recording interval also has sustained arrival-clock offset changes. After B1's bounded correction, a 5.65-second diagnostic still measured 24.1–42.9 blocks/s at 60 FPS around these shifts. Sequence/arrival data alone cannot establish whether each shift was server slowdown, recording delay, or persistent network delay. | Remains a limit. Do not claim that all jitter has been removed or silently retime the entire replay against other recorded actions. |
| T1 | Headless pose/math tests and installed bytecode checks do not show the final shader-rendered video or live frame pacing. | In-game acceptance remains required. |

`RecordedSnapshotTimeline` compares arrival times relative to the packet's
existing server sequence, whose unit is one nominal 50 ms server tick. It takes
the median offset of at most five neighboring snapshots. A correction cannot
exceed 100 ms, reorder samples, cross a gap over 500 ms, or cross a repeated,
backwards, negative or implausibly jumping sequence. Sustained slower tick
delivery retains its timing. The first/last samples of each continuous run retain
their arrival times. Simultaneous arrivals remain distinct by batch until retiming,
so a late packet delivered with its successor does not overwrite that successor.
Both scans are bounded to 500,000 snapshot packets in addition to existing pose
limits. No network packets or replay-file contents are rewritten.

The replay index is loaded synchronously while opening the recording, adding a
bounded scan/loading cost and retaining the pose data until close. Export reuses
that index instead of scanning it again. Unsupported recordings or an indexing
error retain native viewing and log the reason; export retains its stricter hook
and data validation. The supported pose override covers UUID-matched planes and
helicopters. Tanks and other vehicle types retain native body rendering; MCHR's
chunk-unload target settling also applies to them through the aircraft base mixin.

## Files changed for this audit

- `RecordedSnapshotTimeline.java`: shared presentation clock correction.
- `RecordedAircraftFrames.java`, `RecordedVehicleTrack.java`: retain packets by
  batch until the shared corrected timestamps are available.
- `ReplayAircraftExport.java`, `ReplayHandler.java`: replay-scoped index lifetime,
  shared viewing/export sampling and cleanup. The existing class name is retained.
- `MixinMCHExportPose_MCHeli.java`: update its scope comment; its existing two
  render hooks already cover both loaded and distant aircraft.
- `FullReplaySender.java`: settle MCHR's chunk-unload pose without simulation ticks.
- `RecordedSnapshotTimelineTest.java`, `RecordedAircraftFramesTest.java`,
  `MCHReplayCompatibilityTest.java`, `MCHChunkHandoffTest.java`: timing, actual
  recording, lifecycle, installed-class and handoff regressions.

## Verification

- Synthetic steady flight stays exactly linear through both 151/49 ms delivery
  and two snapshots arriving at the same millisecond. Camera and body agree at
  every millisecond. Slow server ticks, gaps and sequence resets retain timing.
- In the real recording's isolated burst at replay 33.350–33.950 seconds,
  corrected 60 FPS samples measure **40.011–40.564 blocks/s**, RMS error **0.170**
  against the 40.5-block/server-second reference. This short interval specifically
  isolates B1; it does not establish uniform speed through the later B4 shifts.
- The actual six-second orbit checks camera-target/body agreement at all 360
  frames. Its serialized regenerated camera has maximum framing error
  **0.0014514 blocks** against the indexed body.
- Lifecycle tests cover pause, rewind, repeated samples, leaving export, closing
  replay and opening a different replay. Installed X190 checks cover the fields,
  target methods and clock call sites used by the existing optional mixins.
- Chunk-handoff tests prove that MCHR uses target settling once without simulation
  updates and that vanilla retains its four updates.
- Java 8 `gradlew.bat --offline test` passed: **221 tests, 206 passed, 15 skipped,
  zero failures/errors**. Installed MCHR and actual orbit fixtures were enabled;
  other optional recording/GPU/external-tool fixtures were unavailable.
- Java 8 `gradlew.bat --offline build` also passed, including the serialized
  regenerated-camera fixture and the full test suite. The resulting local JAR is
  `build/libs/ReplayMod-WDG-2.5.2-WDG_X56.jar`; packaging advanced the build number
  from X55 to X56. It includes the earlier render-button icon fix. It was not
  installed into a running instance. `git diff --check` passed.

## Compatibility and in-game acceptance

No server/client protocol, MCHR physics/control, model, NBT, definition, registry
or saved replay format changes are required. Existing recordings with UUID
snapshots benefit. Existing saved camera paths are preserved and still contain
their old target timing: **regenerate automatic Follow/Orbit/Fly-by shots** to
make their camera keys follow the corrected timing. Simply re-exporting an old
tracking path can leave a camera/body mismatch. Existing MP4s need re-rendering.

1. Restart with the new ReplayMod build. Open the existing recording and check
   the log for `MCHR replay pose hook active` and the indexed track count.
2. Regenerate a copy of the moving automatic shot. Compare ordinary playback,
   editor preview and 60 FPS export around replay 33.350–33.950 seconds, then
   inspect the entire banking segment for residual B4 timing changes.
3. Pause for ten seconds, seek backward/forward, replay the shot twice and compare
   the same timestamp. The aircraft body should hold the same position/attitude.
4. Fly the camera quickly across chunk boundaries and loaded/distant handoff,
   watching the body, landing gear, rotor/control-surface animations and riders.
   Repeat export with another FPS/antialiasing setting.
5. Check a live world, pilot/passenger/UAV views and a tank. Normal gameplay is
   outside the recorded-pose override; verify no control or simulation changes.

Realtime chunk/shader work can still stall displayed frames. Offline frame cost
must not move a supported indexed body pose, but missing snapshots, genuine
recorded discontinuities, long gaps, unsupported vehicles, terrain availability,
part animation, and temporal shader artifacts remain separate limits. No updated
Minecraft capture has been performed during this audit.
