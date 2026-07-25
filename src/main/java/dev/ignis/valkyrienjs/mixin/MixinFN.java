package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(value = org.valkyrienskies.core.impl.shadow.FN.class, remap = false)
public class MixinFN {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");
    private static final String FIELD_NAME = "b";
    private static Field queueField;

    static {
        try {
            queueField = org.valkyrienskies.core.impl.shadow.FN.class.getDeclaredField(FIELD_NAME);
            queueField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOGGER.error("[ValkyrienJS] Failed to find field '{}' in FN class", FIELD_NAME, e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentLinkedQueue<Object> getQueue(Object instance) {
        if (queueField == null) return null;
        try {
            return (ConcurrentLinkedQueue<Object>) queueField.get(instance);
        } catch (IllegalAccessException e) {
            LOGGER.error("[ValkyrienJS] Failed to access field '{}'", FIELD_NAME, e);
            return null;
        }
    }

    @Inject(method = "a(DZ)Lorg/valkyrienskies/core/impl/shadow/FL;", at = @At("HEAD"), remap = false)
    private void onPhysTickStart(double dt, boolean isLastTick, CallbackInfoReturnable<org.valkyrienskies.core.impl.shadow.FL> cir) {
        ConcurrentLinkedQueue<?> queue = getQueue(this);
        if (queue != null && queue.size() > 100) {
            int droppedCount = queue.size();
            long currentTime = System.currentTimeMillis();
            Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Physics thread detected severe game frame queue backpressure! Dropping {} backed-up game frames. Current thread: {} (ID: {}), Timestamp: {}. This indicates the physics stage is running slower than the game stage. The queue will be cleared to restore real-time synchronization.", droppedCount, currentThread.getName(), currentThread.getId(), currentTime);
            queue.clear();
        }
    }

    @Redirect(method = "a(Lorg/valkyrienskies/core/impl/shadow/FG;)V", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;)V", ordinal = 0), remap = false)
    private void redirectQueueFullLog(org.apache.logging.log4j.Logger logger, String originalMessage) {
        ConcurrentLinkedQueue<?> queue = getQueue(this);
        int queueSize = queue != null ? queue.size() : -1;
        Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Game frame queue backpressure detected! Current queue size: {} (threshold: 800). Current thread: {} (ID: {}). This indicates the physics stage is running slower than the game stage, causing game frames to accumulate in the queue. The producer thread will sleep 1000ms to allow the consumer to catch up. Original message: {}", queueSize, currentThread.getName(), currentThread.getId(), originalMessage);
    }
}
