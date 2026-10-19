package com.replaymod.studio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** Native Minecraft controls with WGMap's flat blue-gray visual language. */
public abstract class StudioScreen extends GuiScreen {
    protected static final int PANEL = 0xEE111B24, EDGE = 0xFF4E6478;
    protected static final int ACCENT = 0xFF79A9CF, TEXT = 0xFFE4EDF4, MUTED = 0xFF9BB0C1;
    protected final List<GuiTextField> fields = new ArrayList<>();
    protected int canvasWidth = 960, canvasHeight = 540;
    private StudioViewport viewport;
    protected String message = "";

    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void setWorldAndResolution(Minecraft minecraft, int width, int height) {
        if (viewport != null) beforeResize();
        super.setWorldAndResolution(minecraft, width, height);
    }
    protected void beforeResize() { }
    protected StudioViewport createViewport() { return new StudioViewport(width, height, canvasWidth, canvasHeight); }
    @Override public void initGui() {
        buttonList.clear();
        fields.clear();
        viewport = createViewport();
        canvasWidth = viewport.canvasWidth; canvasHeight = viewport.canvasHeight;
        Keyboard.enableRepeatEvents(true);
        build();
    }
    protected abstract void build();
    protected abstract void drawContent(int x, int y, float partialTicks);
    protected void activate(int id) { }
    protected void submit() { }
    protected void escape() { mc.displayGuiScreen(null); }
    protected void clicked(int x, int y, int button) { }
    protected void dragged(int x, int y, int button) { }
    protected void released(int x, int y, int button) { }
    protected void scrolled(int x, int y, int amount) { }

    @Override public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * width / mc.displayWidth;
            int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            attempt(() -> scrolled(logicalX(x), logicalY(y), wheel));
        }
    }

    protected int logicalX(int x) { return viewport.logicalX(x); }
    protected int logicalY(int y) { return viewport.logicalY(y); }
    @Override public final void drawScreen(int x, int y, float partialTicks) {
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPushMatrix();
        GL11.glTranslatef(viewport.x, viewport.y, 0);
        GL11.glScalef(viewport.scale, viewport.scale, 1);
        try {
            drawContent(logicalX(x), logicalY(y), partialTicks);
            super.drawScreen(logicalX(x), logicalY(y), partialTicks);
            for (GuiTextField field : fields) field.drawTextBox();
            if (!message.isEmpty()) {
                List<String> lines = fontRendererObj.listFormattedStringToWidth(message, canvasWidth - 40);
                int count = Math.min(3, lines.size()), top = canvasHeight - count * 11 - 8;
                drawRect(10, top - 4, canvasWidth - 10, canvasHeight - 3, 0xFA111B24);
                for (int i = 0; i < count; i++) label(lines.get(i), 20, top + i * 11, 0xFFFFCB82);
            }
        } finally { GL11.glPopMatrix(); }
    }
    @Override protected final void actionPerformed(GuiButton button) {
        if (button.enabled) { message = ""; attempt(() -> activate(button.id)); }
    }
    protected void attempt(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException error) { message = error.getMessage() == null ? "Could not apply that change (" + error.getClass().getSimpleName() + ")." : error.getMessage(); }
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        x = logicalX(x); y = logicalY(y);
        for (GuiTextField field : fields) field.mouseClicked(x, y, button);
        boolean control = false;
        for (Object value : buttonList) {
            GuiButton item = (GuiButton) value;
            if (item.enabled && item.visible && hit(x, y, item.xPosition, item.yPosition, item.width, item.height)) control = true;
        }
        super.mouseClicked(x, y, button);
        if (control || mc.currentScreen != this) return;
        final int px = x, py = y;
        attempt(() -> clicked(px, py, button));
    }
    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        final int px = logicalX(x), py = logicalY(y);
        attempt(() -> dragged(px, py, button));
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        released(logicalX(x), logicalY(y), button);
        super.mouseReleased(logicalX(x), logicalY(y), button);
    }
    @Override protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) { escape(); return; }
        if (key == Keyboard.KEY_TAB && !fields.isEmpty()) {
            int selected = -1;
            for (int i = 0; i < fields.size(); i++) if (fields.get(i).isFocused()) selected = i;
            if (selected >= 0) fields.get(selected).setFocused(false);
            fields.get((selected + (isShiftKeyDown() ? fields.size() - 1 : 1) + fields.size()) % fields.size()).setFocused(true);
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { message = ""; attempt(this::submit); return; }
        for (GuiTextField field : fields) if (field.isFocused()) { field.textboxKeyTyped(character, key); return; }
        shortcut(character, key);
    }
    protected void shortcut(char character, int key) { }
    @Override public void updateScreen() { for (GuiTextField field : fields) field.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    protected GuiTextField field(int x, int y, int w, String value) {
        GuiTextField result = new GuiTextField(fontRendererObj, x, y, w, 18);
        result.setMaxStringLength(1024);
        result.setTextColor(TEXT);
        result.setText(value);
        fields.add(result);
        return result;
    }
    protected GuiButton button(int id, int x, int y, int w, String title) {
        GuiButton result = new FlatButton(id, x, y, w, 20, title);
        buttonList.add(result);
        return result;
    }
    protected void label(String text, int x, int y, int color) { fontRendererObj.drawString(text, x, y, color); }
    protected void panel(int x, int y, int w, int h, String title) {
        drawRect(x, y, x + w, y + h, PANEL);
        drawRect(x, y, x + w, y + 2, ACCENT);
        if (!title.isEmpty()) label(title, x + 10, y + 10, TEXT);
    }
    protected boolean hit(int x, int y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }
    public static final class FlatButton extends GuiButton {
        public FlatButton(int id, int x, int y, int w, int h, String title) { super(id, x, y, w, h, title); }
        @Override public void drawButton(Minecraft mc, int x, int y) {
            if (!visible) return;
            boolean hover = enabled && x >= xPosition && x < xPosition + width && y >= yPosition && y < yPosition + height;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, hover ? 0xFF7199BA : EDGE);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, hover ? 0xFF466586 : 0xF01B2835);
            String title = mc.fontRendererObj.trimStringToWidth(displayString, width - 8);
            drawCenteredString(mc.fontRendererObj, title, xPosition + width / 2, yPosition + (height - 8) / 2, enabled ? TEXT : 0xFF81909B);
        }
    }
}
