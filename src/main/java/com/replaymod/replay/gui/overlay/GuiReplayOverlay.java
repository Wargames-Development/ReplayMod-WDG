package com.replaymod.replay.gui.overlay;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.events.KeyBindingEventCallback;
import com.replaymod.core.events.KeyEventCallback;
import com.replaymod.core.versions.MCVer.Keyboard;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.ReplaySender;
import de.johni0702.minecraft.gui.GuiRenderer;
import de.johni0702.minecraft.gui.RenderInfo;
import de.johni0702.minecraft.gui.container.AbstractGuiOverlay;
import de.johni0702.minecraft.gui.container.GuiPanel;
import de.johni0702.minecraft.gui.element.GuiButton;
import de.johni0702.minecraft.gui.element.GuiElement;
import de.johni0702.minecraft.gui.element.GuiSlider;
import de.johni0702.minecraft.gui.element.GuiTooltip;
import de.johni0702.minecraft.gui.element.advanced.IGuiTimeline;
import de.johni0702.minecraft.gui.layout.CustomLayout;
import de.johni0702.minecraft.gui.layout.HorizontalLayout;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import de.johni0702.minecraft.gui.utils.lwjgl.WritablePoint;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.event.GuiOpenEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import static com.replaymod.core.ReplayMod.TEXTURE_SIZE;

public class GuiReplayOverlay extends AbstractGuiOverlay<GuiReplayOverlay> {

    private final ReplayModReplay mod = ReplayModReplay.instance;

    public final GuiPanel topPanel = new GuiPanel(this)
            .setLayout(new HorizontalLayout(HorizontalLayout.Alignment.LEFT).setSpacing(5));
    public final GuiButton playPauseButton = new GuiButton() {
        @Override
        public GuiElement getTooltip(RenderInfo renderInfo) {
            GuiTooltip tooltip = (GuiTooltip) super.getTooltip(renderInfo);
            if (tooltip != null) {
                String label;
                if (getSpriteUV().getY() == 0) { // Play button
                    label = "replaymod.gui.ingame.menu.unpause";
                } else { // Pause button
                    label = "replaymod.gui.ingame.menu.pause";
                }
                tooltip.setText(I18n.format(label) + " (" + mod.keyPlayPause.getBoundKey() + ")");
            }
            return tooltip;
        }
    }.setSize(20, 20).setTexture(ReplayMod.TEXTURE, TEXTURE_SIZE).setTooltip(new GuiTooltip());
    public final GuiSlider speedSlider = new GuiSlider().setSize(100, 20).setSteps(37); // 0.0 is not included
    public final GuiMarkerTimeline timeline;

    /**
     * This is not used by the replay module itself but may be used by other modules/extras to show
     * when they're active.
     */
    public final GuiPanel statusIndicatorPanel = new GuiPanel(this).setSize(100, 16)
            .setLayout(new HorizontalLayout(HorizontalLayout.Alignment.RIGHT).setSpacing(5));

    private final EventHandler eventHandler = new EventHandler();
    private boolean hidden;
    private com.replaymod.studio.CinematicEditor studioEditor;

    public com.replaymod.studio.CinematicEditor getStudioEditor() {
        if (studioEditor == null) studioEditor = new com.replaymod.studio.CinematicEditor(mod.getReplayHandler());
        if (!com.replaymod.simplepathing.ReplayModSimplePathing.instance.getGuiPathing().isPreviewPlaying()) mod.getReplayHandler().getReplaySender().setReplaySpeed(0);
        return studioEditor;
    }
    public void resetStudioEditor() { studioEditor = null; }

    public GuiReplayOverlay(final ReplayHandler replayHandler) {
        timeline = new GuiMarkerTimeline(replayHandler){
            @Override
            public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
                setCursorPosition(replayHandler.getReplaySender().currentTimeStamp());
                super.draw(renderer, size, renderInfo);
            }
        }.setSize(Integer.MAX_VALUE, 20);

        topPanel.addElements(null, playPauseButton, speedSlider, timeline);
        setLayout(new CustomLayout<GuiReplayOverlay>() {
            @Override
            protected void layout(GuiReplayOverlay container, int width, int height) {
                pos(topPanel, 10, 10);
                size(topPanel, width - 20, 20);

                pos(statusIndicatorPanel, width / 2, height - 21);
                width(statusIndicatorPanel, width / 2 - 5);
            }
        });

        playPauseButton.setSpriteUV(new ReadablePoint() {
            @Override
            public int getX() {
                return 0;
            }

            @Override
            public int getY() {
                return replayHandler.getReplaySender().paused() ? 0 : 20;
            }

            @Override
            public void getLocation(WritablePoint dest) {
                dest.setLocation(getX(), getY());
            }
        }).onClick(new Runnable() {
            @Override
            public void run() {
                ReplaySender replaySender = replayHandler.getReplaySender();
                // If currently paused
                if (replaySender.paused()) {
                    // then play
                    replaySender.setReplaySpeed(getSpeedSliderValue());
                } else {
                    // else pause
                    replaySender.setReplaySpeed(0);
                }
            }
        });

