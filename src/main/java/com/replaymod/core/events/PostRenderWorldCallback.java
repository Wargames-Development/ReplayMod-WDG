package com.replaymod.core.events;

import de.johni0702.minecraft.gui.utils.Event;
import de.johni0702.minecraft.gui.versions.MatrixStack;

public interface PostRenderWorldCallback {
    Event<PostRenderWorldCallback> EVENT = Event.create((listeners) ->
            (MatrixStack matrixStack) -> {
                for (PostRenderWorldCallback listener : listeners) {
                    listener.postRenderWorld(matrixStack);
                }
            }
    );

    void postRenderWorld(MatrixStack matrixStack);
}
