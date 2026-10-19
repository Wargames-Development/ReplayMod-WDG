# Player recording and replay seeking regression checks

## Angelica pixel readback and empty videos

The X14 export log showed `NoSuchMethodError` for
`GLStateManager.glReadPixels(int,int,int,int,int,int,long)` at frame zero.
Angelica 2.2.14 rewrites the PBO-offset GL11 call but does not implement that
overload. FFmpeg consequently received no frames and wrote an empty MP4.
Angelica does implement the ByteBuffer overload, so colour exports now use
synchronous readback when Angelica is installed. This may reduce capture speed.
Depth-map export is explicitly rejected with Angelica because it requires the
incompatible PBO route; the normal MP4 preset does not request a depth map.

Failure popups attach to the live parent screen rather than closed settings.
Capture unbinds PBOs/framebuffers on errors and cleanup resets the render colour.
FFmpeg output is not reported as successful unless at least one frame was sent.
The shared vertical-flip processor now serializes access to its scratch arrays;
a concurrent-frame regression test checks every output pixel across 64 frames.

1. Export a short MP4 with Angelica. Confirm progress passes frame zero and the
   completed file contains a playable video stream of the expected duration.
2. Repeat with Preview enabled and disabled, then cancel an export midway.
3. Confirm returning to the replay leaves normal colours and interactive controls.
4. Trigger an invalid output/encoder setting and confirm the error stays visible.

## Angelica outline state during export

Angelica 2.2.14 starts its outline phase at `drawSelectionBox` HEAD and ends it at
RETURN. ReplayMod's former cancellable HEAD injection could return after
Angelica's start hook without reaching its end hook. Subsequent tile-entity
rendering then crashed with `outline = true`, including the normal game frame
after export failed. Disabling shader packs did not remove those hooks.

Outline suppression now redirects both `EntityRenderer.renderWorld` call sites,
skipping the whole selection-box invocation during export. Ordinary gameplay
still invokes the original method with all its hooks. Shader packs are no longer
disabled or changed by ReplayMod.

A Mixin 0.7.11 transformation smoke check against the MCP 1.7.10 renderer verifies
that both call sites redirect and no direct selection-box calls remain in
`renderWorld`. The installed Angelica bytecode was checked for the matching
HEAD/RETURN pair. These checks do not replace an OpenGL render test.

1. With Angelica installed, look at a selectable block and export a short path.
2. Repeat with shader packs enabled and disabled; confirm no outline-state crash.
3. Cancel an export, then move the camera and start another export.
4. Close the replay and join a live world. Confirm block selection outlines work.

## Replay controls and the chat key

The replay overlay handles the configured chat/command keys before vanilla's
key-binding lookup. In 1.7.10 that lookup only retains one binding per key;
weapon mods sharing T can prevent the chat binding's press counter from updating.
While the overlay is active, attempts to open `GuiChat` instead expose the replay
controls, retaining their mouse input screen rather than a chat screen that
absorbs timeline clicks.

1. Open a replay, press T, and click Play/Pause, the timeline, and Render Camera
   Path. Repeat while paused and with weapon controls also bound to T.
2. Press Escape to resume free-camera movement, then use `/` to expose controls.
   Remap Chat and repeat using the new key.
3. Check dragging keyframes and opening/closing render settings. Their keyboard
   and mouse input must still work; T in text fields must remain ordinary text.
4. Close the replay and join a live world. T and `/` must open normal chat again.

## MCHR turret rotation

The affected recording contained the recorder's movement, aim, and mounting
packets but no `S0CPacketSpawnPlayer`. The recording log showed vanilla's spawn
writer failing on a null profile string. In 1.7.10 profile properties always
include a signature string on the wire, while a local session can have unsigned
properties. Losing the pilot's spawn leaves MCHR without a rider from which to
read turret yaw and pitch.

The recording writer now handles unsigned properties and missing profile names
without modifying the live session profile. The packet format remains 1.7.10.

