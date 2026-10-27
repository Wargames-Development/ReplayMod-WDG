package com.replaymod.studio;

/** User-facing help shared by the native studio controls. */
final class StudioHelp {
    private StudioHelp() { }
    static String button(String screen, int id, String title) {
        if ((screen.equals("StudioExport") && id==33) || (screen.equals("GuiVideoRenderer") && id==2))
            return "Show or hide the video preview during export. Uncheck it to skip preview pixel copies and texture uploads; the exported video is unchanged.";
        if (screen.equals("StudioAutoShot")) switch (id) {
            case 1: return "Move the camera around the target.\nExample: a 360-degree sweep makes one full orbit.";
            case 2: return "Keep the camera at a chosen bearing. Vehicle relative follows recorded position and heading; World relative holds the route at Replay In.\nExample: choose Vehicle relative and bearing 180 for a chase shot.";
            case 3: return "Move past the target while looking at it.\nExample: travel from -50 to 50 with a side offset of 12 blocks.";
            case 4: return "Set Replay Out to Replay In plus video duration for normal-speed action.\nExample: In 0:14 and duration 0:10 gives Out 0:24.";
            case 5: return "Set Replay Out equal to In to freeze the recorded action while the camera moves.\nExample: orbit a frozen aircraft.";
            case 6: return "Place this draft just after the last existing camera key. Changes Video start only.\nExample: after a shot ending at video 0:30, append at 0:30.050. Replay In must still be at or after the preceding shot's replay time.";
            case 7: return "Cycle available vehicle targets and the fixed-point option.\nExample: choose the F-16 by name/ID. A fixed point does not follow a moving vehicle.";
            case 8: return "Refresh vehicles and select the one aimed at, or the nearest within 12 blocks if none is aimed at.\nExample: close this panel, fly toward an aircraft, then reopen and pick it.";
            case 9: return "Toggle recorded target movement. Requires a selected vehicle.\nExample: On follows its recorded route; Off keeps its current XYZ as a fixed target.";
            case 10: return "Choose how smoothly the camera's framing changes. Recorded action speed stays separate.\nExample: Ease in / out gently starts and finishes a pull-back.";
            case 11: return "Recalculate the draft using these settings and retry loading vehicle motion if needed.\nExample: after changing Replay In/Out, check the route or error shown on the right.";
            case 12: return "Insert the valid draft into an empty video range as one undoable shot. Wait for loading/errors to clear.\nExample: place the shot, then play it from the editor.";
            case 13: return "Insert the valid draft and immediately play its camera move. Wait for loading/errors to clear.\nExample: use this to watch the selected aircraft being followed.";
            case 14: return "Choose the camera path's reference. Vehicle relative follows position and heading, without aircraft pitch or bank. World relative anchors the path at Replay In, using the starting heading for Follow / Fly-by. Both keep aiming at the target. Existing point offsets are interpreted in the chosen reference.";
            case 15: return "Switch the diagram between Top (X/Z) and Side (X/Y). Drag numbered points to bend the camera path; action timing stays unchanged. Use both views to adjust all three axes.";
            case 16: return "Discard manual point adjustments and restore five points on the generated path. Distance, height, timing and the other shot settings stay as entered.";
            case 17: return "Fit the entire camera and target route into this diagram. The diagram scale stays fixed while dragging so points follow the mouse predictably.";
            case 18: case 19: return "Select the previous or next path point. Useful when the start and end of an orbit overlap. Selected points are highlighted in gold; drag one in either view to adjust it.";
            case 20: return "Insert a point halfway through the interval after the selected point, up to 16 points. The new point starts on the current adjustment curve. Select an earlier point if the end point is selected.";
            case 21: return "Remove the selected interior point and smoothly join its neighbours. The start and end points cannot be removed; drag them instead.";
            case 22: return "Undo a spline drag, point addition/removal or Reset path. Each drag is one action; this draft retains the latest 32 actions. Ctrl+Z works even when a text field has focus. Other shot settings stay as entered.";
            case 23: return "Redo an undone spline edit with Ctrl+Y or Ctrl+Shift+Z. Making a new spline edit clears redo history. Draft history lasts until this Auto Shot panel closes.";
        }
        if (screen.equals("CinematicEditor")) switch (id) {
            case 1: return "Open your recordings and folders.\nExample: choose another replay to edit.";
            case 2: return "Leave the editor to fly the camera freely.\nExample: frame your next shot, then press I to capture it.";
            case 3: return "Play the camera path from the playhead, or pause at the current position.\nExample: pause to inspect framing; press Space again to continue.";
            case 4: return "Undo the last camera-path edit.\nExample: remove an accidentally placed automated shot in one step.";
            case 5: return "Restore the last undone edit.\nExample: bring back a shot removed with Undo.";
            case 6: return "Save, load and name separate camera sequences.\nExample: use a separate path for an earlier part of the recording instead of reversing replay time.";
            case 7: return "Open video export settings for the current camera path.\nExample: choose 1080p, an output filename, then Render video.";
            case 8: return "Move the camera-path playhead to the Video time entered above.\nExample: 0:15 previews the fifteenth second of your finished video.";
            case 9: return "Seek the source recording to Replay time without moving the video playhead.\nExample: find an aircraft maneuver at replay 0:32 before capturing it.";
            case 10: return "Copy the current free camera's position and angles into the shot fields.\nExample: fly to a better angle, then apply it to the selected shot.";
            case 11: return "Store the time, position and angle fields as a new shot or update the selected one.\nExample: change Height/Y, then apply to save the adjustment.";
            case 12: return "Switch the selected camera move between smooth and straight interpolation. Requires a move between shots.\nExample: use Straight to avoid a curve near an obstacle.";
            case 13: return "Delete the selected shot. With Samples Off, a generated shot is removed as a group.\nExample: delete a follow shot, then Undo if needed.";
            case 14: return "Deselect the current shot so the next saved pose becomes a new shot.\nExample: begin another camera position instead of overwriting the selected one.";
            case 15: return "Capture the current camera and replay time at the video playhead.\nExample: fly to a starting view and press I.";
            case 16: return "Advance the video slot by Step. Replay time also advances unless new steps use frozen action.\nExample: Step 2.000 creates a slot two seconds later.";
            case 17: return "Set how much video time is visible on the timeline. This does not retime the video.\nExample: 0:10 shows a ten-second span.";
            case 18: return "Zoom into the video timeline for precise placement.\nExample: zoom in before dragging a shot by a few frames.";
            case 19: return "Zoom out to see more of the video timeline.\nExample: inspect several camera moves together.";
            case 20: case 21: return "Show the previous or next page of camera shots in the list.\nExample: find a shot outside the currently displayed rows.";
            case 22: return "Play or pause the source recording independently of the camera path.\nExample: find the action you want before adding camera shots.";
            case 23: return "Choose whether new steps advance the recording or hold it still.\nExample: frozen action lets the camera move around one recorded moment.";
            case 24: return "Show or hide camera-path lines in the world.\nExample: inspect the route before exporting.";
            case 25: return "Manage named bookmarks in the source recording.\nExample: mark the beginning of a takeoff to find it again.";
            case 26: return "Retime the whole camera path so recorded action plays at normal speed.\nExample: restore 1x after experimenting with slow motion.";
            case 27: return "Edit replay In/Out, video duration and action speed for a move or the whole path.\nExample: stretch two replay seconds across four video seconds for half-speed action.";
            case 28: return "Fit the camera path into the visible timeline and reset the replay view.\nExample: find your shots again after panning far away.";
            case 30: return "Fill the screen with the camera viewer.\nSpace plays or pauses; Ctrl+F or Esc returns to the editor.";
            case 31: return "Restore the default editor panel layout.\nExample: recover panels after resizing them too far.";
            case 34: return "Stop playback and return camera and replay to video time zero.\nExample: press K, wait for seeking to finish, then Space to replay the scene.";
            case 35: return "Capture an ending camera pose using the Step interval.\nExample: capture the start with I, fly to the end view, then press O.";
            case 36: return "Switch the viewer between fitting the entire image and filling its panel with cropping.\nExample: use Fit to judge the exported frame's edges.";
            case 37: return "Draft an Orbit, Follow or Fly-by around a vehicle or fixed point.\nExample: choose Follow, pick the F-16, then Place & preview.";
            case 38: return "Show or hide the individual camera samples inside generated shots.\nExample: leave Off to edit a whole follow shot as one range.";
        }
        if (screen.equals("StudioShotTiming")) switch (id) {
            case 1: return "Switch between editing one camera move and the entire camera sequence.\nExample: choose the whole sequence to retime every move together.";
            case 2: case 3: return "Select the previous or next camera move to retime.\nExample: adjust only the move between your second and third shots.";
            case 30: return "Choose what changes when applying speed: Replay Out or video duration.\nExample: keep duration fixed to fit action into a ten-second video slot.";
            case 10: case 11: case 12: case 13: case 14: return "Choose this recorded-action speed preset.\nExample: 0.5x plays two seconds of recording over four seconds of video.";
            case 15: return "Hold recorded action at Replay In while the camera moves.\nExample: move around a frozen aircraft.";
            case 21: return "Apply replay In/Out over the entered video duration.\nExample: In 0:14, Out 0:22, duration 0:10 gives 0.8x action.";
            case 22: return "Apply action speed using the selected speed-change mode.\nExample: use 1x for normal-speed action.";
            case 23: return "Apply the timing settings and play the edited range.\nExample: compare a slow-motion move with its original timing.";
            case 24: return "Shift the whole path to start at video zero.\nExample: remove empty video before the first camera shot.";
        }
        if (screen.equals("StudioExport")) switch (id) {
            case 1: return "Show resolution, frame rate, output and image settings.\nExample: use the 1080p preset for a standard video.";
            case 2: return "Show encoder, camera export and panoramic settings.\nExample: browse to FFmpeg if it was installed in a custom location.";
            case 4: return "Cycle the output encoding format.\nExample: choose a video format for playback or PNG for an image sequence.";
            case 5: return "Cycle rendering modes, such as a normal camera view or panoramic output.\nExample: use the normal view for a conventional follow shot.";
            case 6: return "Toggle player name tags in the exported video.\nExample: turn Off for clean cinematic footage.";
            case 7: return "Cycle antialiasing to smooth image edges. Some output modes disable it.\nExample: increase it if aircraft edges look jagged.";
            case 8: return "Choose how frequently the on-screen render preview refreshes.\nExample: once per second reduces preview overhead.";
            case 10: return "Restore the default encoder arguments.\nExample: recover after a custom FFmpeg command fails.";
            case 11: return "Also export camera-path data alongside the render.\nExample: use it in an external editing workflow.";
            case 12: return "Toggle spherical metadata for compatible 360-degree videos.\nExample: enable for a player that recognizes panoramic video.";
            case 15: return "Render the current camera path to the chosen output.\nExample: set a filename, resolution and frame rate before starting.";
            case 16: return "Save these export settings as a queued render job.\nExample: queue several versions before rendering them together.";
            case 17: return "Open the list of queued render jobs.\nExample: review jobs, then render the queue.";
            case 18: case 19: case 20: return "Stabilize this camera rotation axis during export.\nExample: lock roll to keep the horizon level.";
            case 30: case 31: return "Fill in a standard output resolution preset.\nExample: 1080p uses 1920 by 1080 pixels.";
            case 32: return "Toggle recorded game audio in supported video exports.\nExample: include engine sounds in a follow shot.";
        }
        if (screen.equals("ReplayLibrary")) switch (id) {
            case 1: return "Filter recordings by the search text.\nExample: type part of the recording's name, then Search.";
            case 2: return "Move to the parent recordings folder.";
            case 3: return "Read the recordings folder again.\nExample: refresh after copying a replay into it.";
            case 4: return "Open the selected recording, or enter the selected folder.\nExample: select a replay in the list before opening it.";
            case 6: return "Open the recordings folder in your file manager.";
            case 7: return "Open ReplayMod preferences. Changes are saved immediately.";
            case 11: return "Create an edited recording by splitting or removing sections. The original is kept.\nExample: cut a long recording into shorter scenes.";
            case 13: return "Select the Distant Horizons terrain cache used by this recording.\nExample: choose the original world's cache for an older replay missing terrain-source metadata.";
        }
        if (screen.equals("StudioPaths")) switch (id) {
            case 10: return "Delete the selected saved camera path.\nExample: select an unwanted saved sequence first.";
            case 11: return "Rename the selected saved path using the name field.\nExample: name a sequence Takeoff.";
        }
        if (screen.equals("StudioMarkers")) switch (id) {
            case 1: return "Create a named bookmark using the current camera and entered replay time.\nExample: mark the takeoff view so you can return later.";
            case 2: return "Update the selected bookmark's name and replay time.\nExample: rename it to Landing.";
            case 3: return "Move to the selected bookmark's recording time and camera view.\nExample: return to the takeoff marker.";
            case 5: return "Deselect the marker and prepare a new bookmark.\nExample: begin marking a second event.";
        }
        if (screen.equals("StudioReplayTrim")) switch (id) {
            case 1: return "Move the source preview to the entered replay time.\nExample: seek to 0:30 before marking a split.";
            case 2: return "Add a split at the preview's replay time.\nExample: divide a recording at the start of the next scene.";
            case 3: return "Mark the start of a section to exclude from the edited copy.\nExample: start removing a long idle period.";
            case 4: return "Mark the end of the section being removed.\nExample: end the idle section just before the action starts.";
            case 10: return "Write the edited recording using the split/removal list and output location. Keeps the original recording.\nExample: create shorter copies for separate scenes.";
        }
        if (screen.equals("StudioPause")) switch (id) {
            case 1: return "Open the cinematic workspace to edit and preview camera shots.";
            case 2: return "Return to free camera movement.\nExample: frame an aircraft before capturing a shot.";
            case 3: return "Browse your recordings and folders.";
            case 4: return "Open replay preferences. Settings save immediately.";
            case 5: return "Save the replay and leave playback.";
        }
        if (screen.equals("StudioConfirm") && id == 4) return "Confirm the action described on this screen for the listed files.\nExample: review the filenames before confirming deletion.";
        if (screen.equals("StudioRecordingNames")) switch (id) {
            case 4: return "Apply all entered names and delete recordings marked with the trash icon, including rows outside the scroll view.\nExample: name each scene, then Save all once. Escape keeps the original files.";
        }
        if (screen.equals("StudioRecordingNames") && id >= 100) return "Mark this recording for deletion when Save all is clicked. Click again to restore it.\nExample: discard an unwanted take while naming the others. Escape leaves the files unchanged.";
        if (screen.equals("StudioRenderQueue") && id == 5) return "Render the queued video jobs in sequence.\nExample: queue several camera paths, review the list, then start.";
        if (screen.equals("StudioRenderDone") && id == 1) return "Open the exported video, image or image sequence with the system's associated application.";
        if (screen.equals("StudioScreenshot") && id == 5) return "Export the current camera view as a PNG at the entered resolution.\nExample: pause on an aircraft and save a still image without creating a path.";
        if (screen.equals("StudioFilePicker")) switch (id) {
            case 1: return "Open the folder path entered above.\nExample: paste your Videos folder path and click Go.";
            case 2: return "Move up to the parent folder.";
            case 6: return "Use the current folder and entered filename.\nExample: enter takeoff.mp4, then choose the file to return to export settings.";
        }
        if (screen.equals("StudioLodSources") && id == 4) return "Save the selected terrain-cache mapping and open the replay.\nExample: select the matching original world's cache for each dimension first.";
        if (screen.equals("StudioSettings") && id < 12) return "Change this setting and save it immediately.\nExample: click an On/Off option to toggle it; click a choice to cycle its values. Other settings require their dedicated editor.";
        // Shared navigation and small utility screens use the complete button
        // title, so dynamic labels and filenames remain visible in the popup.
        if (title.contains("Back") || title.equals("Done") || title.startsWith("Return to")) return "Return to the previous screen.\nExample: go back after reviewing these settings.";
        if (title.equals("Previous") || title.equals("Next")) return "Show another page of entries.\nExample: find a recording or saved path beyond the current list.";
        if (title.startsWith("Browse")) return "Open the file picker to choose a location.\nExample: select an output folder and enter a filename.";
        if (title.equals("Cancel")) return "Close this operation without confirming it.";
        if (title.contains("clipboard")) return "Transfer saved camera-path data using the clipboard.\nExample: copy a path, then import it to reuse the sequence.";
        if (title.startsWith("Save current sequence")) return "Save the current camera sequence under the name entered above.\nExample: save Takeoff, then create a separate path for earlier footage.";
        if (title.equals("Load selected path")) return "Load the selected saved camera sequence into the editor.\nExample: select Takeoff in the list, then load it.";
        if (title.startsWith("Replace selected")) return "Overwrite the selected saved path with the current sequence.\nExample: update a saved take after adjusting its camera positions.";
        if (title.startsWith("Rename")) return "Open the naming controls for the selected entry.\nExample: use a descriptive scene name so it is easier to find.";
        if (title.startsWith("Delete") || title.startsWith("Remove")) return "Remove the selected entry or edit. Read any confirmation before proceeding.\nExample: select the unwanted entry in the list first.";
        if (title.contains("Open output folder")) return "Open the folder containing the exported files.\nExample: locate your rendered video to play or share it.";
        if (title.startsWith("Name tags:")) return "Toggle player names in the exported image.\nExample: turn Off for a clear cinematic view.";
        if (title.startsWith("Rendering:")) return "Cycle the image rendering mode.\nExample: choose a normal camera view for a standard image.";
        return "Use " + title + " for the selected entry or current settings. Select an entry first when this control requires one.";
    }

