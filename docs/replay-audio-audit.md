# Replay export audio (X44)

The offline audio path had several gaps in its sound-manager contract:

- MCHR's engine updater queried vanilla `isSoundPlaying`, which knew nothing
  about offline voices. During export that query now uses offline voice state.
  Duplicate loop starts preserve phase; explicit stops and restarts still work.
- Forge may replace an ISound. The original request now retains query/stop
  ownership while the replacement supplies position, volume, pitch and updates.
- Warm-up previously discarded one-shot sounds and did not advance loops.
  The existing one-second pre-roll now mixes discarded samples, preserving
  tails that began in that interval and advancing engine phase. Starts within
  the first second of a recording now receive the available shorter pre-roll.
- Delayed sounds now belong to the offline session. Replay ticks trigger them;
  stop and stop-all also cancel pending entries. Progress-screen waits do not
  advance their clock. Tickable sounds update once per replay tick.
- MCHR simulates engine range by compressing sound-source distance toward the
  player. A fast follow camera can travel beyond that source's short attenuation
  radius between 20-Hz updates, producing silence while the plane stays nearby.
  A late optional engine hook binds the sound to its aircraft. The mixer samples
  the recorded export pose and current camera every frame, then applies the
  original range/rider rules. No aircraft position, throttle or flight state is
  modified. If no recorded pose is available it uses the entity position.
- FMUR's explosion-deduplication clock hook now loads after Forge discovers
  optional mods, like the MCHR hooks. It uses replay time during replay and
  retains native time outside replay.

All new sound behavior is export-scoped except the existing replay explosion
clock. Native live-game playback, category levels, explicit stop events and
silent exports retain their behavior. An MCHR update is not required.

Regression coverage runs the actual offline voice lifecycle/mixer without
OpenAL, tests 400-block/s follow-camera motion, validates the installed MCHR
binding fields/methods, checks complete decoding of installed plane/helicopter/
GAU-8 sounds against FFprobe durations, and exercises FFmpeg soundtrack muxing.
Other tests cover elapsed-sample duration at multiple frame rates and repeats.

These are automated regression checks, not an in-game listening test. Validate
the same shot after restarting: engine during a fast follow, repeated gunfire,
explosions, start partway through a sound, pause/resume, then export again with
preview off. A recording cannot reproduce packets it never received, missing
sound assets cannot be reconstructed, and sounds beginning before the pre-roll
interval are not reconstructed by this change. Distant visual-only aircraft
snapshots do not contain engine/throttle audio state.

## Playback follow-up (X45)

The report affects normal replay playback as well as exports. Native playback
was not covered by X44's export-only voice handling. X45 also:

- Pauses positional world sources when replay time is paused and resumes those
  same channels, preserving their playback positions. UI clicks remain usable.
  Minecraft's GUI-focus resume calls cause replay pause to be reapplied on the
  next listener update. Stop-all discards old ownership.
- Reuses the aircraft binding for native audio. The existing per-frame listener
  update supplies the exact interpolated listener coordinates; engine sources
  use interpolated aircraft positions and MCHR's original distance/rider rules.
  Stopped, expired and unloaded voices release bindings. Live gameplay sources
  are not repositioned or paused by this path.
- Gives repeating MCHR sources priority during replay, so ordinary transient
  effects cannot steal their native sound channels. Existing priorities are
  retained, and live-game allocation is unchanged.
- Stops the previous scene's sounds during a manual replay jump and suppresses
  historical sound starts while seeking. Engine retry cooldown resets once per
  seek revision, rather than waiting another twenty simulation ticks after a
  stopped channel. Normal retry backoff remains in place between seeks.

Playback tests use a fake native backend to check pause/resume ownership,
GUI-focus resume, seek cleanup, engine priority, source interpolation at
400 blocks/s, and untouched live-game behavior. The actual SoundManager bytecode
is checked for the exact listener and source-allocation injection sites.
They do not substitute for a listening test with the running modpack.
