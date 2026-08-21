package com.replaymod.core.versions.forge;

import com.replaymod.gradle.remap.Pattern;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.config.Property;

class Patterns {
    @Pattern
    private static GuiButton getButton(GuiScreenEvent.ActionPerformedEvent event) {
        return event.button;
    }

    @Pattern
    private static GuiScreen getGui(GuiScreenEvent event) {
        return event.gui;
    }

    @Pattern
    private static EntityLivingBase getEntity(RenderLivingEvent event) {
        return event.entity;
    }

    @Pattern
    private static RenderGameOverlayEvent.ElementType getType(RenderGameOverlayEvent event) {
        return event.type;
    }

    @Pattern
    private static void setComment(Property property, String comment) {
        property.comment = comment;
    }
}
