# Studio follow-up fixes (X47)

- Esc recording controls remain available when Record Server or Record Singleplayer
  is unchecked. An unchecked preference starts the session cut/stopped, so manual
  recording can retain the required login and world state.
- Saved recording rows have stable 1-based numbers, including while scrolling.
- Render progress shows frame counts immediately above the bar and centers its
  terrain-loading status. Pause/play uses drawn icons beside Cancel. Cancellation
  opens a modal confirmation with red action text; Esc dismisses that confirmation.
- Markers and saved paths fit their actual panels. Export, queue and file picker
  fill the window with an opaque background, without an outer dimmed rectangle.
- The camera viewer extends to the timeline border without its shortcut footer.
  Fullscreen hides editor chrome, enters Minecraft fullscreen if necessary, and
  restores the previous window mode on return via Esc or Ctrl+F.
- IN/OUT and speed text sits above the replay track, clear of its red connectors.
- Viewer capture follows the configured preview frame rate instead of a fixed
  30 FPS cap. Generated camera samples share smooth splines, while vehicle-track
  sampling uses continuous monotone tangents. Older saved generated paths are
  upgraded when opened; recorded-action time interpolation remains unchanged.
- MCHR's unified aircraft render pass also draws its mounted riders using that
  exact body pose. Seat/turret offsets are rebased into the smoothed body frame;
  temporary rider coordinates, texture state and lighting are restored afterward.
  This applies during playback and export and respects MCHR's rider-render setting.
- Recorded flare spawns trigger positional deployment audio, with nearby paired
  flares grouped into a single burst. Missile explosion fallback audio remains
  audible from distant cinematic viewpoints, using the cinematic camera as its
  listener and preserving the impact direction. Seeked historical effects remain
  suppressed.

Validation: Java 8 offline Gradle build succeeded. The suite ran 195 tests with
185 passing and 10 optional tests skipped. Current MCHR build classes were supplied
for compatibility checks, and the GPU viewer test ran with the cached LWJGL natives.
Regressions cover continuous camera velocity, legacy generated paths, rigid seat
offsets through rapid manoeuvres, and distant impacts producing audible offline PCM.
This does not replace a visual/listening check in the running modpack. Existing
video files need to be rendered again to include these changes.
