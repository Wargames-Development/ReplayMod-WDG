package com.replaymod.replay.handler;

import com.replaymod.core.gui.GuiReplayButton;
import com.replaymod.replay.Setting;
import de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import de.johni0702.minecraft.gui.element.GuiTooltip;
import de.johni0702.minecraft.gui.layout.CustomLayout;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import de.johni0702.minecraft.gui.utils.lwjgl.Point;
import de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.gui.screen.GuiReplayViewer;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.replaymod.core.versions.MCVer.*;
import static com.replaymod.replay.ReplayModReplay.LOGGER;

public class GuiHandler extends EventRegistrations {
    private static final int BUTTON_REPLAY_VIEWER = 17890234;
    private static final int BUTTON_EXIT_REPLAY = 17890235;

    private final ReplayModReplay mod;

    public GuiHandler(ReplayModReplay mod) {
        this.mod = mod;
    }

    { on(InitScreenCallback.EVENT, this::injectIntoIngameMenu); }
    private void injectIntoIngameMenu(GuiScreen guiScreen, List<GuiButton> buttonList) {
        if (!(guiScreen instanceof GuiIngameMenu)) {
            return;
        }

        if (mod.getReplayHandler() != null) {
            // Pause replay when menu is opened
            mod.getReplayHandler().getReplaySender().setReplaySpeed(0);

            final int BUTTON_EXIT_SERVER = 1;
            final int BUTTON_ADVANCEMENTS = 5;
            final int BUTTON_STATS = 6;
            final int BUTTON_OPEN_TO_LAN = 7;


            GuiButton openToLan = null;
            GuiButton achievements = null, stats = null;
            for(GuiButton b : new ArrayList<>(buttonList)) {
                boolean remove = false;
                Integer id = b.id;
                if (id.equals(BUTTON_EXIT_SERVER)) {
                    // Replace "Exit Server" button with "Exit Replay" button
                    remove = true;
                    addButton(guiScreen, new InjectedButton(
                            guiScreen,
                            BUTTON_EXIT_REPLAY,
                            b.xPosition,
                            b.yPosition,
                            b.width,
                            b.height,
                            "replaymod.gui.exit",
                            this::onButton
                    ));
                } else if (id.equals(BUTTON_ADVANCEMENTS)) {
                    // Remove "Advancements", "Stats" and "Open to LAN" buttons
                    remove = true;
                    achievements = b;
                } else if (id.equals(BUTTON_STATS)) {
                    remove = true;
                    stats = b;
                } else if (id.equals(BUTTON_OPEN_TO_LAN)) {
                    remove = true;
                    openToLan = b;
                }
                if (remove) {
                    // Moving the button far off-screen is easier to do cross-version than actually removing it
                    b.xPosition = -1000;
                    b.yPosition = -1000;
                }
            }
            if (achievements != null && stats != null) {
                moveAllButtonsInRect(buttonList,
                        achievements.xPosition, stats.xPosition + stats.width,
                        achievements.yPosition, Integer.MAX_VALUE,
                        -24);
            }
            // In 1.13+ Forge, the Options button shares one row with the Open to LAN button
            if (openToLan != null) {
                moveAllButtonsInRect(buttonList,
                        openToLan.xPosition, openToLan.xPosition + openToLan.width,
                        openToLan.yPosition, Integer.MAX_VALUE,
                        -24);
            }
        }
    }

    /**
     * Moves all buttons that in any way intersect a rectangle by a given amount on the y axis.
     * @param buttons List of buttons
     * @param yStart Top y limit of the rectangle
     * @param yEnd Bottom y limit of the rectangle
     * @param xStart Left x limit of the rectangle
     * @param xEnd Right x limit of the rectangle
     * @param moveBy Signed distance to move the buttons
     */
    private void moveAllButtonsInRect(
            List<GuiButton> buttons,
            int xStart,
            int xEnd,
            int yStart,
            int yEnd,
            int moveBy
    ) {
        buttons.stream()
                .filter(button -> button.xPosition <= xEnd && button.xPosition + button.width >= xStart)
                .filter(button -> button.yPosition <= yEnd && button.yPosition + button.height >= yStart)
                .forEach(button -> button.yPosition += moveBy);
    }

