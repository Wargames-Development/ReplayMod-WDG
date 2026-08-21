package com.replaymod.core.versions;

import com.replaymod.core.mixin.GuiScreenAccessor;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.us.myles.ViaVersion.api.protocol.ProtocolVersion;
import com.replaymod.replaystudio.us.myles.ViaVersion.packets.State;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Util;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.realms.RealmsSharedConstants;

import net.minecraft.client.resources.ResourcePackRepository;
import cpw.mods.fml.client.FMLClientHandler;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.Sys;
import java.awt.Desktop;
import java.io.IOException;

import com.replaymod.core.mixin.ResourcePackRepositoryAccessor;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.network.PacketBuffer;

import static org.lwjgl.opengl.GL11.*;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.util.Optional;

/**
 * Abstraction over things that have changed between different MC versions.
 */
public class MCVer {
    public static int getProtocolVersion() {
        return RealmsSharedConstants.NETWORK_PROTOCOL_VERSION;
    }

    public static PacketTypeRegistry getPacketTypeRegistry(boolean loginPhase) {
        return PacketTypeRegistry.get(
                ProtocolVersion.getProtocol(getProtocolVersion()),
                loginPhase ? State.LOGIN : State.PLAY
        );
    }

    public static void resizeMainWindow(Minecraft mc, int width, int height) {
        if (width != mc.displayWidth || height != mc.displayHeight) {
            mc.resize(width, height);
        }
    }

    public static String tryReadString(PacketBuffer buffer, int max) {
        try {
            return buffer.readStringFromBuffer(max);
        } catch (IOException e) {
            throw new DecoderException(e);
        }
    }

    public static ListenableFuture<?> setServerResourcePack(File file) {
        ResourcePackRepository repo = getMinecraft().getResourcePackRepository();
        ResourcePackRepositoryAccessor acc = (ResourcePackRepositoryAccessor) repo;
        acc.setActive(false);
        acc.setPack(new FileResourcePack(file));
        Minecraft.getMinecraft().scheduleResourcesRefresh();
        return Futures.immediateFuture(null);
    }

    public static <T> void addCallback(ListenableFuture<T> future, Consumer<T> success, Consumer<Throwable> failure) {
        Futures.addCallback(future, new FutureCallback<T>() {
            @Override
            public void onSuccess(T result) {
                success.accept(result);
            }

            @Override
            public void onFailure(Throwable throwable) {
                failure.accept(throwable);
            }
        });
    }

    public static RenderManager getRenderManager(@SuppressWarnings("unused") Minecraft mc) {
        return RenderManager.instance;
    }

    public static Minecraft getMinecraft() {
        return Minecraft.getMinecraft();
    }

    public static void addButton(GuiScreen screen, GuiButton button) {
        GuiScreenAccessor acc = (GuiScreenAccessor) screen;
        acc.getButtons().add(button);
    }

