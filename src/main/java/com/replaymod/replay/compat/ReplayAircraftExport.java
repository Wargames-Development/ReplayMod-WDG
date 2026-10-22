package com.replaymod.replay.compat;

import com.replaymod.replay.ReplayHandler;
import cpw.mods.fml.common.Loader;
import java.io.*;
import java.util.UUID;

/** Recorded presentation shared by replay viewing and export; never changes entity or flight state. */
public final class ReplayAircraftExport {
    public interface PoseHook {
        double[] replayMod$bodyPose();
        default int replayMod$packedLight() { return -1; }
    }
    private static RecordedAircraftFrames frames;
    private static RecordedAircraftFrames playbackFrames;
    private static ReplayHandler playbackOwner;
    private ReplayAircraftExport() { }
    public static void open(ReplayHandler replay) {
        close();
        if (!Loader.isModLoaded("mcheli")) return;
        try {
            verifyHook();
            playbackFrames=read(replay);
            playbackOwner=replay;
            if (playbackFrames!=null) com.replaymod.replay.ReplayModReplay.LOGGER.info(
                "MCHR replay pose hook active; indexed {} recorded aircraft tracks",playbackFrames.trackCount());
        } catch (IOException error) {
            com.replaymod.replay.ReplayModReplay.LOGGER.warn("Could not index MCHR replay poses; retaining native playback",error);
        }
    }
    public static void close() { frames=null; playbackFrames=null; playbackOwner=null; }
    public static void begin(ReplayHandler replay) throws IOException {
        frames=null;
        if (!Loader.isModLoaded("mcheli")) return;
        verifyHook();
        frames=playbackOwner==replay && playbackFrames!=null ? playbackFrames : read(replay);
        if (frames!=null) com.replaymod.replay.ReplayModReplay.LOGGER.info(
            "MCHR export hook active; indexed {} recorded aircraft tracks",frames.trackCount());
    }
    private static void verifyHook() throws IOException {
        try {
            Class<?> pose=Class.forName("mcheli.render.MCH_RenderFarVehicle$SmoothedPose", false, ReplayAircraftExport.class.getClassLoader());
            if (!PoseHook.class.isAssignableFrom(pose)) throw new IOException("MCHR aircraft render hook is missing. Enable UniMixins and restart Minecraft before exporting.");
        } catch (ClassNotFoundException missing) { throw new IOException("This MCHR version does not provide the supported aircraft renderer.",missing); }
    }
    private static RecordedAircraftFrames read(ReplayHandler replay) throws IOException {
        if (!com.replaymod.replaystudio.us.myles.ViaVersion.api.protocol.ProtocolVersion.getProtocol(5)
                .equals(replay.getReplayFile().getMetaData().getProtocolVersion())) return null;
        com.google.common.base.Optional<InputStream> stream=replay.getReplayFile().get("recording.tmcpr");
        if (stream.isPresent()) try (InputStream input=stream.get()) {
            return RecordedAircraftFrames.read(input);
        }
        return null;
    }
    public static void end() { frames=null; }
    public static RecordedAircraftFrames.Pose pose(UUID uuid, String type, String name) {
        if (!ReplayVehicleClock.active()) return null;
        RecordedAircraftFrames source=frames!=null ? frames
            : com.replaymod.replay.ReplayModReplay.instance.getReplayHandler()==playbackOwner ? playbackFrames : null;
        return source!=null ? source.at(uuid,type,name,(int)ReplayVehicleClock.millis()) : null;
    }
}
