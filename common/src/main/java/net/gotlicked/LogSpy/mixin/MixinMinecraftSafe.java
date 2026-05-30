package net.gotlicked.LogSpy.mixin;

import net.gotlicked.LogSpy.Constants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps the runTick() call inside Minecraft.run() to catch per-tick RuntimeExceptions.
 * Re-throws after MAX_CONSECUTIVE_FAILURES consecutive failures to allow a proper crash report.
 * Target verified against MC 26.1.2: run() calls this.runTick(!oomRecovery) directly.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftSafe {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private final AtomicInteger logspy$consecutiveFailures = new AtomicInteger(0);

    @WrapOperation(
            method = "run()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"),
            require = 0
    )
    private void logspy$safeRunTick(Minecraft instance, boolean renderLevel, Operation<Void> op) {
        try {
            op.call(instance, renderLevel);
            logspy$consecutiveFailures.set(0);
        } catch (RuntimeException e) {
            int failures = logspy$consecutiveFailures.incrementAndGet();
            Constants.LOG.error("[LogSpy] RuntimeException in client tick ({}/{} before crash): {}",
                    failures, MAX_CONSECUTIVE_FAILURES, e.getMessage(), e);
            if (failures >= MAX_CONSECUTIVE_FAILURES) throw e;
        }
    }
}
