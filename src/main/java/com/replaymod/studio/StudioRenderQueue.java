package com.replaymod.studio;

import com.replaymod.core.ReplayMod;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.render.utils.RenderJob;
import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.gui.GuiScreen;
import java.util.ArrayList;
import java.util.List;

public final class StudioRenderQueue extends StudioScreen {
    private final GuiScreen parent;
    private final ReplayHandler replay;
    private int offset, selected = -1;
    private boolean running;
    public StudioRenderQueue(GuiScreen parent, ReplayHandler replay) { this.parent = parent; this.replay = replay; }
    private List<RenderJob> jobs() { return ReplayModRender.instance.getRenderQueue(); }
    @Override protected StudioViewport createViewport() { return new StudioViewport(width, height, 80, 36, 800, 464, true); }
    @Override protected void drawBackdrop(int x, int y, float partialTicks) { drawRect(0, 0, width, height, 0xFF111B24); }
    @Override protected void build() {
        button(1, 100, 450, 120, "Previous"); button(2, 230, 450, 120, "Next");
        button(3, 370, 450, 150, "Remove selected");
        button(4, 530, 450, 150, "Back"); button(5, 690, 450, 170, "Render queue");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        panel(80, 36, canvasWidth - 80, canvasHeight - 36, "RENDER QUEUE");
        label("Each item keeps a copy of its camera path and video settings.", 100, 66, MUTED);
        label("Complete this queue before switching recordings.", 100, 84, MUTED);
        for (int i = 0; i < 12 && offset + i < jobs().size(); i++) {
            RenderJob job = jobs().get(offset + i);
            int top = 112 + i * 25;
            drawRect(100, top, 860, top + 23, offset + i == selected ? 0xFF29425A : 0x801B2835);
            label(fontRendererObj.trimStringToWidth(job.getName(), 500), 110, top + 7, TEXT);
            label(job.getSettings().getTargetVideoWidth() + " x " + job.getSettings().getTargetVideoHeight() + " / " + job.getSettings().getFramesPerSecond() + " fps", 650, top + 7, MUTED);
        }
    }
    @Override protected void clicked(int x, int y, int button) {
        if (button == 0 && hit(x, y, 100, 112, 760, 300)) {
            int index = offset + (y - 112) / 25;
            if (index < jobs().size()) selected = index;
        }
    }
    @Override protected void activate(int id) {
        if (id == 1) offset = Math.max(0, offset - 12);
        if (id == 2) offset = Math.min(Math.max(0, jobs().size() - 1), offset + 12);
        if (id == 3 && selected >= 0 && selected < jobs().size()) { jobs().remove(selected); selected = -1; offset = Math.min(offset, Math.max(0, jobs().size() - 1)); ReplayModRender.instance.saveRenderQueue(); }
        if (id == 4) escape();
        if (id == 5 && !running) {
            if (jobs().isEmpty()) throw new IllegalArgumentException("The queue is empty. Add an export first.");
            List<RenderJob> queue = new ArrayList<>(jobs());
            for (RenderJob job : queue) if (job.getSettings().getOutputFile().exists()) throw new IllegalArgumentException("Output already exists: " + job.getName() + ". Remove that item or rename its existing output.");
            running = true;
            ReplayMod.instance.runLaterWithoutLock(() -> {
                try {
                    for (RenderJob job : queue) {
                        if (!new VideoRenderer(job.getSettings(), replay, job.getTimeline()).renderVideo()) { mc.displayGuiScreen(this); return; }
                        jobs().remove(job); ReplayModRender.instance.saveRenderQueue();
                    }
                } catch (Throwable error) {
                    ReplayModRender.LOGGER.error("Queued render failed", error);
                    mc.displayGuiScreen(this); message = "Export failed: " + error.getClass().getSimpleName() + ": " + error.getMessage();
                } finally { running = false; }
            });
        }
    }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
}
