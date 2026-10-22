# ReplayMod-WDG

ReplayMod-WDG is the Wargames Development Minecraft **1.7.10-only** ReplayMod fork.

This branch deliberately does not retain ReplayMod's historical multi-Minecraft-version build graph. There are no 1.8+, Fabric, Loom, `setCoreVersion`, or ReplayMod preprocessor projects in normal development.

## Target

- Minecraft: 1.7.10
- Forge: 10.13.4.1614
- MCP mappings: stable_12
- Java: 8 (x86_64 Java 8 for runtime development on Apple Silicon)
- ForgeGradle: anatawa12 ForgeGradle 1.2, pinned to 1.2-1.1.1

## Cinematic studio

**Auto shots [G]** creates Orbit, Follow and Fly-by camera paths around a fixed
point or recorded MCHR vehicle. Edit duration, replay In/Out, framing, easing and
roll; review the draft, then place and preview it as one undoable shot. See
[automated shots](docs/automated-shots.md) for controls and tracking details.

The replay interface uses native Minecraft controls styled after WGMap's flat
blue-gray menus. WGMap is a read-only design reference, not a runtime dependency.

1. Open a recording from the replay library and press **T** for the editor.
2. Press **Esc** to fly to your first view. Press **I** to capture the camera and
   replay time together, or reopen the editor and click **Add shot**.
3. Use **Next slot + step**, fly to your next view, and add another shot.
4. Click keyframes to inspect them; drag them on the **Video** timeline to retime
   them. Type exact output time, replay time, position, yaw, pitch, and roll.
   Pose changes preview live; **Apply** stores the edit. **Ctrl+Z** undoes it.
5. Click **Timing / speed** to choose replay **In / Out**, video duration or action
   speed. Red lines connect that replay range to its video range. Speed presets
   include slow motion, normal speed, fast motion and frozen action. Use
   **Apply + preview** to test the selected move. **Whole path: normal speed 1x**
   is a shortcut for a whole sequence. **Camera move** selects smooth or straight
   motion. **New steps: frozen action** affects newly added steps.
6. Click **Export video**. Choose the output path with **Browse**, then render
   or add multiple named exports to the queue.

**Replay time** identifies the action in the recording; **output time** places
that action in your finished video. For example, mapping replay 10–12 seconds
onto output 0–4 seconds makes the action play at half speed. Equal replay times
at different output times hold the action still while the camera moves.

The editor also provides named camera paths with clipboard import/export, scene
markers, a native file picker, render settings, and a render queue. **F2** opens
the still-image exporter. Existing `.mcpr` recordings and camera-path data keep
their format. See [studio validation](docs/cinematic-studio.md) for controls and
the checks still required in a running client.

**Fit path**, the mouse wheel and **Set zoom span** change the visible timeline
only. **Timing / speed** changes the actual clip. See the
[timing workflow and audit](docs/timing-workflow-audit.md) for examples.

## Setup

For BountifulStars with Angelica and 1440p/60 or 4K/60 trailer exports, see
[shader trailer export](docs/shader-trailer-export.md).

On Windows (PowerShell):

```powershell
.\setup-1.7.10.ps1
```

On macOS/Linux:

```bash
./setup-1.7.10.sh
```

On Apple Silicon Macs, this selects an **x86_64 Java 8** JDK for Minecraft 1.7.10/LWJGL 2 development. Use that same JDK for IntelliJ's generated `Minecraft Client` configuration.

For IntelliJ import and build instructions, see [SETUP.md](SETUP.md#intellij-idea).

## Build

```bash
./gradlew clean build
```

Builds use the WG `_X` counter, for example `ReplayMod-WDG-2.5.2-WDG_X10.jar`.
Each successful packaging invocation advances the counter once. The same full
version appears in Minecraft's Mods list, crash reports, and recording metadata.
See [build numbering](SETUP.md#build-numbering) for details.

See `SOURCE-PROVENANCE.md` for the source-normalisation provenance used to create this native 1.7.10 tree.
