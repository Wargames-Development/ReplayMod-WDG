package com.replaymod.replay.gui.screen;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.NetworkManager;

public class GuiOpeningReplay extends GuiScreen {
    private final NetworkManager networkManager;

    public GuiOpeningReplay(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF111B24);
        drawRect(width / 2 - 120, height / 2 - 26, width / 2 + 120, height / 2 - 24, 0xFF79A9CF);
        drawCenteredString(fontRendererObj, "Loading recording...", width / 2, height / 2 - 10, 0xFFE4EDF4);
        drawCenteredString(fontRendererObj, "Press T for the cinematic editor once loaded.", width / 2, height / 2 + 8, 0xFF9BB0C1);
    }

    @Override
    public void handleInput() {
        networkManager.processReceivedPackets();
    }
}
