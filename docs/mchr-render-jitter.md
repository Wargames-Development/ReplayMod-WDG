# MCHR attitude jitter during export

The installed MCHR X190 far-vehicle renderer presents loaded aircraft through
`SmoothedPose.updateFromAircraft`. Its precise `SnapshotRotation` smoother receives
`System.nanoTime()` from `onRenderWorldLast`, independently of the millisecond
clock used for snapshot age and handoffs.

ReplayMod already redirected that millisecond clock to recording time, but left
the nanosecond clock on wall time. A frame taking 300 ms to export could finish a
100 ms attitude blend even though the recording advanced only 16–17 ms. Repeated
capture passes, render stalls, and frozen action could therefore produce different
attitudes from normal viewing.

The optional MCHR mixin now redirects the renderer's nanosecond clock to the same
recording time, converted to nanoseconds. Normal gameplay retains `System.nanoTime`.
Replay pause, slow motion, export, repeated capture passes and rewind share the
existing replay clock; seeks still clear the old pose cache. The redirect is
optional for older MCHR versions without this clock call. No MCHR files or replay
formats change.

Verification uses `REPLAYMOD_TEST_MCHR` pointing at the installed, unpacked MCHR
mod. It loads that mod's actual `SnapshotRotation` class and feeds it 100 ms attitude
updates at 60 video frames/second. Simulating 300 ms wall time per frame reproduces
20-degree jumps in the old clock path. With the redirected replay clock, changes
remain below 4 degrees per frame for the same 200 degrees/second input. Repeated
passes at a frozen replay timestamp preserve the attitude. Compatibility checks
verify the installed renderer exposes both clock call sites; unit checks cover
pause/rewind and the normal-gameplay clock fallback.

This is a headless regression, not a rendered-video comparison. To verify the
reported clip, restart with the updated ReplayMod, export a short aircraft turn
at the original settings, and compare with replay viewing. Also check a frozen
frame and a slow-motion turn. Existing jittering video files must be re-rendered.
