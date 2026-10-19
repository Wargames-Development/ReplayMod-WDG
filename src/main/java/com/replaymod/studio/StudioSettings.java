package com.replaymod.studio;

import com.replaymod.core.SettingsRegistry;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import java.util.ArrayList;
import java.util.List;

public final class StudioSettings extends StudioScreen {
    private final GuiScreen parent;
    private final SettingsRegistry registry;
    private final List<SettingsRegistry.SettingKey<?>> options = new ArrayList<>();
    private int page;
    public StudioSettings(GuiScreen parent, SettingsRegistry registry) {
        this.parent = parent; this.registry = registry;
        for (SettingsRegistry.SettingKey<?> key : registry.getSettings()) if (key.getDisplayString() != null) options.add(key);
    }
    @Override protected void build() {
        for (int i = 0; i < 12 && page * 12 + i < options.size(); i++) {
            SettingsRegistry.SettingKey<?> key = options.get(page * 12 + i);
            Object value = registry.get(key);
            String display = value instanceof Boolean ? ((Boolean) value ? "On" : "Off") : I18n.format(String.valueOf(value));
            button(i, 100, 90 + i * 28, 760, I18n.format(key.getDisplayString()) + ": " + display);
        }
        button(90, 100, 456, 120, "Previous"); button(91, 230, 456, 120, "Next"); button(92, 740, 456, 120, "Done");
    }
    @Override protected void drawContent(int x, int y, float partialTicks) {
        drawRect(0, 0, 960, 540, 0xC0101720);
        panel(80, 36, 800, 464, "REPLAY SETTINGS");
        label("Click an option to change it. Settings save immediately.", 100, 64, MUTED);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override protected void activate(int id) {
        if (id == 90) page = Math.max(0, page - 1);
        else if (id == 91) page = Math.min(Math.max(0, (options.size() - 1) / 12), page + 1);
        else if (id == 92) { escape(); return; }
        else {
            SettingsRegistry.SettingKey key = options.get(page * 12 + id);
            Object value = registry.get(key);
            if (value instanceof Boolean) registry.set(key, !((Boolean) value));
            else if (key instanceof SettingsRegistry.MultipleChoiceSettingKey) {
                List choices = ((SettingsRegistry.MultipleChoiceSettingKey) key).getChoices();
                if (!choices.isEmpty()) registry.set(key, choices.get((choices.indexOf(value) + 1) % choices.size()));
            } else throw new IllegalArgumentException("This setting is edited through its dedicated editor.");
            registry.save();
        }
        initGui();
    }
    @Override protected void escape() { mc.displayGuiScreen(parent); }
}
