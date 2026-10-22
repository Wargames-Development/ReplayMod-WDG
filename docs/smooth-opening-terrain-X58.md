# Smooth.mp4 opening terrain and aircraft — X58

`Smooth.mp4` contains 1,500 frames at 2560 x 1440 / 60 FPS, lasting 25 seconds.
The saved `f-15Shot.mcpr` camera maps output 0–25 seconds to replay 12–37 seconds.
Its first camera position is (16.3996, 100.4968, -561.4787), chunk (1, -36).
Extracted opening frames show a large terrain hole and a black aircraft.

The installed JAR and running-client log confirm this video used **X56**, so it
does not exercise the X57 recorded-aircraft lighting correction. Terrain recovery
was enabled, indexed 1,558 chunks and supplied 386 missing chunks; toggling that
setting alone cannot fix the damaged initial terrain described below.

## Confirmed terrain cause

The raw backup `raw/2026_10_22_15_03_22.mcpr` contains the camera's chunk at raw
time 59.890 seconds as a full bulk snapshot, masks 0x003f / 0x001f. The recording's
initial cut ends at raw time 87.440 seconds. Its processed replay lacks that full
chunk. Nearby processed startup packets have zero section masks and only biome
bytes despite the corresponding raw terrain containing actual blocks.

WGCore widens each 1.7.10 chunk section mask from a short to an int. Its bulk
headers therefore use 16 bytes per column instead of 12; single-chunk headers use
17 bytes before the compressed length instead of 13. The compressed terrain
layout is unchanged, verified against actual raw bulk packet lengths.

ReplayStudio's vanilla `SquashFilter` reads the high zero half of those masks as
the entire mask during automatic trimming, then emits empty/corrupted startup
terrain. Separately, the recorder's single-chunk length normalizer assumed the
13-byte vanilla header and rejected valid extended single-chunk packets.

## Changes

- `LegacyChunkPacket.java` recognizes original/extended single-chunk framing and
  the existing VarInt-length anomaly. It narrows only masks at ReplayStudio's
  input boundary, preserving compressed terrain bytes, and widens synthesized
  output back to the native layout. Unsupported masks and malformed framing fail
  explicitly instead of silently erasing terrain.
- `ChunkSquashFilter.java` wraps the existing squash implementation. Its copies
  retain the native-layout flag. Non-chunk packets and modern protocols use the
  existing delegate without conversion.
- `MarkerProcessor.java` uses that adapter for both initial cuts and splits.
- `PacketListener.java` uses the shared framing normalizer, retaining valid
  extended single-chunk packets when recording.
- `LegacyChunkPacketTest.java` and `MarkerChunkRepairTest.java` exercise both
  widths, malformed lengths, copied squash state, coordinates, section masks,
  block/light/biome bytes and the actual raw recording through MarkerProcessor.

No gameplay/server protocol, shader-pack source, camera keys or movement
interpolation changes are introduced. Ordinary recorded packets retain native
wire bytes. X58 includes X57's recorded-aircraft lightmap correction.

## Repair and verification

The real MarkerProcessor was run on a **workspace copy** of the raw backup.
The repaired output contains full terrain for **361 / 361 chunks** in the opening
camera's radius-nine square. The old processed replay contains only 227 of them
anywhere in its recording and 147 loaded at replay 12 seconds.

The camera chunk's 71,936 decompressed terrain bytes are identical to its raw
source, SHA-256 `d8f1459b9c14391180974a3ca9bb3dce1644158bbce50b1e6bb56ef26e8c7a31`.
The repaired recording remains 40.100 seconds long. A final repaired copy combines
that corrected packet stream with the original processed recording's metadata,
camera timelines, markers, mod list and other entries **byte for byte**. Its packet
CRC was recalculated and its ZIP integrity checked.

Java 8 `gradlew.bat --offline build` passed: **230 tests, 220 passed, 10 optional
skips, zero failures/errors**. Actual raw/processed recordings, installed MCHR,
Angelica, the F-15 lighting fixture and previous orbit checks were enabled.
`git diff --check` passed. Packaging advanced X57 to X58.

Installed into Wargames Release, with SHA-256 source/destination checks:

- `mods/ReplayMod-WDG-2.5.2-WDG_X58.jar`
- `replay_recordings/f-15Shot-repaired-X58.mcpr`

The old X56 JAR is backed up under
`replaymod_backups/smooth-X58/ReplayMod-WDG-2.5.2-WDG_X56.jar`.
The original `f-15Shot.mcpr`, raw backup, `Smooth.mp4` and other videos were not
changed. A second repaired artifact remains in the repository's
`build/repaired-replays/f-15Shot-repaired-X58.mcpr`.

## GPU acceptance still required

Windows computer-use approval timed out before the client check could start.
No corrected shader video was captured in this turn. Launch Wargames Release,
open **f-15Shot-repaired-X58**, and export its existing saved path. Check the first
two seconds for complete nearby terrain and consistent aircraft lighting, then
check the full shot. No shot regeneration is needed. Exporting the original
damaged replay again does not restore its erased startup terrain.

This repairs terrain preserved in this raw backup. Data rejected during older
recordings and absent from every backup cannot be reconstructed by this adapter;
shader state and frame pacing also remain separate from packet-data validation.