        speedSlider.onValueChanged(new Runnable() {
            @Override
            public void run() {
                double speed = getSpeedSliderValue();
                speedSlider.setText(I18n.format("replaymod.gui.speed") + ": " + speed + "x");
                ReplaySender replaySender = replayHandler.getReplaySender();
                if (!replaySender.paused()) {
                    replaySender.setReplaySpeed(speed);
                }
            }
        }).setValue(9);

        timeline.onClick(new IGuiTimeline.OnClick() {
            @Override
            public void run(int time) {
                replayHandler.doJump(time, true);
            }
        }).setLength(replayHandler.getReplayDuration());
    }

    public double getSpeedSliderValue() {
        int value = speedSlider.getValue() + 1;
        if (value <= 9) {
            return value / 10d;
        } else {
            return 1 + (0.25d * (value - 10));
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (isVisible() != visible) {
            if (visible) {
                eventHandler.register();
            } else {
                eventHandler.unregister();
            }
        }
        super.setVisible(visible);
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        // The native studio owns replay interaction. Keep the legacy overlay as
        // a data/API adapter for existing modules, without drawing two interfaces.
        if (com.replaymod.simplepathing.ReplayModSimplePathing.instance != null
                && com.replaymod.simplepathing.ReplayModSimplePathing.instance.getGuiPathing() != null) return;
        // Do not render overlay if all hud, or this one specifically, is hidden and we're not in some popup
        if ((getMinecraft().gameSettings.hideGUI || hidden) && isAllowUserInput()) {
            // Note that this only applies to when the mouse is visible, otherwise
            // the draw method isn't called in the first place
            return;
        }
        super.draw(renderer, size, renderInfo);
    }

    @Override
    public void setMouseVisible(boolean visible) {
        // The studio owns native mouse input. Legacy preview callbacks must not
        // replace it with jGui's invisible overlay input screen.
        if (visible && getMinecraft().currentScreen instanceof com.replaymod.studio.StudioScreen) return;
        if (visible && getMinecraft().currentScreen == null && mod.getReplayHandler() != null
                && com.replaymod.simplepathing.ReplayModSimplePathing.instance != null
                && com.replaymod.simplepathing.ReplayModSimplePathing.instance.getGuiPathing() != null) {
            super.setMouseVisible(false);
            getMinecraft().displayGuiScreen(getStudioEditor());
            return;
        }
        super.setMouseVisible(visible);
    }

    @Override
    protected GuiReplayOverlay getThis() {
        return this;
    }

    public class EventHandler extends EventRegistrations {
        { on(KeyBindingEventCallback.EVENT, this::onKeyBindingEvent); }
        private void onKeyBindingEvent() {
            GameSettings gameSettings = getMinecraft().gameSettings;
            while (gameSettings.keyBindChat.isPressed() || gameSettings.keyBindCommand.isPressed()) {
                if (!isMouseVisible()) {
                    setMouseVisible(true);
                }
            }
        }

        { on(KeyEventCallback.EVENT, (key, scanCode, action, modifiers) -> onKeyInput(key, action)); }
        private boolean onKeyInput(int key, int action) {
            if (action != KeyEventCallback.ACTION_PRESS) return false;
            GameSettings gameSettings = getMinecraft().gameSettings;
            // Read the configured keys before KeyBinding's single-entry-per-key
            // lookup or another mod can consume their press counters.
            if (getMinecraft().currentScreen == null && key != 0
                    && (key == gameSettings.keyBindChat.getKeyCode()
                    || key == gameSettings.keyBindCommand.getKeyCode())) {
                setMouseVisible(true);
                return true;
            }
            // Allow F1 to be used to hide the replay gui (e.g. for recording with OBS)
            if (isMouseVisible() && key == Keyboard.KEY_F1) {
                hidden = !hidden;
            }
            return false;
        }

        @SubscribeEvent
        public void onGuiOpen(GuiOpenEvent event) {
            if (event.gui instanceof net.minecraft.client.gui.GuiIngameMenu && mod.getReplayHandler() != null
                    && com.replaymod.simplepathing.ReplayModSimplePathing.instance.getGuiPathing() != null) {
                event.setCanceled(true);
                getMinecraft().displayGuiScreen(new com.replaymod.studio.StudioPause(mod.getReplayHandler()));
                return;
            }
            // A mod can still open chat directly after the raw key callback.
            // Keep the overlay's input screen so timeline buttons receive clicks.
            if (event.gui instanceof GuiChat) {
                event.setCanceled(true);
                setMouseVisible(true);
            }
        }
    }
}
