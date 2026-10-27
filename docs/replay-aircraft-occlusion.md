# Aircraft visibility behind terrain

MCHR already uses WGMap's CPU terrain queries and optional GPU depth pass.
The GPU pass declines shader/scissor/stencil contexts it cannot safely support.
Its CPU fallback depends on available, correctly identified occlusion snapshots.
Recorded WGMap handshakes alone cannot supply missing `.wgor` baseline geometry.

The installed Wargames Release pack was inspected with MCHR X190, WGMap X124,
and the selected Distant Horizons build. F-15EX Test, Plane Fight, and the repaired
f-15Shot all identify server `4f258df0-b6e5-4792-8884-26a8f44da784`, world
`12d945db-385f-4e1b-bb66-69affbeca173`, dimension 0. No local `.wgor` regions
were found for that world; regions present belonged to another world. Those
regions must not be used to hide replay vehicles.

## Change

ReplayMod's optional MCHR hook preserves native WGMap CLEAR/BLOCKED answers.
For other answers, it traces the same world-space camera-to-target ray through:

1. Already-loaded replay chunks, without requesting/loading chunks.
2. Primitive terrain-column snapshots from the explicitly selected DH terrain
   cache for the current replay dimension and world wrapper.

The existing MCHR nine-point envelope decides whole-model occlusion; a blocked
center ray alone does not hide a plane with exposed edges. Opaque solid DH spans
can block; liquid, air and translucent spans do not. Missing, failed, unsupported
or coarse columns remain unknown. A known mountain still blocks after a missing
section earlier in the ray. No unknown result invents a blocker.

`TerrainVisibility` owns bounded RAM-only ray traversal. `ReplayTerrainVisibility`
uses DH's public data API on a daemon worker, copies data into primitive snapshots,
and retains at most 512 chunks / 32 MiB with 256 queued or in-flight reads.
Missing/partial data retries after two seconds. The DH soft cache is cleared every
16 reads. Each ray has an 8192-column / 32768-block bound and the MCHR pass shares
a 65536-column budget. Budget exhaustion returns unknown. Read completion changes
the terrain generation, invalidating MCHR's cached visibility even while paused.
World changes discard results; closing a replay interrupts the worker and releases
the DH cache. No WGMap connection, private store, cache identity or network state
is changed. WGMap and MCHR jars remain unchanged.

Export uses the existing terrain retry loop to wait for pending occlusion reads
and redraw after their results arrive before capturing the frame. This does not
make nonexistent terrain available: missing data stays unknown after a completed
read. Ordinary viewing stays asynchronous and can briefly show a vehicle while
new terrain columns load, especially following a seek or fast camera move.

## Validation and remaining checks

Focused tests cover covered/above-mountain paths, missing-before-known terrain,
vertical, climbing, diagonal and negative-coordinate rays, traversal budgets,
nonblocking reads, cancellation/world lifetime, generation changes and export
redraw readiness. An opt-in test loads the installed DH jar and exercises its real
public API types with controlled terrain columns, including opaque/translucent,
air, liquid and missing data. The installed MCHR bytecode is checked for both
injection targets. Existing real-aircraft interpolation tests remain enabled.

Client-only hooks are selected only when MCHR is present; DH is discovered only
through the existing selected replay source. No common/server registration or
network/save format changes are introduced.

In-game check after restarting: open the replay with its correct DH cache,
put a distant aircraft fully behind a mountain, then move above/around the ridge.
Check free camera, paused viewing, an automated shot and exported frames, with
shaders both enabled and disabled. Repeat after a backwards seek. Full in-game
pixel/visual verification has not been performed. The fallback is whole-model
culling; partial silhouettes still depend on the native terrain depth path.
