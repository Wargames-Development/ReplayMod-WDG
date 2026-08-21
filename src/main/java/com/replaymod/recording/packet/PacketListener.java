package com.replaymod.recording.packet;

import com.github.steveice10.netty.buffer.PooledByteBufAllocator;
import com.github.steveice10.packetlib.tcp.io.ByteBufNetOutput;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Restrictions;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.editor.gui.MarkerProcessor;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.recording.Setting;
import com.replaymod.recording.gui.GuiSavingReplay;
import com.replaymod.recording.handler.ConnectionEventHandler;
import com.replaymod.recording.mixin.SPacketSpawnMobAccessor;
import com.replaymod.recording.mixin.SPacketSpawnPlayerAccessor;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.io.ReplayOutputStream;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.us.myles.ViaVersion.api.Pair;
import de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.play.server.S0DPacketCollectItem;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.entity.Entity;
import net.minecraft.entity.DataWatcher;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.crash.CrashReport;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;

import java.util.List;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.replaymod.core.versions.MCVer.*;
import static com.replaymod.replaystudio.util.Utils.writeInt;

public class PacketListener extends ChannelInboundHandlerAdapter {

    private static final Minecraft mc = getMinecraft();
    private static final Logger logger = LogManager.getLogger();

    private final ReplayMod core;
    private final Path outputPath;
    private final ReplayFile replayFile;

    private final ResourcePackRecorder resourcePackRecorder;

    private final ExecutorService saveService = Executors.newSingleThreadExecutor();
    private final ReplayOutputStream packetOutputStream;

    private ReplayMetaData metaData;

    private ChannelHandlerContext context = null;

    private final long startTime;
    private long lastSentPacket;
    private long timePassedWhilePaused;
    /**
     * Connection/FML negotiation and the short JoinGame-to-first-chunk window happen before
     * there is useful world geometry to render. Those packets are still required for playback,
     * but preserving their wall time creates an empty/void replay prefix. Keep startup packets
     * at t=0 and only start advancing replay time once the first world chunk arrives after
     * JoinGame. Packet ordering is preserved even when several startup packets share t=0.
     */
    private long initialConnectionDelay = -1L;
    private boolean joinGameSeen;
    private volatile boolean serverWasPaused;
    private EnumConnectionState connectionState = EnumConnectionState.PLAY;
    private boolean loginPhase = false;

    /**
     * Used to keep track of the last metadata save job submitted to the save service and
     * as such prevents unnecessary writes.
     */
    private final AtomicInteger lastSaveMetaDataId = new AtomicInteger();

    public PacketListener(ReplayMod core, Path outputPath, ReplayFile replayFile, ReplayMetaData metaData) throws IOException {
        this.core = core;
        this.outputPath = outputPath;
        this.replayFile = replayFile;
        this.metaData = metaData;
        this.resourcePackRecorder = new ResourcePackRecorder(replayFile);
        this.packetOutputStream = replayFile.writePacketData();
        this.startTime = metaData.getDate();

        saveMetaData();
    }

    private void saveMetaData() {
        int id = lastSaveMetaDataId.incrementAndGet();
        saveService.submit(() -> {
            if (lastSaveMetaDataId.get() != id) {
                return; // Another job has been scheduled, it will do the hard work.
            }
            try {
                synchronized (replayFile) {
                    if (ReplayMod.isMinimalMode()) {
                        metaData.setFileFormat("MCPR");
                        metaData.setFileFormatVersion(ReplayMetaData.CURRENT_FILE_FORMAT_VERSION);
                        metaData.setProtocolVersion(MCVer.getProtocolVersion());
                        metaData.setGenerator("ReplayMod in Minimal Mode");

                        try (OutputStream out = replayFile.write("metaData.json")) {
                            String json = (new Gson()).toJson(metaData);
                            out.write(json.getBytes());
                        }
                    } else {
                        replayFile.writeMetaData(MCVer.getPacketTypeRegistry(true), metaData);
                    }
                }
            } catch (IOException e) {
                logger.error("Writing metadata:", e);
            }
        });
    }

