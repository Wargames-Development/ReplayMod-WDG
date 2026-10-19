package com.replaymod.replay;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.utils.Restrictions;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.utils.WrappedTimer;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replay.camera.SpectatorCameraController;
import com.replaymod.replay.events.ReplayClosedCallback;
import com.replaymod.replay.events.ReplayClosingCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.util.Location;
import de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import de.johni0702.minecraft.gui.container.GuiContainer;
import de.johni0702.minecraft.gui.container.GuiScreen;
import de.johni0702.minecraft.gui.element.GuiLabel;
import de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import de.johni0702.minecraft.gui.layout.HorizontalLayout;
import de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerLoginClient;
import com.replaymod.core.versions.Window;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;

import java.io.IOException;
import java.util.*;

import com.replaymod.replay.mixin.EntityOtherPlayerMPAccessor;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import org.lwjgl.opengl.Display;

import io.netty.channel.ChannelOutboundHandlerAdapter;

import de.johni0702.minecraft.gui.element.GuiLabel;
import de.johni0702.minecraft.gui.popup.GuiInfoPopup;
import de.johni0702.minecraft.gui.utils.Colors;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import com.replaymod.replay.gui.screen.GuiOpeningReplay;
import net.minecraft.entity.EntityLivingBase;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.replaymod.core.versions.MCVer.*;
import static com.replaymod.replay.ReplayModReplay.LOGGER;
import static com.replaymod.core.versions.MCVer.GlStateManager.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;

public class ReplayHandler {

    private static Minecraft mc = getMinecraft();

    /**
     * The file currently being played.
     */
    private final ReplayFile replayFile;

    /**
     * Decodes and sends packets into channel.
     */
    private final FullReplaySender fullReplaySender;

    private static final String QUICK_MODE_MIN_MC = "1.9.4";

    /**
     * Currently active replay restrictions.
     */
    private Restrictions restrictions = new Restrictions();

    /**
     * Whether camera movements by user input and/or server packets should be suppressed.
     */
    private boolean suppressCameraMovements;

    private Set<Marker> markers;

    private final GuiReplayOverlay overlay;

    private EmbeddedChannel channel;

    private int replayDuration;

    /**
     * The position at which the camera should be located after the next jump.
     */
    private Location targetCameraPosition;

    private UUID spectating;

    public ReplayHandler(ReplayFile replayFile, boolean asyncMode) throws IOException {
        Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");
        this.replayFile = replayFile;

        replayDuration = replayFile.getMetaData().getDuration();

        markers = replayFile.getMarkers().or(Collections.emptySet());

        fullReplaySender = new FullReplaySender(this, replayFile, false);

        setup();

        overlay = new GuiReplayOverlay(this);
        overlay.setVisible(true);

        ReplayOpenedCallback.EVENT.invoker().replayOpened(this);

        fullReplaySender.setAsyncMode(asyncMode);
    }

    void restartedReplay() {
        Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");

        channel.close();

        mc.setIngameNotInFocus();

        // Force re-creation of camera entity by unloading the previous world
        // We need to re-set the GUI screen because having one with `allowsUserInput = true` active during world
        // load (i.e. before player is set) will crash MC...
        mc.displayGuiScreen(new net.minecraft.client.gui.GuiScreen() {});
        mc.loadWorld(null);

        restrictions = new Restrictions();

        setup();
    }

    public void endReplay() throws IOException {
        Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");

        ReplayClosingCallback.EVENT.invoker().replayClosing(this);

        fullReplaySender.terminateReplay();

        replayFile.save();
        replayFile.close();

        channel.close().awaitUninterruptibly();

        if (mc.thePlayer instanceof CameraEntity) {
            mc.thePlayer.setDead();
        }

        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }

        TimerAccessor timer = (TimerAccessor) ((MinecraftAccessor) mc).getTimer();
        timer.setTimerSpeed(1);
        overlay.setVisible(false);

        ReplayModReplay.instance.forcefullyStopReplay();

        mc.displayGuiScreen(null);

