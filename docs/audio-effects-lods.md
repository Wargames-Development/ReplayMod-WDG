# Audio, FMUR camera shake, and Distant Horizons

Game audio is enabled by default in Export cinematic. Existing export settings
without the new flag also enable it. The Game audio toggle persists with export
settings and queued jobs. The ordinary game sound category/master levels apply.

The exporter intercepts game sound events and decodes the chosen OGG assets with
Minecraft's existing JOrbis decoder. A bounded decoded-resource cache, stereo
position/linear attenuation, native sound pitch, repeats, explicit stops, and
tickable sound updates feed a streaming 48 kHz stereo WAV. Each video frame writes
exactly its share of samples; wall-clock render time and pauses cannot stretch the
track. Events are quantized to video frames. Sound effects retain their natural
pitch when the action timeline is slowed; this is an event-based game mix, not
a microphone recording or a capture of other desktop applications. It does not
reproduce OpenAL effects/third-party acoustic processing.

FFmpeg adds the soundtrack to MP4/MOV/MKV (AAC) or WebM (Opus) without re-encoding
the video. PNG exports retain a WAV inside the image directory. Failed muxes keep
both the video and WAV; an audio failure is reported rather than silently claiming
a successful soundtrack. Successful embedded-audio exports remove the temporary
WAV. Cancelling, setup failures, and pipeline failures release audio resources and
restore the game's sound settings. An existing WAV is never overwritten.

Replay-only MCHeli explosion handling observes its documented effect packet and
waits for the matching vanilla/mod sound in the same tick before using a fallback.
Near blasts use the vanilla explosion sample, distant blasts use FMUR's own
distant-explosion method. Existing matching sounds are not layered twice. FMUR's
shake and explosion sound deduplication use the replay clock during playback and
export, and the original clock outside replays. Seeking clears transient effects
and deduplication state so old effects do not spill into the new scene.

## Distant Horizons terrain

New recordings save `wdg/lod-sources.json`, mapping dimensions to their actual DH
cache folders relative to the Minecraft instance. This is a small locator; it
does not copy gigabytes of LOD databases into every recording.

For recordings lacking the locator, ReplayMod reads **framed DH LevelInit
packets** from their packet stream. It chooses a cache only when the recorded
world identity and dimension match exactly one real cache. It does not choose by
database size, timestamp, or arbitrary strings in chat/chunk data. A scan is
bounded to 128 MiB. Multiple world identities in a dimension or multiple matching
folders require an explicit choice.

Replay library's **Distant Horizons terrain source...** chooser provides that
choice. Some real caches are under a server folder named NULL; that name alone
does not mean they are empty. The cache world ID, dimension, size and modification
date are displayed. The empty REPLAY and synthetic ReplayModReplay caches are not
offered as real-world sources. Opening an unmapped recording through the library
opens the chooser. A missing mapping blocks export while DH is installed.

The optional API save-structure override points DH to the chosen cache. Replay
chunks do not replace its terrain: client chunk update taps are suppressed and
DH's read-only world flag remains enabled. Legacy cache migration is suppressed
inside replays. The override is removed after the replay closes. Other source
repositories, including WGMap, FMUR, MCHR and DH, are untouched.

Before capturing each view, the renderer ticks DH's render tree independently
of Minecraft game ticks, so moving a cinematic camera through frozen action
still loads terrain. It renders at the same replay time while pending GPU/file
builds complete and waits for a stable, nonempty set of ready LOD sections in
the current world. Progress/cancel controls stay responsive. A two-minute load
timeout stops the export with a useful error rather than accepting an empty
LOD view. Only terrain already present in the chosen cache can be rendered;
historic distant block changes are not reconstructed from the locator.

## Validation

Automated tests cover exact PCM timing at fractional frame rates, stereo gains,
pitch/resampling/repeats/stops, mixing saturation, WAV format and overwrite
protection, old audio settings, actual FMUR explosion OGG decoding, FFmpeg mux
stream/duration/sample checks, failed-mux preservation, framed DH identity parsing,
ambiguous identities, malformed input, and the existing Test1 recording's identity.
Integration fixtures are read-only and provided through REPLAYMOD_TEST_* variables.

Still requires an in-game check: preview a nearby and distant missile impact;
pause/seek and replay it; export the same shot at 1x and slow motion; check shake
with and without stabilization; verify DH terrain in preview and an exported
frame, including a frozen-action orbit crossing a 16-block camera cell; cancel
during LOD loading and audio muxing; then join a live server and check normal
sound, vehicle controls, and DH updates. No OpenGL/in-game verification is claimed
by the headless/integration suite.