    public void save(Packet packet) {
        // If we're not on the main thread (i.e. we're on the netty thread), then we need to schedule the saving
        // to happen on the main thread so we can guarantee correct ordering of inbound and inject packets.
        // Otherwise, injected packets may end up further down the packet stream than they were supposed to and other
        // inbound packets which may rely on the injected packet would behave incorrectly when played back.
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(() -> save(packet));
            return;
        }
        try {
            if(packet instanceof S0CPacketSpawnPlayer) {
                UUID uuid = ((S0CPacketSpawnPlayer) packet).func_148948_e().getId();
                Set<String> uuids = new HashSet<>(Arrays.asList(metaData.getPlayers()));
                uuids.add(uuid.toString());
                metaData.setPlayers(uuids.toArray(new String[uuids.size()]));
                saveMetaData();
            }

            long now = System.currentTimeMillis();
            long elapsedSinceConnection = now - startTime;

            // ReplayMod starts listening before Forge/FML has finished connecting, and 1.7.10
            // can still spend a short moment after JoinGame before the first terrain packet is
            // available. Preserve all required startup packets but collapse both delays to t=0.
            // The first S21/S26 after JoinGame is the earliest point at which playback can render
            // an actual world instead of the void.
            if (packet instanceof S01PacketJoinGame) {
                joinGameSeen = true;
            }
            if (initialConnectionDelay < 0L && joinGameSeen
                    && (packet instanceof S21PacketChunkData || packet instanceof S26PacketMapChunkBulk)) {
                initialConnectionDelay = elapsedSinceConnection;
            }

            long elapsedSinceReplayStart = initialConnectionDelay < 0L
                    ? 0L
                    : Math.max(0L, elapsedSinceConnection - initialConnectionDelay);

            if (serverWasPaused && initialConnectionDelay >= 0L) {
                timePassedWhilePaused = elapsedSinceReplayStart - lastSentPacket;
                serverWasPaused = false;
            }
            int timestamp = (int) Math.max(0L, elapsedSinceReplayStart - timePassedWhilePaused);
            lastSentPacket = timestamp;
            PacketData packetData = getPacketData(timestamp, packet);
            saveService.submit(() -> {
                try {
                    if (ReplayMod.isMinimalMode()) {
                        // Minimal mode, ReplayStudio might not know our packet ids, so we cannot use it
                        com.github.steveice10.netty.buffer.ByteBuf packetIdBuf = PooledByteBufAllocator.DEFAULT.buffer();
                        com.github.steveice10.netty.buffer.ByteBuf packetBuf = packetData.getPacket().getBuf();
                        try {
                            new ByteBufNetOutput(packetIdBuf).writeVarInt(packetData.getPacket().getId());

                            int packetIdLen = packetIdBuf.readableBytes();
                            int packetBufLen = packetBuf.readableBytes();
                            writeInt(packetOutputStream, (int) packetData.getTime());
                            writeInt(packetOutputStream, packetIdLen + packetBufLen);
                            packetIdBuf.readBytes(packetOutputStream, packetIdLen);
                            packetBuf.getBytes(packetBuf.readerIndex(), packetOutputStream, packetBufLen);
                        } finally {
                            packetIdBuf.release();
                            packetBuf.release();
                        }
                    } else {
                        packetOutputStream.write(packetData);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch(Exception e) {
            logger.error("Writing packet:", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        metaData.setDuration((int) lastSentPacket);
        saveMetaData();

        core.runLater(() -> {
            ConnectionEventHandler connectionEventHandler = ReplayModRecording.instance.getConnectionEventHandler();
            if (connectionEventHandler.getPacketListener() == this) {
                connectionEventHandler.reset();
            }
        });

        GuiSavingReplay guiSavingReplay = new GuiSavingReplay(core);
        new Thread(() -> {
            core.runLater(guiSavingReplay::open);

            saveService.shutdown();
            try {
                saveService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Waiting for save service termination:", e);
            }
            try {
                packetOutputStream.close();
            } catch (IOException e) {
                logger.error("Failed to close packet output stream:", e);
            }

            List<Pair<Path, ReplayMetaData>> outputPaths;
            synchronized (replayFile) {
                try {
                    if (!MarkerProcessor.producesAnyOutput(replayFile)) {
                        // Immediately close the saving popup, the user doesn't care about it
                        core.runLater(guiSavingReplay::close);

                        // If we crash right here, on the next start we'll prompt the user for recovery
                        // but we don't really want that, so drop a marker file to skip recovery for this replay.
                        Files.createFile(outputPath.resolveSibling(outputPath.getFileName() + ".no_recover"));

                        // We still have the replay, so we just save it (at least for a few weeks) in case they change their mind
                        String replayName = FilenameUtils.getBaseName(outputPath.getFileName().toString());
                        Path rawFolder = ReplayMod.instance.getRawReplayFolder();
                        Path rawPath = rawFolder.resolve(outputPath.getFileName());
                        for (int i = 1; Files.exists(rawPath); i++) {
                            rawPath = rawPath.resolveSibling(replayName + "." + i + ".mcpr");
                        }
                        Files.createDirectories(rawPath.getParent());
                        replayFile.saveTo(rawPath.toFile());
                        replayFile.close();
                        return;
                    }

                    replayFile.save();
                    replayFile.close();

                    if (core.getSettingsRegistry().get(Setting.AUTO_POST_PROCESS) && !ReplayMod.isMinimalMode()) {
                        outputPaths = MarkerProcessor.apply(outputPath, guiSavingReplay.getProgressBar()::setProgress);
                    } else {
                        outputPaths = Collections.singletonList(new Pair<>(outputPath, metaData));
                    }
                } catch (Exception e) {
                    logger.error("Saving replay file:", e);
                    CrashReport crashReport = CrashReport.makeCrashReport(e, "Saving replay file");
                    core.runLater(() -> Utils.error(logger, VanillaGuiScreen.wrap(mc.currentScreen), crashReport, guiSavingReplay::close));
                    return;
                }
            }

            core.runLater(() -> guiSavingReplay.presentRenameDialog(outputPaths));
        }).start();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(ctx == null) {
            if(context == null) {
                return;
            } else {
                ctx = context;
            }
        }
        this.context = ctx;

        if (msg instanceof Packet) {
            try {
                Packet packet = (Packet) msg;
                if(packet instanceof S0DPacketCollectItem) {
                    if(mc.thePlayer != null && ((S0DPacketCollectItem) packet).func_149353_d() == mc.thePlayer.getEntityId()) {
                        super.channelRead(ctx, msg);
                        return;
                    }
                }

                if (packet instanceof S3FPacketCustomPayload) {
                    S3FPacketCustomPayload p = (S3FPacketCustomPayload) packet;
                    if ("MC|RPack".equals(p.func_149169_c())) {
                        save(resourcePackRecorder.handleResourcePack(p));
                        return;
                    }
                }

                if (packet instanceof FMLProxyPacket) {
                    // This packet requires special handling
                    save(((FMLProxyPacket) packet).toS3FPacket());
                    super.channelRead(ctx, msg);
                    return;
                }

                save(packet);

                if (packet instanceof S3FPacketCustomPayload) {
                    S3FPacketCustomPayload p = (S3FPacketCustomPayload) packet;
                    if (Restrictions.PLUGIN_CHANNEL.equals(p.func_149169_c())) {
                        packet = new S40PacketDisconnect(new ChatComponentText("Please update to view this replay."));
                        save(packet);
                    }
                }
            } catch(Exception e) {
                logger.error("Handling packet for recording:", e);
            }

        }

        super.channelRead(ctx, msg);
    }

    @SuppressWarnings("unchecked")
    private PacketData getPacketData(int timestamp, Packet packet) throws Exception {
        if (packet instanceof S0FPacketSpawnMob) {
            S0FPacketSpawnMob p = (S0FPacketSpawnMob) packet;
            SPacketSpawnMobAccessor pa = (SPacketSpawnMobAccessor) p;
            if (pa.getDataManager() == null) {
                pa.setDataManager(new DataWatcher(null));
                if (p.func_149027_c() != null) {
                    Set<Integer> seen = new HashSet<>();
                    for(DataWatcher.WatchableObject wo : Lists.reverse((List<DataWatcher.WatchableObject>) p.func_149027_c())) {
                        if (!seen.add(wo.getDataValueId())) continue;
                        pa.getDataManager().addObject(wo.getDataValueId(), wo.getObject());
                    }
                }
            }
        }

        if (packet instanceof S0CPacketSpawnPlayer) {
            S0CPacketSpawnPlayer p = (S0CPacketSpawnPlayer) packet;
            SPacketSpawnPlayerAccessor pa = (SPacketSpawnPlayerAccessor) p;
            if (pa.getDataManager() == null) {
                pa.setDataManager(new DataWatcher(null));
                if (p.func_148944_c() != null) {
                    Set<Integer> seen = new HashSet<>();
                    for(DataWatcher.WatchableObject wo : Lists.reverse((List<DataWatcher.WatchableObject>) p.func_148944_c())) {
                        if (!seen.add(wo.getDataValueId())) continue;
                        pa.getDataManager().addObject(wo.getDataValueId(), wo.getObject());
                    }
                }
            }
        }

        Integer packetId = (Integer) connectionState.func_150755_b().inverse().get(packet.getClass());
        if (packetId == null) {
            throw new IOException("Unknown packet type:" + packet.getClass());
        }
        ByteBuf byteBuf = Unpooled.buffer();
        try {
            packet.writePacketData(new PacketBuffer(byteBuf));

            // Copy the readable region rather than relying on the backing-array offset. This also gives us a
            // stable byte sequence before the original Netty buffer is released below.
            byte[] serialized = new byte[byteBuf.readableBytes()];
            byteBuf.getBytes(byteBuf.readerIndex(), serialized);

            if (packet instanceof S21PacketChunkData) {
                serialized = normalize1710ChunkDataPacket(serialized);
            }

            return new PacketData(timestamp, new com.replaymod.replaystudio.protocol.Packet(
                    MCVer.getPacketTypeRegistry(loginPhase),
                    packetId,
                    com.github.steveice10.netty.buffer.Unpooled.wrappedBuffer(serialized)
            ));
        } finally {
            byteBuf.release();
        }
    }

    /**
     * Normalizes the 1.7.10 S21 chunk-data payload produced by local/integrated-server packet objects.
     *
     * The normal 1.7.10 wire layout stores the compressed-data length as a four-byte big-endian int after
     * the 13-byte chunk header. Certain already-used local packet objects can instead re-serialize that same
     * length as a VarInt. Minecraft's S21 reader still expects the four-byte form, so recording those bytes
     * verbatim creates a replay which later interprets the first three zlib bytes as part of an enormous length.
     *
     * We only rewrite when the normal int is inconsistent AND a VarInt at the same position exactly accounts
     * for every remaining payload byte. Anything else is rejected rather than knowingly writing a corrupt replay.
     */
    private byte[] normalize1710ChunkDataPacket(byte[] data) throws IOException {
        final int headerLength = 13;
        final int intLength = 4;
        if (data.length < headerLength + intLength) {
            throw new IOException("Truncated 1.7.10 S21 chunk packet: " + data.length + " bytes");
        }

        int declaredLength = ((data[headerLength] & 0xff) << 24)
                | ((data[headerLength + 1] & 0xff) << 16)
                | ((data[headerLength + 2] & 0xff) << 8)
                | (data[headerLength + 3] & 0xff);
        int normalPayloadLength = data.length - headerLength - intLength;
        if (declaredLength == normalPayloadLength) {
            return data;
        }

        int value = 0;
        int shift = 0;
        int varIntLength = 0;
        while (varIntLength < 5 && headerLength + varIntLength < data.length) {
            int current = data[headerLength + varIntLength] & 0xff;
            value |= (current & 0x7f) << shift;
            varIntLength++;
            if ((current & 0x80) == 0) {
                break;
            }
            shift += 7;
        }

        if (varIntLength <= 0 || varIntLength >= intLength
                || value != data.length - headerLength - varIntLength) {
            throw new IOException("Invalid 1.7.10 S21 chunk packet length: declared=" + declaredLength
                    + ", payload=" + normalPayloadLength + ", bytes=" + data.length);
        }

        byte[] normalized = new byte[data.length + (intLength - varIntLength)];
        System.arraycopy(data, 0, normalized, 0, headerLength);
        normalized[headerLength] = (byte) (value >>> 24);
        normalized[headerLength + 1] = (byte) (value >>> 16);
        normalized[headerLength + 2] = (byte) (value >>> 8);
        normalized[headerLength + 3] = (byte) value;
        System.arraycopy(data, headerLength + varIntLength, normalized, headerLength + intLength, value);
        return normalized;
    }

    public void addMarker(String name) {
        addMarker(name, (int) getCurrentDuration());
    }

    public void addMarker(String name, int timestamp) {
        Entity view = mc.renderViewEntity;

        Marker marker = new Marker();
        marker.setName(name);
        marker.setTime(timestamp);
        if (view != null) {
            marker.setX(view.posX);
            marker.setY(view.posY);
            marker.setZ(view.posZ);
            marker.setYaw(view.rotationYaw);
            marker.setPitch(view.rotationPitch);
        }
        // Roll is always 0
        saveService.submit(() -> {
            synchronized (replayFile) {
                try {
                    Set<Marker> markers = replayFile.getMarkers().or(HashSet::new);
                    markers.add(marker);
                    replayFile.writeMarkers(markers);
                } catch (IOException e) {
                    logger.error("Writing markers:", e);
                }
            }
        });
    }

    public long getCurrentDuration() {
        return lastSentPacket;
    }

    public void setServerWasPaused() {
        this.serverWasPaused = true;
    }
}
