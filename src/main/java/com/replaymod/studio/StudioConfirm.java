package com.replaymod.studio;

import net.minecraft.client.gui.GuiScreen;
import java.util.List;

public final class StudioConfirm extends StudioScreen {
    private final GuiScreen parent;
    private final String title, action;
    private final List<String> lines;
    private final Runnable accept;
    private Runnable cancel;
    private int offset;
    public StudioConfirm(GuiScreen parent, String title, List<String> lines, String action, Runnable accept) {
        this.parent = parent; this.title = title; this.lines = lines; this.action = action; this.accept = accept;
    }
    public StudioConfirm onCancel(Runnable cancel) { this.cancel = cancel; return this; }
    @Override protected void build() {
        button(1, 100, 454, 140, "Previous"); button(2, 250, 454, 140, "Next");
        button(3, 530, 454, 150, "Cancel"); button(4, 690, 454, 170, action);
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xC0101720); panel(80, 40, 800, 464, title);
        for (int i = 0; i < 16 && offset + i < lines.size(); i++) label(fontRendererObj.trimStringToWidth(lines.get(offset + i), 760), 100, 90 + i * 20, TEXT);
    }
    @Override protected void activate(int id) {
        if (id == 1) offset = Math.max(0, offset - 16);
        if (id == 2) offset = Math.min(Math.max(0, lines.size() - 1), offset + 16);
        if (id == 3) escape();
        if (id == 4) { mc.displayGuiScreen(parent); accept.run(); }
    }
    @Override protected void escape() { if (cancel != null) cancel.run(); else mc.displayGuiScreen(parent); }
}
