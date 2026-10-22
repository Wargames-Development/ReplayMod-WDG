# Aircraft black flashes with shaders — X57

The supplied `randomBlackness.mp4` is a 10-second 2560 x 1440 / 60 FPS export
containing 600 frames. Extracted frames show the F-15 becoming a black silhouette
around video 0.5 and 4–4.5 seconds while the surrounding terrain remains lit.
This is separate from the movement issue, which the user reports is now smooth.
The instance uses Angelica 2.2.14 and BountifulStars ALPHA 0.0.3.

## Evidence and correction

The new `F-15EX Test.mcpr` contains 330 aircraft snapshots. Every one carries
`LIT1` packed light **0x00f00000**, full exposed skylight. Original chunk packets
do not cover the sampled aircraft chunk until replay 4.253 seconds; the export
log also confirms optional terrain recovery supplied 284 missing chunks. This
does not prove the GPU lightmap value of each bad frame, but it establishes
incomplete native terrain coverage and consistent server light data.

MCHR's unified renderer overwrites loaded-aircraft pose brightness with
`aircraft.getBrightnessForRender`, sampled from the client entity/world. Its
nearby path accepts zero when native light data is unavailable; the distant path
already applies an exposed-sky fallback. ReplayMod's corrected body can therefore
be present and lit in the recording while its native entity lighting is zero.
The aircraft reader previously discarded the recorded `LIT1` values entirely.

The reader now retains the recorded packed light alongside each pose and carries
the earlier discrete light value through interpolation. The existing pose hook
exposes this value. A narrowly scoped redirect supplies its block/sky components
to the normal lightmap call inside `MCH_RenderFarVehicle.renderContact` whenever
a supported recorded pose is available in replay viewing or export.

This preserves actual recorded darkness and torchlight, rather than forcing
full brightness. The thermal full-bright call and the renderer's lightmap
restoration calls are outside the redirect. Missing/legacy light data, unmatched
poses and normal gameplay retain native lighting. The generated and packaged
refmap maps the MCP setter to the installed `OpenGlHelper.func_77475_a(IFF)V`
call. The source and installed bytecode confirm that ordinal 1 is the normal
lightmap call, following the thermal call.

## Files and validation

- `RecordedAircraftFrames.java`: retain and sample LIT1 packed light.
- `ReplayAircraftExport.java`, `MixinMCHExportPose_MCHeli.java`: expose recorded
  lighting through the existing pose interface and index lifetime.
- `MixinMCHFarVehicle_MCHeli.java`: replace only normal recorded-aircraft lightmap
  coordinates, preserving the surrounding renderer and shader pipeline.
- `RecordedAircraftFramesTest.java`, `MCHReplayCompatibilityTest.java`: cover
  light sampling, real darkness/torchlight, native fallback, live-game isolation,
  installed render-call contracts and the actual F-15 recording at all 600 frames.

Java 8 offline compilation, focused regressions and the full local X57 build
passed. Installed MCHR and Angelica plus the real F-15 and Plane Fight recordings
were used as read-only fixtures. The build advances X56 to X57 and produces
`build/libs/ReplayMod-WDG-2.5.2-WDG_X57.jar`. No MCHR sources, shader-pack files,
instance settings, recordings or existing videos were changed. The new JAR was
not installed into the instance. Movement interpolation is unchanged.

## Remaining GPU acceptance

Restart with X57 and re-export the same shot with the same shader settings.
Inspect the earlier black sections around video 0.5 and 4–4.5 seconds and the
rest of the 10-second shot. Compare replay viewing as well as the encoded file.
Check an indoor/night aircraft and a live world for preserved lighting.
No camera-shot regeneration is needed for this lighting-only correction.

The regression verifies recorded data and the rendering hook, not final pixels
on the running GPU. No corrected shader video was captured during this change.
Shader phase/state issues remain a separate possibility if a new capture still
flashes after the native-light-data correction; no speculative shader-pass or
shader-pack changes were made.
