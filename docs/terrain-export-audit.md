# Low-altitude terrain export audit — X55

The supplied `replay_videos/darkTerrain.mp4` contains 1,200 frames at
2560 x 1440 / 60 FPS (20 seconds). Its saved path starts at replay 6.715 seconds,
camera (-200.46, 82.07, 486.35), in chunk (-13, 30). None of the surrounding
5 x 5 chunks has arrived at that point. The camera's chunk first arrives at
20.301 seconds; all 25 surrounding chunks are available later in the recording.
All 289 chunks in an eight-chunk camera radius are eventually available at the
sampled video times 0, 1, 3, 6, 11 and 19 seconds.
At video 11 seconds the surrounding 25 chunks have arrived. This explains why
terrain coverage improves along the shot independently of camera altitude.

The packet audit supports both vanilla 12-byte bulk chunk headers and the
installed extended-ID 16-byte headers. Treating the latter as vanilla headers
gives incorrect coordinates and empty section masks.

Two export changes address separate problems:

- **Fill missing terrain from later recorded snapshots**, under Advanced export
  settings, is explicit and defaults off. It indexes the first nonempty full
  snapshot for each dimension/chunk and supplies only unloaded chunks within the
  camera's render distance plus one neighbour ring. Chunks already loaded by
  normal playback always take priority; normal later packets still replace
  recovered terrain. Recovery does not dispatch future entity/event packets.
  Terrain damage present in the later snapshot can consequently appear early.
  This is presentation recovery, not a reconstruction of unrecorded history.
- Angelica 2.2.14 replaces `RenderGlobal.updateRenderers`, bypassing the legacy
  force-chunk flag. New late mixins force synchronous builds through
  `SimpleWorldRenderer.prepareFrame` for both main and shadow views. Export
  performs at least two world passes at the same source time and frame index,
  and repeats after new mesh results so shadows catch up with terrain uploads.
  A two-minute per-frame limit prevents an endless terrain retry.

Existing recordings, saved camera keys, server physics and shader-pack sources
are unchanged. The terrain recovery preference survives encoding preset changes
and saves with render settings. Existing settings leave it disabled.

Headless validation: full Java 8 offline build passed, 204 tests passed and nine
optional tests skipped. Coverage includes original/extended packet layouts,
first-snapshot precedence, dimension isolation, truncated-packet failure,
same-frame terrain/shadow convergence, explicit preference persistence, the real
Plane Fight opening coverage, installed Angelica Java 8/17 contracts and existing
aircraft/camera regressions. GPU acceptance remains to be recorded below.

X53 moves queue controls to the basic export tab so they do not overlap the
new Advanced terrain-recovery checkbox. The rendering/recovery implementation
is identical to X52; the full build and regression suite passed again.

The X52 GPU test exposed `ChunkProviderClient.chunkExists` returning an
unconditional true, including missing chunks. X54 checks the provider's shared
`EmptyChunk` placeholder instead, preserving real loaded all-air chunks. A
regression using Minecraft's actual client provider covers this distinction.
Superseded X52 diagnostic renders were moved into the instance backup folder.

X54's full 20-second GPU render recovered 657 missing chunks, removing the
opening hole. It completed 1,200 shader frames at 2560 x 1440 / 60 FPS with
audio and decoded without errors. Consecutive-frame inspection still found
isolated terrain-shadow flashes, so this is retained as a diagnostic capture.

X55 additionally joins Angelica's current visibility search before drawing and
forces `ShadowRenderer.shadowTerrainAlwaysRelays()` during exports. The normal
renderer otherwise reuses terrain shadow snapshots with a 250 ms wall-clock
fallback; render cost must not decide which shadow snapshot an offline camera
frame receives. Both hooks leave normal gameplay and saved graphics options
unchanged. Installed-JAR tests cover the actual Java 8/17 shadow draw gate and
visibility join methods. Short-section and final GPU results follow below.

The two-second X55 shader check at original video 10.017–12.020 seconds
completed all 120 frames and audio. After the first 0.2 seconds of temporal
warmup, the maximum consecutive-frame mean luma step over the terrain half of
the image was 0.446 levels, with no steps above one level. X54's corresponding
full-video region had isolated 3.998-level drops immediately followed by
2.994-level recoveries. Consecutive X55 frames at the previously flashing
section show continuous lighting. This diagnostic measures one image region
and is not a promise about every shader effect or every recording.

The final X55 full-shot export completed all 1,200 frames and 20 seconds of
audio at 2560 x 1440 / 60 FPS, with a clean full-file decode. The same terrain
luma check over video 10–12 seconds has maximum step 0.395 and no steps over
one level. Extracted opening frames show nearby full-detail terrain replacing
the black hole. The original saved camera keys were used unchanged.

Final files in the instance's `replay_videos` folder:

- `terrain-fixed-1440p60-X55.mp4`: full-resolution 20-second corrected export.
- `terrain-before-after-X55.mp4`: labeled 60 FPS side-by-side comparison.
- `terrain-opening-4K60-X55.mp4`: two-second 3840 x 2160 / 60 FPS shader sample,
  all 120 frames and audio verified, clean full decode, low-altitude terrain
  visually present. The 4K check recovered 339 missing chunks.

The source `darkTerrain.mp4` and `Plane Fight.mcpr` were preserved. The user
explicitly chose later recorded terrain recovery; that export preference is on.
The usual 1440p/60, 50 Mbps preset and preview-off preference were restored
after testing. X55 is installed; prior JARs and diagnostic captures are in
`replaymod_backups/terrain-X52-20261021-180240/`.