1. Record a new session with MCHR installed. Mount a tank, rotate its turret
   independently of the hull, elevate/depress the gun, drive, and dismount.
2. Replay the recording in free camera and while spectating the pilot.
3. Confirm the pilot exists, mounts/dismounts correctly, and the turret follows
   the recorded aim rather than the replay camera.
4. Seek backward and forward through the mounted section, then render a short
   camera path through it. Check turret yaw and gun elevation again.

Use new recordings for this check. Recordings that already lost the spawn packet
are not migrated.

## MCHR observer input and camera ownership

MCHR's `MCH_ClientCommonTickHandler` runs live key polling, hull/turret angle
updates, recoil, rotation limits, HUD input, and vehicle-camera selection. Those
local-player callbacks are now disabled while a replay is open. Entity ticks,
vehicle rendering and recorded mod packets remain active. The proxy always sees
the observer as the local player; recorded pilots remain remote, including while
explicitly spectated. The old render-scope player substitution is removed.

The replay camera cannot mount an entity. Free-camera mode restores its own view
instead of following an unsolicited MCHR camera dummy. Only recorded remote
players can be selected for player spectating. Holding Sneak exits spectating
even if another handler already consumed the key's press event.
Spectating copies inventory state for rendering rather than sharing the recorded
player's inventory, so local hotbar changes cannot change that player's weapon.

1. Open a new recording in free camera. Move the mouse and press movement,
   vehicle steering, firing and seat-switch keys while watching an occupied tank.
   Only the observer should move; turret, hull and tracks must follow the replay.
2. Pause at an occupied tank and repeat. Its pose must remain unchanged while the
   observer flies around and away from it.
3. Explicitly spectate the recorded pilot, then hold Sneak to exit. Confirm free
   camera persists across subsequent frames and the pilot remains mounted.
4. Seek before/after mounting, backward through the tank section, and across a
   dimension change. Repeat the input checks and render a short camera path.
5. Close the replay and join a live world. Check normal MCHR tank steering,
   turret controls, cockpit cameras and HUDs still work.

## Timeline click during camera-path playback

Camera-path playback uses the packet sender in synchronous mode. Previously a
small forward jump from the replay overlay unconditionally called the async-only
`jumpToTime`, causing the reported `IllegalStateException`. Larger jumps also
unconditionally enabled the async sender afterward. Overlay seeks now use
`sendPacketsTill` and leave synchronous mode under the path player's control.

1. Start a camera path, expose the replay overlay, and click the replay timeline
   less than five seconds ahead. Repeat with a larger forward jump and a backward
   jump.
2. Confirm there is no crash and no independent async playback starts. The active
   path continues to control replay time on subsequent frames.
3. Stop/finish the path. Check ordinary playback, pause, small/large seeks,
   backward seeks, and marker double-clicks.

## Opening a newly recorded replay

The affected `test1` recording stored its local player spawn and a block event
before `JoinGame`. The inbound packets had only scheduled their recording work,
while the locally injected packets were serialized immediately on the main
thread. Playback consequently tried to create a player before its client world
existed; Et Futurum exposed this as a null-world constructor crash.

Packets are now queued immediately on receipt. Main-thread serialization drains
that queue in order, so a local spawn cannot overtake its preceding `JoinGame` or
respawn packet. This corrects new recordings; it does not migrate earlier files.

1. Make a fresh recording with the updated JAR, disconnect, and open it.
2. Confirm the world and recorded player load without a null-world exception.
3. Record a dimension change/respawn, then play and seek across that transition.
   Confirm the recorded player appears in the correct world.

Automated spawn-writer tests cover signed/unsigned/empty properties, missing
profile names, identity, position, rotation, and entity metadata, using the vanilla
1.7.10 packet reader. Queue tests reproduce delayed inbound recording tasks with
immediate local spawn/block events and dimension respawns. In-game rendering and
camera-path input require the manual checks above.
