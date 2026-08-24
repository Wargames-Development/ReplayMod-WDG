package com.replaymod.replay.compat;

import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Render-only compatibility context for mods which distinguish the local
 * client player from other players.
 *
 * During replay spectating Minecraft.thePlayer remains ReplayMod's
 * CameraEntity while Minecraft.renderViewEntity is the recorded entity being
 * watched.  Some 1.7.10 mods use their local-player accessor from inside
 * entity renderers to decide which first-person parts to hide.  Returning the
 * spectated player globally would be unsafe because those same accessors are
 * also used by client control and physics code.
 *
 * This context therefore exposes the spectated player only while an entity is
 * actively being rendered.  Optional mod adapters can use it for visual
 * identity checks without making the recorded entity behave like the live
 * controlling player outside rendering.
 */
public final class ReplayRenderViewContext {
    private static final ThreadLocal<Integer> ENTITY_RENDER_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private ReplayRenderViewContext() {
    }

    public static void enterEntityRender() {
        ENTITY_RENDER_DEPTH.set(ENTITY_RENDER_DEPTH.get() + 1);
    }

    public static void exitEntityRender() {
        int depth = ENTITY_RENDER_DEPTH.get();
        if (depth <= 1) {
            ENTITY_RENDER_DEPTH.remove();
        } else {
            ENTITY_RENDER_DEPTH.set(depth - 1);
        }
    }

    public static boolean isEntityRendering() {
        return ENTITY_RENDER_DEPTH.get() > 0;
    }

    /**
     * @return the recorded player currently being spectated, but only while an
     *         entity render is in progress; otherwise {@code null}
     */
    public static EntityPlayer getSpectatedPlayerForEntityRender() {
        if (!isEntityRendering()) {
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.thePlayer instanceof CameraEntity)) {
            return null;
        }

        Entity view = mc.renderViewEntity;
        if (view instanceof EntityPlayer && view != mc.thePlayer) {
            return (EntityPlayer) view;
        }

        return null;
    }
}
