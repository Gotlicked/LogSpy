package net.gotlicked.logspy.mixin;

import net.gotlicked.logspy.LogSpyConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Confirms the LogSpy pipeline is live once the Minecraft client finishes initialising. */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(at = @At("TAIL"), method = "<init>")
    private void logspy$onClientReady(CallbackInfo ci) {
        LogSpyConstants.LOG.info("Starting LogSpy for: MC {}",
                Minecraft.getInstance().getVersionType());
    }
}
