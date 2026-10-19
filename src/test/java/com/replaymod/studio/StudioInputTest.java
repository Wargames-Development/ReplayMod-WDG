package com.replaymod.studio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiButton;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class StudioInputTest {
    @Test public void buttonValidationRemainsVisibleAndDoesNotAlsoScrubTheTimeline() throws Exception {
        TestScreen screen = new TestScreen();
        Field allocatorField = Unsafe.class.getDeclaredField("theUnsafe"); allocatorField.setAccessible(true);
        Minecraft minecraft = (Minecraft) ((Unsafe) allocatorField.get(null)).allocateInstance(Minecraft.class);
        screen.mc = minecraft; minecraft.currentScreen = screen;
        Field viewport = StudioScreen.class.getDeclaredField("viewport"); viewport.setAccessible(true);
        viewport.set(screen, new StudioViewport(480, 270, 960, 540));
        screen.build();
        // The button's logical centre is 120,110, drawn at half scale.
        screen.mouseClicked(60, 55, 0);
        assertEquals("Choose a valid output filename", screen.message);
        assertEquals(0, screen.canvasClicks);
        // Minecraft sends held-button drag events after the click. A no-op drag
        // or deferred seek must not erase the error before the user can read it.
        screen.mouseClickMove(60, 55, 0, 20);
        screen.attempt(() -> { });
        assertEquals("Choose a valid output filename", screen.message);
        screen.mouseClicked(200, 200, 0);
        assertEquals(1, screen.canvasClicks);
    }
    @Test public void nativeControlsRemainClickableOnTheTallerTimingEditorCanvas() throws Exception {
        TestScreen screen = new TestScreen();
        Field allocatorField = Unsafe.class.getDeclaredField("theUnsafe"); allocatorField.setAccessible(true);
        Minecraft minecraft = (Minecraft) ((Unsafe) allocatorField.get(null)).allocateInstance(Minecraft.class);
        screen.mc = minecraft; minecraft.currentScreen = screen;
        StudioViewport mapping = new StudioViewport(960, 540, 960, 580);
        Field viewport = StudioScreen.class.getDeclaredField("viewport"); viewport.setAccessible(true); viewport.set(screen, mapping);
        screen.build();
        screen.mouseClicked(mapping.x + Math.round(150 * mapping.scale), mapping.y + Math.round(110 * mapping.scale), 0);
        assertEquals("Choose a valid output filename", screen.message);
        assertEquals(0, screen.canvasClicks);
    }
    @Test public void rightAnchoredControlsUseTheFullWindowMouseCoordinates() throws Exception {
        for (int[] size : new int[][]{{1920, 1080}, {3440, 1440}, {800, 600}}) {
            TestScreen screen = new TestScreen();
            Field allocatorField = Unsafe.class.getDeclaredField("theUnsafe"); allocatorField.setAccessible(true);
            Minecraft minecraft = (Minecraft) ((Unsafe) allocatorField.get(null)).allocateInstance(Minecraft.class);
            screen.mc = minecraft; minecraft.currentScreen = screen;
            StudioViewport mapping = new StudioViewport(size[0], size[1], 960, 580, true);
            StudioEditorLayout layout = new StudioEditorLayout(mapping.canvasWidth, mapping.canvasHeight);
            Field viewport = StudioScreen.class.getDeclaredField("viewport"); viewport.setAccessible(true); viewport.set(screen, mapping);
            screen.buildRightAnchored(layout);
            screen.mouseClicked(Math.round(layout.right(872) * mapping.scale), Math.round(22 * mapping.scale), 0);
            assertEquals("Choose a valid output filename", screen.message);
            assertEquals(0, screen.canvasClicks);
        }
    }
    private static final class TestScreen extends StudioScreen {
        int canvasClicks;
        @Override protected void build() {
            buttonList.add(new GuiButton(1, 100, 100, 100, 20, "Export") {
                @Override public void playPressSound(SoundHandler unused) { }
            });
        }
        void buildRightAnchored(StudioEditorLayout layout) {
            buttonList.add(new GuiButton(1, layout.right(800), 12, 144, 20, "Export") {
                @Override public void playPressSound(SoundHandler unused) { }
            });
        }
        @Override protected void drawContent(int x, int y, float partialTicks) { }
        @Override protected void activate(int id) { throw new IllegalArgumentException("Choose a valid output filename"); }
        @Override protected void clicked(int x, int y, int button) { canvasClicks++; }
    }
}
