package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mixin for FQ class - handles game frame queue backpressure from the producer side
 * FQ.b is FN (the physics engine core), and FN.b is ConcurrentLinkedQueue<FG> (game frame queue)
 * When the physics thread is stalled, game frames accumulate in FN.b.
 * This mixin:
 * 1. Drops new game frames when the queue is severely backpressured (>200 frames)
 * 2. Prevents Thread.sleep(1000) from blocking the main thread when queue is full
 */
@Mixin(value = org.valkyrienskies.core.impl.shadow.FQ.class, remap = false)
public class MixinFQ {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");
    private static final String FN_FIELD_NAME = "b"; // FQ.b is FN
    private static final String QUEUE_FIELD_NAME = "b"; // FN.b is ConcurrentLinkedQueue<FG>
    private static Field fnField;
    private static Field queueField;
    private static boolean initialized = false;
    private static int dropCount = 0;
    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 5000;

    static {
        LOGGER.info("[ValkyrienJS] MixinFQ static initializer started");
        try {
            // Get FQ.b field (FN instance)
            fnField = org.valkyrienskies.core.impl.shadow.FQ.class.getDeclaredField(FN_FIELD_NAME);
            fnField.setAccessible(true);

            // Get FN.b field (ConcurrentLinkedQueue<FG>)
            queueField = org.valkyrienskies.core.impl.shadow.FN.class.getDeclaredField(QUEUE_FIELD_NAME);
            queueField.setAccessible(true);

            initialized = true;
            LOGGER.info("[ValkyrienJS] MixinFQ initialized successfully");
        } catch (NoSuchFieldException e) {
            LOGGER.error("[ValkyrienJS] Failed to find field in MixinFQ", e);
        } catch (Exception e) {
            LOGGER.error("[ValkyrienJS] Unexpected error in MixinFQ static initializer", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentLinkedQueue<Object> getQueue(Object fqInstance) {
        if (fnField == null || queueField == null) return null;
        try {
            Object fnInstance = fnField.get(fqInstance);
            if (fnInstance == null) return null;
            Object queue = queueField.get(fnInstance);
            if (queue instanceof ConcurrentLinkedQueue) {
                return (ConcurrentLinkedQueue<Object>) queue;
            }
            return null;
        } catch (IllegalAccessException e) {
            LOGGER.error("[ValkyrienJS] Failed to access queue in MixinFQ", e);
            return null;
        }
    }

    /**
     * Inject into FQ.postTickGame() at the beginning.
     * Check if the game frame queue is severely backpressured.
     * If so, clear the queue to prevent infinite accumulation.
     */
    @Inject(
        method = "postTickGame",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void onPostTickGame(CallbackInfo ci) {
        if (!initialized) return;

        ConcurrentLinkedQueue<?> queue = getQueue(this);
        if (queue == null) return;

        int size = queue.size();
        long now = System.currentTimeMillis();

        // Throttled logging
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            lastLogTime = now;
            if (size > 0) {
                LOGGER.info("[ValkyrienJS] Game frame queue status (from FQ): size={}, drops={}",
                    size, dropCount);
            }
        }

        // If queue is severely backpressured, clear it to prevent server hang
        if (size > 200) {
            dropCount++;
            int dropped = size;
            Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Game thread detected severe game frame queue backpressure! " +
                "Dropping {} backed-up game frames (totalDrops={}). " +
                "Current thread: {} (ID: {}), Timestamp: {}. " +
                "This prevents the server from hanging due to infinite queue growth. " +
                "The physics stage is running slower than the game stage.",
                dropped, dropCount, currentThread.getName(), currentThread.getId(), now);
            queue.clear();
        }
    }

    /**
     * Redirect Thread.sleep calls in FQ.postTickGame() to prevent blocking the main thread.
     * When the game frame queue is full, VS2 would sleep for 1 second to "wait" for the physics thread.
     * But since we're actively dropping frames to prevent queue growth, this sleep is unnecessary
     * and would only make the server more unresponsive.
     */
    @Redirect(
        method = "postTickGame",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Thread;sleep(J)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void redirectSleep(long millis) {
        // Only skip sleep if our mixin is initialized and queue is backpressured
        if (initialized) {
            try {
                ConcurrentLinkedQueue<?> queue = getQueue(this);
                if (queue != null && queue.size() > 100) {
                    // Queue is backpressured, skip sleep to keep server responsive
                    LOGGER.debug("[ValkyrienJS] Skipping Thread.sleep({}ms) in postTickGame because " +
                        "game frame queue is backpressured (size={})", millis, queue.size());
                    return;
                }
            } catch (Exception e) {
                // If anything goes wrong, fall through to normal sleep
            }
        }
        
        // Normal case: call the original sleep
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
