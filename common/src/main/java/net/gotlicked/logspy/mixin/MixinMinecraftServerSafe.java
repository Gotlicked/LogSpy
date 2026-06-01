package net.gotlicked.logspy.mixin;

import net.gotlicked.logspy.LogSpyConstants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Wraps the tickServer() call inside processPacketsAndTick() to catch per-tick RuntimeExceptions.
 * Re-throws after MAX_CONSECUTIVE_FAILURES consecutive failures to allow a proper crash report.
 * Target verified against MC 26.1.2: runServer() → processPacketsAndTick() → tickServer().
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerSafe {

    @Unique
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    @Unique
    private final AtomicInteger logspy$consecutiveFailures = new AtomicInteger(0);

    @WrapOperation(
            method = "processPacketsAndTick(Z)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"),
            require = 0
    )
    private void logspy$safeTickServer(MinecraftServer instance, BooleanSupplier haveTime, Operation<Void> op) {
        try {
            op.call(instance, haveTime);
            logspy$consecutiveFailures.set(0);
        } catch (RuntimeException e) {
            int failures = logspy$consecutiveFailures.incrementAndGet();
            LogSpyConstants.LOG.error("RuntimeException in server tick ({}/{} before crash): {}",
                    failures, MAX_CONSECUTIVE_FAILURES, e.getMessage(), e);
            if (failures >= MAX_CONSECUTIVE_FAILURES) throw e;
        }
    }
}
