package com.replaymod.core.gui;

import de.johni0702.minecraft.gui.GuiRenderer;
import de.johni0702.minecraft.gui.RenderInfo;
import de.johni0702.minecraft.gui.container.AbstractGuiContainer;
import de.johni0702.minecraft.gui.container.GuiPanel;
import de.johni0702.minecraft.gui.container.GuiScreen;
import de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import de.johni0702.minecraft.gui.element.GuiElement;
import de.johni0702.minecraft.gui.layout.CustomLayout;
import de.johni0702.minecraft.gui.layout.VerticalLayout;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;

import static com.replaymod.core.versions.MCVer.getMinecraft;

public class GuiBackgroundProcesses extends EventRegistrations {
    private GuiPanel panel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(10));

    { on(InitScreenCallback.EVENT, (screen, buttons) -> onGuiInit(screen)); }
    private void onGuiInit(net.minecraft.client.gui.GuiScreen guiScreen) {
        if (guiScreen != getMinecraft().currentScreen) return; // people tend to construct GuiScreens without opening them

        VanillaGuiScreen vanillaGui = VanillaGuiScreen.wrap(guiScreen);
        vanillaGui.setLayout(new CustomLayout<GuiScreen>(vanillaGui.getLayout()) {
            @Override
            protected void layout(GuiScreen container, int width, int height) {
                pos(panel, width - 5 - width(panel), 5);
            }
        }).addElements(null, panel);
    }

    public void addProcess(GuiElement<?> element) {
        panel.addElements(new VerticalLayout.Data(1.0),
                new Element(element));
    }

    public void removeProcess(GuiElement<?> element) {
        for (GuiElement child : panel.getChildren()) {
            if (((Element) child).inner == element) {
                panel.removeElement(child);
                return;
            }
        }
    }

    private static class Element extends AbstractGuiContainer<Element> {
        private GuiElement inner;

        Element(GuiElement inner) {
            this.inner = inner;
            addElements(null, inner);

            setLayout(new CustomLayout<Element>() {
                @Override
                protected void layout(Element container, int width, int height) {
                    pos(inner, 10, 10);
                    size(inner, width - 20, height - 20);
                }
            });
        }

        @Override
        public int getLayer() {
            return 1;
        }

        @Override
        public ReadableDimension getMinSize() {
            ReadableDimension minSize = inner.getMinSize();
            return new Dimension(minSize.getWidth() + 20, minSize.getHeight() + 20);
        }

        @Override
        public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
            renderer.drawRect(0, 0, size.getWidth(), size.getHeight(),
                    new de.johni0702.minecraft.gui.utils.lwjgl.Color(17, 27, 36, 238));
            renderer.drawRect(0, 0, size.getWidth(), 2,
                    new de.johni0702.minecraft.gui.utils.lwjgl.Color(121, 169, 207, 255));
            super.draw(renderer, size, renderInfo);
        }

        @Override
        protected Element getThis() {
            return this;
        }
    }
}