        ReplayClosedCallback.EVENT.invoker().replayClosed(this);
    }

    private void setup() {
        Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");

        mc.ingameGUI.getChatGUI().clearChatMessages();

        NetworkManager networkManager = new NetworkManager(true) {
            @Override
            public SocketAddress getRemoteAddress() {
                // See https://github.com/Dyonovan/TCNodeTracker/issues/37
                if (Loader.isModLoaded("tcnodetracker")) {
                    StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
                    if ("com.dyonovan.tcnodetracker.events.ClientConnectionEvent".equals(elem.getClassName())) {
                        LOGGER.debug("TCNodeTracker crash workaround applied");
                        return new InetSocketAddress("replaymod.dummy", 0);
                    }
                }
                return super.getRemoteAddress();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable t) {
                t.printStackTrace();
            }
        };
        mc.displayGuiScreen(new GuiOpeningReplay(networkManager));
        FMLClientHandler.instance().connectToRealmsServer(null, 0); // just to init the playClientBlock latch

        networkManager.setNetHandler(new NetHandlerLoginClient(
                networkManager,
                mc,
                null
        ));

        ChannelOutboundHandlerAdapter dummyHandler = new ChannelOutboundHandlerAdapter();
        channel = new EmbeddedChannel(dummyHandler);
        channel.pipeline().remove(dummyHandler);

        channel.pipeline().addLast("ReplayModReplay_replaySender", fullReplaySender);
        channel.pipeline().addLast("packet_handler", networkManager);
        channel.pipeline().fireChannelActive();
    }

    public ReplayFile getReplayFile() {
        return replayFile;
    }

    public Restrictions getRestrictions() {
        return restrictions;
    }

    public ReplaySender getReplaySender() {
        return fullReplaySender;
    }

    public GuiReplayOverlay getOverlay() {
        return overlay;
    }

    // leave code so quick mode (hopefully) can be implemented
    public void ensureQuickModeInitialized(@SuppressWarnings("unused") Runnable andThen) {
        GuiInfoPopup.open(overlay,
                new GuiLabel().setI18nText("replaymod.gui.noquickmode", QUICK_MODE_MIN_MC).setColor(Colors.BLACK));
    }

    public void setQuickMode(@SuppressWarnings("unused") boolean quickMode) {
        throw new UnsupportedOperationException("Quick Mode not supported on this version.");
    }

    public boolean isQuickMode() {
        return false;
    }

    public int getReplayDuration() {
        return replayDuration;
    }

    /**
     * Return whether camera movement by user inputs and/or server packets should be suppressed.
     * @return {@code true} if these kinds of movement should be suppressed
     */
    public boolean shouldSuppressCameraMovements() {
        return suppressCameraMovements;
    }

    /**
     * Set whether camera movement by user inputs and/or server packets should be suppressed.
     * @param suppressCameraMovements {@code true} to suppress these kinds of movement, {@code false} to allow them
     */
    public void setSuppressCameraMovements(boolean suppressCameraMovements) {
        this.suppressCameraMovements = suppressCameraMovements;
    }

    /**
     * Spectate the specified entity.
     * When the entity is {@code null} or the camera entity, the camera becomes the view entity.
     * @param e The entity to spectate
     */
    public void spectateEntity(EntityLivingBase e) {
        CameraEntity cameraEntity = getCameraEntity();
        if (cameraEntity == null) {
            return; // Cannot spectate if we have no camera
        }
        if (e instanceof EntityPlayer && e != cameraEntity && !(e instanceof EntityOtherPlayerMP)) {
            return; // Exclude local mod camera dummies, including MCHR's cockpit camera.
        }
        if (e == null || e == cameraEntity) {
            spectating = null;
            e = cameraEntity;
        } else if (e instanceof EntityPlayer) {
            spectating = e.getUniqueID();
        }

        if (e == cameraEntity) {
            cameraEntity.setCameraController(ReplayModReplay.instance.createCameraController(cameraEntity));
        } else {
            cameraEntity.setCameraController(new SpectatorCameraController(cameraEntity));
        }

        if (mc.renderViewEntity != e) {
            mc.renderViewEntity = e;
            cameraEntity.setCameraPosRot(e);
        }
    }

    /**
     * Set the camera as the view entity.
     * This is equivalent to {@code spectateEntity(null)}.
     */
    public void spectateCamera() {
        spectateEntity(null);
    }

    /**
     * Returns whether the current view entity is the camera entity.
     * @return {@code true} if the camera is the view entity, {@code false} otherwise
     */
    public boolean isCameraView() {
        return mc.thePlayer instanceof CameraEntity && mc.thePlayer == mc.renderViewEntity;
    }

    /**
     * Returns the camera entity.
     * @return The camera entity or {@code null} if it does not yet exist
     */
    public CameraEntity getCameraEntity() {
        return mc.thePlayer instanceof CameraEntity ? (CameraEntity) mc.thePlayer : null;
    }

    public UUID getSpectatedUUID() {
        return spectating;
    }

    public void moveCameraToTargetPosition() {
        CameraEntity cam = getCameraEntity();
        if (cam != null && targetCameraPosition != null) {
            cam.setCameraPosRot(targetCameraPosition);
        }
    }

    public void doJump(int targetTime, boolean retainCameraPosition) {
        FullReplaySender replaySender = fullReplaySender;

        if (replaySender.isHurrying()) {
            return; // When hurrying, no Timeline jumping etc. is possible
        }

        if (targetTime < replaySender.currentTimeStamp()) {
            mc.displayGuiScreen(null);
        }

        if (retainCameraPosition) {
            CameraEntity cam = getCameraEntity();
            if (cam != null) {
                targetCameraPosition = new Location(cam.posX, cam.posY, cam.posZ,
                        cam.rotationYaw, cam.rotationPitch);
            } else {
                targetCameraPosition = null;
            }
        }

        // Camera-path playback and rendering own the sender in synchronous mode.
        // Seeking from the overlay must not start an async worker behind their back.
        if (!replaySender.isAsyncMode()) {
            replaySender.sendPacketsTill(targetTime);
            moveCameraToTargetPosition();
            return;
        }

        long diff = targetTime - (replaySender.isHurrying() ? replaySender.getDesiredTimestamp() : replaySender.currentTimeStamp());
        if (diff != 0) {
            if (diff > 0 && diff < 5000) { // Small difference and no time travel
                replaySender.jumpToTime(targetTime);
            } else { // We either have to restart the replay or send a significant amount of packets
                // Render our please-wait-screen
                GuiScreen guiScreen = new GuiScreen();
                guiScreen.setBackground(AbstractGuiScreen.Background.DIRT);
                guiScreen.addElements(new HorizontalLayout.Data(0.5),
                        new GuiLabel().setI18nText("replaymod.gui.pleasewait"));

                // Make sure that the replaysender changes into sync mode
                replaySender.setSyncModeAndWait();

                // Perform the rendering using OpenGL
                pushMatrix();
                clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                enableTexture2D();
                mc.getFramebuffer().bindFramebuffer(true);
                Window window = new com.replaymod.core.versions.Window(mc);
                mc.entityRenderer.setupOverlayRendering();
                guiScreen.toMinecraft().setWorldAndResolution(mc, window.getScaledWidth(), window.getScaledHeight());
                guiScreen.toMinecraft().drawScreen(0, 0, 0);
                guiScreen.toMinecraft().onGuiClosed();

                mc.getFramebuffer().unbindFramebuffer();
                popMatrix();
                pushMatrix();
                mc.getFramebuffer().framebufferRender(new com.replaymod.core.versions.Window(mc).getFramebufferWidth(), new com.replaymod.core.versions.Window(mc).getFramebufferHeight());
                popMatrix();

                Display.update();

                // Send exactly to the requested timestamp. The old 1.7.10 loop kept extending
                // the target while the terrain screen/player state was not ready and could wedge seeks.
                replaySender.sendPacketsTill(targetTime);
                replaySender.setAsyncMode(true);
                replaySender.setReplaySpeed(0);

                while (mc.currentScreen instanceof GuiOpeningReplay) {
                    mc.currentScreen.handleInput();
                }

                mc.getNetHandler().getNetworkManager().processReceivedPackets();
                for (Entity entity : ((java.util.List<net.minecraft.entity.Entity>) mc.theWorld.loadedEntityList)) {
                    skipTeleportInterpolation(entity);
                    entity.lastTickPosX = entity.prevPosX = entity.posX;
                    entity.lastTickPosY = entity.prevPosY = entity.posY;
                    entity.lastTickPosZ = entity.prevPosZ = entity.posZ;
                    entity.prevRotationYaw = entity.rotationYaw;
                    entity.prevRotationPitch = entity.rotationPitch;
                }
                mc.runTick();

                //finally, updating the camera's position (which is not done by the sync jumping)
                moveCameraToTargetPosition();

                // No need to remove our please-wait-screen. It'll vanish with the next
                // render pass as it's never been a real GuiScreen in the first place.
            }
        }
    }

    private void skipTeleportInterpolation(Entity entity) {
        if (entity instanceof EntityOtherPlayerMP) {
            EntityOtherPlayerMP e = (EntityOtherPlayerMP) entity;
            EntityOtherPlayerMPAccessor ea = (EntityOtherPlayerMPAccessor) e;
            e.setPosition(ea.getOtherPlayerMPX(), ea.getOtherPlayerMPY(), ea.getOtherPlayerMPZ());
            e.rotationYaw = (float) ea.getOtherPlayerMPYaw();
            e.rotationPitch = (float) ea.getOtherPlayerMPPitch();
        }
    }
}