    static String field(String screen, int index) {
        String[] help;
        if (screen.equals("StudioAutoShot")) help = new String[]{
            "Video start\nPosition in the finished video, independent of recording time. Example: 0:15 starts this shot fifteen seconds into the video. The range must be empty; Append finds space after the last shot.",
            "Video duration\nHow long this camera move lasts. Example: replay 0:14 to 0:22 over duration 0:10 gives 0.8x action.",
            "Replay In\nWhere to start in the source recording. Example: 0:14 uses footage starting at fourteen seconds. It cannot precede the previous shot's replay time; use a separate saved path for earlier footage.",
            "Replay Out\nWhere to end in the source recording. Example: In 0:14 and Out 0:22 uses eight seconds of action. Equal times freeze it. Out must not exceed the next shot's replay time.",
            "Target X\nFixed target's east/west world coordinate. Disabled while following recorded motion. Example: enter the centre of the scene.",
            "Target Y\nFixed target's world height. Disabled while following recorded motion. Example: use the aircraft's altitude.",
            "Target Z\nFixed target's north/south world coordinate. Disabled while following recorded motion. Example: enter the centre of the scene.",
            "Starting distance / side offset\nCamera distance in blocks; Fly-by uses sideways offset. Example: 24 begins twenty-four blocks from the target horizontally.",
            "Ending distance / side offset\nFinal camera distance in blocks. Example: start 10 and end 24 pulls away from the target.",
            "Starting height\nCamera height above the target in blocks. Example: 10 starts ten blocks above it.",
            "Ending height\nFinal camera height above the target. Example: start 10 and end 20 rises during the shot.",
            "Starting angle / travel\nFollow: bearing 0 is front, 180 behind. Orbit: starting world angle. Fly-by: starting along-track offset in blocks. Example: Follow 180 starts behind the vehicle.",
            "Ending bearing / sweep / travel\nFollow: final bearing. Orbit: total sweep in degrees. Fly-by: final along-track offset. Example: Follow 180 to 180 stays behind; Orbit sweep 360 makes one turn.",
            "Focus height\nAim this many blocks above the target origin. Example: 2 aims near the upper body instead of the ground.",
            "Starting roll\nInitial camera tilt in degrees. Example: 0 keeps the horizon level.",
            "Ending roll\nFinal camera tilt in degrees. Example: start 0 and end 15 gradually tilts the view."};
        else if (screen.equals("CinematicEditor")) help = new String[]{
            "Video time\nPosition in the finished camera path. Example: enter 0:15 and click Go to preview the fifteenth second.",
            "Replay time\nPosition in the source recording. Example: enter 0:32 and click Go to find a maneuver. Video and replay time are separate clocks.",
            "Camera X\nEast/west world position. Example: adjust by a few blocks, then Apply values to save.",
            "Camera Y\nWorld height. Example: raise Y to lift the camera, then Apply values.",
            "Camera Z\nNorth/south world position. Example: adjust framing, then Apply values.",
            "Camera yaw\nHorizontal viewing direction in degrees. Example: change yaw to turn left or right.",
            "Camera pitch\nVertical viewing angle in degrees. Example: positive pitch looks downward.",
            "Camera roll\nTilt around the viewing direction in degrees. Example: 0 keeps the horizon level.",
            "Step\nTime between new video slots. Example: 2.000 advances the next slot by two seconds.",
            "Visible timeline span\nAmount of video time shown on the timeline. Example: 0:10 shows ten seconds; Set zoom span applies it without changing action speed."};
        else if (screen.equals("StudioShotTiming")) help = new String[]{
            "Replay In\nStart time in the source recording. Example: 0:14 begins with the action at fourteen seconds.",
            "Replay Out\nEnd time in the source recording. Example: 0:22 ends with the action at twenty-two seconds. Equal to In freezes action.",
            "Video duration\nDuration of the camera move in the finished video. Example: eight replay seconds over ten video seconds gives 0.8x action.",
            "Action speed\nMultiplier for recorded movement. Example: 1 is normal, 0.5 is half speed and 0 freezes action. Apply speed uses the mode selected above."};
        else if (screen.equals("StudioPaths")) help = new String[]{"Path name\nName for saving or renaming a camera sequence. Example: Takeoff or Landing."};
        else if (screen.equals("StudioMarkers")) help = new String[]{"Marker name\nLabel for a bookmark in the recording. Example: Begin takeoff.", "Marker replay time\nPosition in the recording for this bookmark. Example: 0:32."};
        else if (screen.equals("StudioScreenshot")) help = new String[]{"Image width\nOutput width in pixels. Example: 1920 for a Full HD image.", "Image height\nOutput height in pixels. Example: 1080 for a Full HD image.", "PNG filename\nFull destination path for the still image. Example: choose a folder with Browse and name it takeoff.png."};
        else if (screen.equals("StudioReplayTrim")) help = new String[]{"Replay time\nPosition used to preview a split or cut boundary. Example: 0:30.", "Output location\nDestination for edited recording copies. Use Browse to choose it; the original recording is kept."};
        else if (screen.equals("StudioFilePicker")) help = new String[]{"Folder location\nEnter a folder path, then click Go. Example: your Videos folder.", "Filename\nName of the file in the selected folder. Example: takeoff.mp4 for a video or takeoff.png for an image."};
        else if (screen.equals("ReplayLibrary")) help = new String[]{"Search recordings\nFilter the recording list by name. Example: type takeoff, then Search."};
        else if (screen.equals("StudioRecordingNames")) return "Recording filename\nClick to select the default name and type a replacement. Example: Takeoff or Landing. Save all applies every row, including those outside the scroll view.";
        else return null;
        return index >= 0 && index < help.length ? help[index] : null;
    }