    { on(InitScreenCallback.EVENT, this::ensureReplayStopped); }
    private void ensureReplayStopped(GuiScreen guiScreen, List<GuiButton> buttonList) {
        if (!(guiScreen instanceof GuiMainMenu || guiScreen instanceof GuiMultiplayer)) {
            return;
        }

        if (mod.getReplayHandler() != null) {
            // Something went terribly wrong and we ended up in the main menu with the replay still active.
            // To prevent players from joining live servers and using the CameraEntity, try to stop the replay now.
            try {
                mod.getReplayHandler().endReplay();
            } catch (IOException e) {
                LOGGER.error("Trying to stop broken replay: ", e);
            } finally {
                if (mod.getReplayHandler() != null) {
                    mod.forcefullyStopReplay();
                }
            }
        }
    }

    { on(InitScreenCallback.EVENT, this::injectIntoMainMenu); }
    private void injectIntoMainMenu(GuiScreen guiScreen, List<GuiButton> buttonList) {
        if (!(guiScreen instanceof GuiMainMenu)) {
            return;
        }

        boolean isCustomMainMenuMod = guiScreen.getClass().getName().endsWith("custommainmenu.gui.GuiFakeMain");

        MainMenuButtonPosition buttonPosition = MainMenuButtonPosition.valueOf(mod.getCore().getSettingsRegistry().get(Setting.MAIN_MENU_BUTTON));
        if (buttonPosition != MainMenuButtonPosition.BIG && !isCustomMainMenuMod) {
            VanillaGuiScreen vanillaGui = VanillaGuiScreen.wrap(guiScreen);

            GuiReplayButton replayButton = new GuiReplayButton();
            replayButton
                    .onClick(() -> new GuiReplayViewer(mod).display())
                    .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.replayviewer"));

            vanillaGui.setLayout(new CustomLayout<de.johni0702.minecraft.gui.container.GuiScreen>(vanillaGui.getLayout()) {
                private Point pos;

                @Override
                protected void layout(de.johni0702.minecraft.gui.container.GuiScreen container, int width, int height) {
                    if (pos == null) {
                        // Delaying computation so we can take into account buttons
                        // added after our callback.
                        pos = determineButtonPos(buttonPosition, guiScreen, buttonList);
                    }
                    size(replayButton, 20, 20);
                    pos(replayButton, pos.getX(), pos.getY());
                }
            }).addElements(null, replayButton);
            return;
        }

        int x = guiScreen.width / 2 - 100;
        // We want to position our button below the realms button
        int y = findButton(buttonList, "menu.online", 14)
                .map(Optional::of)
                // or, if someone removed the realms button, we'll alternatively take the multiplayer one
                .orElse(findButton(buttonList, "menu.multiplayer", 2))
                // if we found some button, put our button at its position (we'll move it out of the way shortly)
                .map(it -> it.yPosition)
                // and if we can't even find that one, then just guess
                .orElse(guiScreen.height / 4 + 10 + 4 * 24);

        // Move all buttons above or at our one upwards
        moveAllButtonsInRect(buttonList,
                x, x + 200,
                Integer.MIN_VALUE, y,
                -24);

        // Add our button
        InjectedButton button = new InjectedButton(
                guiScreen,
                BUTTON_REPLAY_VIEWER,
                x,
                y,
                200,
                20,
                "replaymod.gui.replayviewer",
                this::onButton
        );
        if (isCustomMainMenuMod) {
            // CustomMainMenu uses a different list in the event than in its Fake gui
            buttonList.add(button);
            return;
        }
        addButton(guiScreen, button);
    }

