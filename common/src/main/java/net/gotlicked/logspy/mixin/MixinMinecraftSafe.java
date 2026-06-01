package net.gotlicked.logspy.mixin;

import net.gotlicked.logspy.LogSpyConstants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftSafe {

    @Unique private static final int MAX_CONSECUTIVE_FAILURES = 3;
    @Unique final AtomicInteger logspy$consecutiveFailures = new AtomicInteger(0);

    // Wraps runTick to catch RuntimeExceptions; re-throws after 3 consecutive failures.
    @WrapOperation(
            method = "run()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"),
            require = 0
    )
    private void logspy$safeRunTick(Minecraft instance, boolean advanceGameTime, Operation<Void> op) {
        try {
            op.call(instance, advanceGameTime);
            logspy$consecutiveFailures.set(0);
        } catch (RuntimeException e) {
            int failures = logspy$consecutiveFailures.incrementAndGet();
            LogSpyConstants.LOG.error("RuntimeException in client tick ({}/{} before crash): {}",
                    failures, MAX_CONSECUTIVE_FAILURES, e.getMessage(), e);
            if (failures >= MAX_CONSECUTIVE_FAILURES) throw e;
        }
    }
}