    static String exportField(boolean advanced, int index) {
        String[] help = advanced ? new String[]{
            "FFmpeg executable\nFull encoder program path; blank detects it automatically. Example: use Browse if FFmpeg is installed in a custom folder.",
            "Encoder arguments\nCustom FFmpeg arguments; blank uses the chosen format's defaults. Example: use %FILENAME% for the output path. Reset encoder arguments restores defaults.",
            "Chroma color\nBackground RGB hex color; blank disables it. Example: 00FF00 selects green.",
            "Horizontal FOV\nHorizontal crop angle for spherical output, in degrees. Example: 360 covers a full horizontal turn.",
            "Vertical FOV\nVertical crop angle for spherical output, in degrees. Example: 180 covers the full vertical sphere."} : new String[]{
            "Video width\nOutput width in pixels. Example: 1920 for Full HD.",
            "Video height\nOutput height in pixels. Example: 1080 for Full HD.",
            "Frames per second\nNumber of exported frames per second. Example: 60 for smoother motion or 30 for a smaller render.",
            "Bitrate\nVideo bitrate in megabits per second for formats that use it. Example: higher values generally improve quality and increase file size.",
            "Output path\nFull destination path, or image-sequence folder when using PNG output. Example: use Browse to choose the folder.",
            "Output file name\nClick to select the name and type a replacement. Example: Takeoff. Leave off .mp4; the Format control chooses the extension for the rendered file. The parent folder stays unchanged."};
        return index >= 0 && index < help.length ? help[index] : null;
    }

    static int[] box(int x, int y, int width, int height, int canvasWidth, int canvasHeight) {
        int left = x + 12, top = y + 16;
        if (left + width > canvasWidth - 6) left = x - width - 12;
        if (top + height > canvasHeight - 6) top = y - height - 12;
        return new int[]{Math.max(6, Math.min(left, canvasWidth - width - 6)), Math.max(6, Math.min(top, canvasHeight - height - 6)), width, height};
    }
}
