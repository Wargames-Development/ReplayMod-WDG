# Replay cloud drift (X43)

Cloud translation uses a separate renderer tick counter plus partial ticks.
This makes it dependent on client tick phase/history rather than solely on the
source frame. X43 replaces only that combined animation-time value with replay
milliseconds / 50. It does not replace the partial tick used for camera position
or cloud color, change cloud geometry/settings, or modify the live tick counter.
Outside replay the original value passes through unchanged.

The standard RenderGlobal fast/fancy paths and Angelica's optimized
CloudRenderer are covered. Angelica's hook loads through the existing UniMixins
late loader when Forge reports the mod present. The installed Angelica 2.2.14
base and Java 17 implementations were inspected and their clock-local contract
is checked by an opt-in fixture test (`REPLAYMOD_TEST_ANGELICA`).

Tests check 24/30/60/120 FPS progression across fractional-tick wraps, identical
time for repeated passes, freeze, rewind, and unchanged native time in live play.
These establish deterministic cloud drift, not an end-to-end visual capture.
Re-export the same shot, check clouds through a slow camera pan and a frozen
action segment, and compare a second export. Custom world-provider or shader
cloud renderers with their own timing are outside these hooks.

## Angelica startup compatibility (X46)

The reported startup crash occurs before audio initialization: Mixin refuses to
prepare the vanilla cloud local-variable hook on `renderCloudsFancy`, which
Angelica's VBO mixin overwrites at the same priority. `require=0` permits missing
injection sites but does not exempt this priority/overwrite rule.

The render config now uses the existing selection plugin to exclude the entire
vanilla cloud hook when Angelica's early `AngelicaTweaker` core plugin is present.
That class is on Launch's early classpath (unlike ordinary late mod classes).
Angelica's own CloudRenderer hook remains in the late loader. Without Angelica,
the vanilla clock remains enabled. The regression test constructs fresh Launch
class loaders with and without the installed Angelica JAR, verifies both
selections and config registration, and checks that audio hooks remain enabled.
