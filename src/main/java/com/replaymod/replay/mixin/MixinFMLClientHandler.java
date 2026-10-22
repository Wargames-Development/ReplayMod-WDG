package com.replaymod.replay.mixin;

import com.replaymod.replay.gui.screen.GuiOpeningReplay;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FMLClientHandler.class, remap = false)
public class MixinFMLClientHandler {
    @Inject(method = "waitForPlayClient", at = @At("HEAD"), remap = false)
    private void tickNetworkManager(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) {
            return;
        }
        ReplayHandler replay = ReplayModReplay.instance == null ? null : ReplayModReplay.instance.getReplayHandler();
        if (replay != null) {
            // The opaque editor and export GUI deliberately stay open on rewind.
            // JoinGame must still run before Forge waits on its play-client latch.
            replay.processReplayPackets();
            return;
        }
        GuiScreen gui = mc.currentScreen;
        if (gui instanceof GuiOpeningReplay) {
            gui.handleInput();
        }
    }
}
