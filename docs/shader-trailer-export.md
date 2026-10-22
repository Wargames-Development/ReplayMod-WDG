# BountifulStars trailer exports

Target instance: Prism Launcher **Wargames Release**, Minecraft 1.7.10,
Angelica 2.2.14, `BountifulStarsALPHA0.0.3.zip`.

## Compatibility

Angelica uses Iris classes (`net.coderbot.iris`), so the existing OptiFine
`shadersmod.client.Shaders` hooks do not control its shader clock. The late
Angelica mixins now set `frameTimeCounter` from replay source time, `frameTime`
from the export FPS, and `frameCounter` from the captured output frame. Water,
clouds, foliage and other time-based effects consequently follow replay speed
and freeze with the action. Temporal filters still receive a positive frame
duration during frozen shots. Terrain retries retain the output frame's jitter.
Ordinary gameplay keeps Angelica's original clocks.

Angelica also renders hands through its own `HandRenderer.canRender` path,
bypassing Forge's hand event. A late hook now invokes ReplayMod's existing
`PreRenderHandCallback`, hiding the free-camera hand in shader exports and
retaining the normal spectated-player hand rules.

Angelica's installed renderer starts and finalizes its shader pipeline inside
`EntityRenderer.renderWorld`; ReplayMod's direct world capture therefore includes
compositing and the final shader pass. Its final pass writes to Minecraft's main
framebuffer, which ReplayMod resizes to the capture dimensions. The editor viewer
temporarily replaces that same framebuffer with its viewer target.

The compatibility tests inspect the installed JAR's Java 8 and Java 17 timer
classes, the direct world-render hooks, and replay clock behavior at 30/60/120
FPS. These checks do not compile the pack on the user's GPU or visually validate
an exported replay.

## Trailer settings

1. In Angelica's shader selection screen, enable BountifulStars. Its persistent
   setting is `config/shaders.properties`: `enableShaders=true` and
   `shaderPack=BountifulStarsALPHA0.0.3.zip`.
2. Open the recording, prepare the camera path and choose **Export video**.
3. Select **1440p/60** (2560 x 1440, 50 Mbps) or **4K/60**
   (3840 x 2160, 100 Mbps). These presets select normal perspective rendering,
   MP4 with the configured bitrate, no additional supersampling and no name tags.
   The shader pack's own TAA remains available.
4. Export a short sample before a complete trailer. Offline export produces
   every output frame even if rendering takes longer than real time; a live
   gameplay rate of 60 FPS is not required for a 60 FPS exported video.

The shader pack and its existing visual options do not need modification.
Additional supersampling multiplies the internal framebuffer dimensions and
shader memory cost; start with native 1440p or 4K and the pack's own TAA.

## Local validation, 2026-10-21

The actual Prism instance loaded the pack on its Java 21 runtime, applied the
late shader mixins and exported two-second H.264/AAC samples at 2560 x 1440 and
3840 x 2160. FFprobe confirmed exactly 120 video frames and `60/1` for both
nominal and average frame rate, with two seconds of audio. Shader lighting,
water reflections, clouds and final color processing are present in extracted
frames. These initial samples also exposed Angelica's camera-hand bypass,
subsequently fixed by `MixinAngelicaShaderHand`.

Testing used a separate `Shader compatibility smoke test.mcpr` copy of
`Plane Fight.mcpr`. A low camera position in this recording shows missing nearby
terrain both with and without shaders. The X52 packet audit established that
the opening camera is outside the full chunks received at that source time;
those chunks arrive later. Advanced export settings now offer **Fill missing
terrain from later recorded snapshots**, off by default. This restores only
missing terrain and may show later terrain damage early. X52 also finishes
Angelica's main/shadow chunk builds before capture. X55 also waits for the
current visibility result and redraws terrain shadows independently of the
real-time shadow cache. A 20-second 1440p/60 export and a two-second 4K/60
opening passed encoded-file checks with recovered nearby terrain. See
[terrain-export-audit.md](terrain-export-audit.md) for evidence and validation.

X49 was then re-tested at 4K/60: all 120 frames and audio were exported, and an
extracted midpoint frame confirms the free-camera hand is absent. Aerial terrain,
shader sky and water remain present. The same checks passed for the separate
1440p/60 export using that camera path. The verified files are
`replay_videos/shader-test-4K60-X49.mp4` and
`replay_videos/shader-test-1440p60-X49.mp4`. These are short compatibility samples rather than a complete
trailer or an exhaustive check of every biome, weather effect or shader option.

A separate unresolved display issue was observed after exporting: the free
camera/completion-screen background can become green while the cinematic editor
viewer and encoded files have correct colors. This happened with shaders both
enabled and disabled during testing. Turning off path lines did not resolve it.
Do not use the completion-screen background to judge the encoded video's color.

The full headless regression suite has 188 passing tests and 12 optional skips,
including installed-JAR shader clock and hand-render contracts. The original
ReplayMod JAR and instance settings were backed up under
`minecraft/replaymod_backups/shader-export-X48-20261021-103834/`.

## In-game acceptance check

Render the same 3–5 second camera move at 1440p/60 and 4K/60. Include sky,
water, foliage and a moving aircraft, if available. Inspect the actual encoded
file, not only the editor or render preview:

- Shadows, water, clouds and final color grading are present across the image.
- The video has the selected dimensions and a constant 60 FPS.
- Animation speed is consistent between exports and after a seek/re-export.
- Frozen-action shots freeze shader animation while the camera still moves.
- No cropped viewport, stale/black frames, repeated flashes or TAA trails persist.
- `logs/latest.log` has no shader compile/link errors, failed shader mixins or
  fallback to the unshaded pipeline.

Keep the original replay; it can be re-rendered at either resolution without
recording the server again. Export performance and maximum usable shader quality
still depend on the GPU, available VRAM and the particular scene.