    private Point determineButtonPos(MainMenuButtonPosition buttonPosition, GuiScreen guiScreen, List<GuiButton> buttonList) {
        Point topRight = new Point(guiScreen.width - 20 - 5, 5);

        if (buttonPosition == MainMenuButtonPosition.TOP_LEFT) {
            return new Point(5, 5);
        } else if (buttonPosition == MainMenuButtonPosition.TOP_RIGHT) {
            return topRight;
        } else if (buttonPosition == MainMenuButtonPosition.DEFAULT) {
            return Stream.of(
                    findButton(buttonList, "menu.singleplayer", 1),
                    findButton(buttonList, "menu.multiplayer", 2),
                    findButton(buttonList, "menu.online", 14),
                    findButton(buttonList, "modmenu.title", 6)
            )
                    // skip buttons which do not exist
                    .flatMap(it -> it.map(Stream::of).orElseGet(Stream::empty))
                    // skip buttons which already have something next to them
                    .filter(it -> buttonList.stream().noneMatch(button ->
                            button.xPosition <= it.xPosition + it.width + 4 + 20
                                    && button.yPosition <= it.yPosition + it.height
                                    && button.xPosition + button.width >= it.xPosition + it.width + 4
                                    && button.yPosition + button.height >= it.yPosition
                    ))
                    // then take the bottom-most and if there's two, the right-most
                    .max(Comparator.<GuiButton>comparingInt(it -> it.yPosition).thenComparingInt(it -> it.xPosition))
                    // and place ourselves next to it
                    .map(it -> new Point(it.xPosition + it.width + 4, it.yPosition))
                    // if all fails, just go with TOP_RIGHT
                    .orElse(topRight);
        } else {
            return Optional.of(buttonList).flatMap(buttons -> {
                switch (buttonPosition) {
                    case LEFT_OF_SINGLEPLAYER:
                    case RIGHT_OF_SINGLEPLAYER:
                        return findButton(buttons, "menu.singleplayer", 1);
                    case LEFT_OF_MULTIPLAYER:
                    case RIGHT_OF_MULTIPLAYER:
                        return findButton(buttons, "menu.multiplayer", 2);
                    case LEFT_OF_REALMS:
                    case RIGHT_OF_REALMS:
                        return findButton(buttons, "menu.online", 14);
                    case LEFT_OF_MODS:
                    case RIGHT_OF_MODS:
                        return findButton(buttons, "modmenu.title", 6);
                }
                throw new RuntimeException();
            }).map(button -> {
                switch (buttonPosition) {
                    case LEFT_OF_SINGLEPLAYER:
                    case LEFT_OF_MULTIPLAYER:
                    case LEFT_OF_REALMS:
                    case LEFT_OF_MODS:
                        return new Point(button.xPosition - 4 - 20, button.yPosition);
                    case RIGHT_OF_MODS:
                    case RIGHT_OF_SINGLEPLAYER:
                    case RIGHT_OF_MULTIPLAYER:
                    case RIGHT_OF_REALMS:
                        return new Point(button.xPosition + button.width + 4, button.yPosition);
                }
                throw new RuntimeException();
            }).orElse(topRight);
        }
    }

    @SubscribeEvent
    public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiScreen guiScreen = event.gui;
        GuiButton button = event.button;
        if(!button.enabled) return;
        if (guiScreen instanceof GuiMainMenu) {
            if (button.id == BUTTON_REPLAY_VIEWER) {
                new GuiReplayViewer(mod).display();
            }
        }

        if (guiScreen instanceof GuiIngameMenu && mod.getReplayHandler() != null) {
            if (button.id == BUTTON_EXIT_REPLAY) {
                button.enabled = false;
                try {
                    mod.getReplayHandler().endReplay();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class InjectedButton extends
            GuiButton
    {
        public final GuiScreen guiScreen;
        public final int id;
        private Consumer<InjectedButton> onClick;
        public InjectedButton(GuiScreen guiScreen, int buttonId, int x, int y, int width, int height, String buttonText, Consumer<GuiScreenEvent.ActionPerformedEvent.Pre> onClick) {
            super(buttonId, x, y, width, height, I18n.format(buttonText));
            this.guiScreen = guiScreen;
            this.id = buttonId;
            this.onClick = null;
        }
    }

    public enum MainMenuButtonPosition {
        // The old big button below Realms/Mods which pushes other buttons around.
        BIG,
        // Right of the bottom-most button in the main block of buttons (so not the quit button).
        // That will generally be either RIGHT_OF_REALMS or RIGHT_OF_MODS depending on version and installed mods.
        DEFAULT,
        TOP_LEFT,
        TOP_RIGHT,
        LEFT_OF_SINGLEPLAYER,
        RIGHT_OF_SINGLEPLAYER,
        LEFT_OF_MULTIPLAYER,
        RIGHT_OF_MULTIPLAYER,
        LEFT_OF_REALMS,
        RIGHT_OF_REALMS,
        LEFT_OF_MODS,
        RIGHT_OF_MODS,
    }
}