    public static Optional<GuiButton> findButton(List<GuiButton> buttonList, @SuppressWarnings("unused") String text, int id) {
        for (GuiButton b : buttonList) {
            if (b.id == id) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public interface MinecraftMethodAccessor {
        void replayModSetEarlyReturnFromRunTick(boolean earlyReturn);
    }

    public static long milliTime() {
        return Minecraft.getSystemTime();
    }

    public static void openFile(File file) {
        String path = file.getAbsolutePath();

        // First try OS specific methods
        try {
            switch (Util.getOSType()) {
                case WINDOWS:
                    Runtime.getRuntime().exec(String.format("cmd.exe /C start \"Open file\" \"%s\"", path));
                    return;
                case OSX:
                    Runtime.getRuntime().exec(new String[]{"/usr/bin/open", path});
                    return;
            }
        } catch (IOException e) {
            LogManager.getLogger().error("Cannot open file", e);
        }

        // Otherwise try to java way
        try {
            Desktop.getDesktop().browse(file.toURI());
        } catch (Throwable throwable) {
            // And if all fails, lwjgl
            Sys.openURL("file://" + path);
        }
    }

    public static void openURL(URI url) {
        try {
            Desktop.getDesktop().browse(url);
        } catch (Throwable e) {
            LogManager.getLogger().error("Failed to open URL: ", e);
        }
    }

    public static class SoundEvent {}

    public static boolean hasOptifine() {
        return FMLClientHandler.instance().hasOptifine();
    }

    public static class GlStateManager {
        public static void resetColor() { /* nop */ }
        public static void clearColor(float r, float g, float b, float a) { glClearColor(r, g, b, a); }
        public static void enableTexture2D() { glEnable(GL_TEXTURE_2D); }
        public static void enableAlpha() { glEnable(GL_ALPHA_TEST); }
        public static void alphaFunc(int func, float ref) { glAlphaFunc(func, ref); }
        public static void enableDepth() { glEnable(GL_DEPTH_TEST); }
        public static void pushMatrix() { glPushMatrix(); }
        public static void popAttrib() { glPopAttrib(); }
        public static void popMatrix() { glPopMatrix(); }
        public static void clear(int mask) { glClear(mask); }
        public static void translate(double x, double y, double z) { glTranslated(x, y, z); }
        public static void rotate(float angle, float x, float y, float z) { glRotatef(angle, x, y, z); }
    }

    public static abstract class Keyboard {
        public static final int KEY_LCONTROL = org.lwjgl.input.Keyboard.KEY_LCONTROL;
        public static final int KEY_LSHIFT = org.lwjgl.input.Keyboard.KEY_LSHIFT;
        public static final int KEY_ESCAPE = org.lwjgl.input.Keyboard.KEY_ESCAPE;
        public static final int KEY_HOME = org.lwjgl.input.Keyboard.KEY_HOME;
        public static final int KEY_END = org.lwjgl.input.Keyboard.KEY_END;
        public static final int KEY_UP = org.lwjgl.input.Keyboard.KEY_UP;
        public static final int KEY_DOWN = org.lwjgl.input.Keyboard.KEY_DOWN;
        public static final int KEY_LEFT = org.lwjgl.input.Keyboard.KEY_LEFT;
        public static final int KEY_RIGHT = org.lwjgl.input.Keyboard.KEY_RIGHT;
        public static final int KEY_BACK = org.lwjgl.input.Keyboard.KEY_BACK;
        public static final int KEY_DELETE = org.lwjgl.input.Keyboard.KEY_DELETE;
        public static final int KEY_RETURN = org.lwjgl.input.Keyboard.KEY_RETURN;
        public static final int KEY_TAB = org.lwjgl.input.Keyboard.KEY_TAB;
        public static final int KEY_F1 = org.lwjgl.input.Keyboard.KEY_F1;
        public static final int KEY_A = org.lwjgl.input.Keyboard.KEY_A;
        public static final int KEY_B = org.lwjgl.input.Keyboard.KEY_B;
        public static final int KEY_C = org.lwjgl.input.Keyboard.KEY_C;
        public static final int KEY_D = org.lwjgl.input.Keyboard.KEY_D;
        public static final int KEY_E = org.lwjgl.input.Keyboard.KEY_E;
        public static final int KEY_F = org.lwjgl.input.Keyboard.KEY_F;
        public static final int KEY_G = org.lwjgl.input.Keyboard.KEY_G;
        public static final int KEY_H = org.lwjgl.input.Keyboard.KEY_H;
        public static final int KEY_I = org.lwjgl.input.Keyboard.KEY_I;
        public static final int KEY_J = org.lwjgl.input.Keyboard.KEY_J;
        public static final int KEY_K = org.lwjgl.input.Keyboard.KEY_K;
        public static final int KEY_L = org.lwjgl.input.Keyboard.KEY_L;
        public static final int KEY_M = org.lwjgl.input.Keyboard.KEY_M;
        public static final int KEY_N = org.lwjgl.input.Keyboard.KEY_N;
        public static final int KEY_O = org.lwjgl.input.Keyboard.KEY_O;
        public static final int KEY_P = org.lwjgl.input.Keyboard.KEY_P;
        public static final int KEY_Q = org.lwjgl.input.Keyboard.KEY_Q;
        public static final int KEY_R = org.lwjgl.input.Keyboard.KEY_R;
        public static final int KEY_S = org.lwjgl.input.Keyboard.KEY_S;
        public static final int KEY_T = org.lwjgl.input.Keyboard.KEY_T;
        public static final int KEY_U = org.lwjgl.input.Keyboard.KEY_U;
        public static final int KEY_V = org.lwjgl.input.Keyboard.KEY_V;
        public static final int KEY_W = org.lwjgl.input.Keyboard.KEY_W;
        public static final int KEY_X = org.lwjgl.input.Keyboard.KEY_X;
        public static final int KEY_Y = org.lwjgl.input.Keyboard.KEY_Y;
        public static final int KEY_Z = org.lwjgl.input.Keyboard.KEY_Z;

        public static boolean hasControlDown() {
            return GuiScreen.isCtrlKeyDown();
        }

        public static boolean isKeyDown(int keyCode) {
            return org.lwjgl.input.Keyboard.isKeyDown(keyCode);
        }

        public static int getEventKey() {
            return org.lwjgl.input.Keyboard.getEventKey();
        }

        public static boolean getEventKeyState() {
            return org.lwjgl.input.Keyboard.getEventKeyState();
        }

        public static String getKeyName(int code) {
            return org.lwjgl.input.Keyboard.getKeyName(code);
        }
    }
}
