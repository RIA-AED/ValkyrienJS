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
 * Mixin for FH class - handles physics frame queue backpressure
 * FH.e is ConcurrentLinkedQueue<FL> - the physics frame queue
 * When the physics thread stalls and then resumes, this queue accumulates frames.
 * This mixin:
 * 1. Clears the backlog when severely backpressured to restore real-time sync
 * 2. Prevents Thread.sleep from blocking the physics thread
 * 3. Throttles log output to prevent console spam
 */
@Mixin(value = org.valkyrienskies.core.impl.shadow.FH.class, remap = false)
public class MixinFH {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");
    private static final String FIELD_NAME = "e"; // Physics frame queue field: ConcurrentLinkedQueue<FL>
    private static Field queueField;
    private static boolean initialized = false;
    private static int clearCount = 0;
    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 10000; // Log at most every 10 seconds
    private static long lastWarnLogTime = 0;
    private static final long WARN_LOG_INTERVAL_MS = 30000; // Warn at most every 30 seconds

    static {
        LOGGER.info("[ValkyrienJS] MixinFH static initializer started");
        try {
            queueField = org.valkyrienskies.core.impl.shadow.FH.class.getDeclaredField(FIELD_NAME);
            queueField.setAccessible(true);
            initialized = true;
            LOGGER.info("[ValkyrienJS] MixinFH initialized successfully, field '{}' found: type={}", 
                FIELD_NAME, queueField.getType().getName());
        } catch (NoSuchFieldException e) {
            LOGGER.error("[ValkyrienJS] Failed to find field '{}' in FH class", FIELD_NAME, e);
        } catch (Exception e) {
            LOGGER.error("[ValkyrienJS] Unexpected error in MixinFH static initializer", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentLinkedQueue<Object> getQueue(Object instance) {
        if (queueField == null) return null;
        try {
            Object value = queueField.get(instance);
            if (value instanceof ConcurrentLinkedQueue) {
                return (ConcurrentLinkedQueue<Object>) value;
            } else {
                LOGGER.warn("[ValkyrienJS] Field '{}' is not a ConcurrentLinkedQueue, actual type: {}",
                    FIELD_NAME, value != null ? value.getClass().getName() : "null");
                return null;
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("[ValkyrienJS] Failed to access field '{}'", FIELD_NAME, e);
            return null;
        }
    }

    /**
     * Inject into FH.a() - the method that processes physics frames.
     * This is called on the physics thread. We check if the physics frame queue
     * has accumulated too many frames (indicating the physics thread was stalled)
     * and clear it if necessary.
     */
    @Inject(
        method = "a",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void onPhysicsFrameProcess(CallbackInfo ci) {
        if (!initialized) {
            LOGGER.debug("[ValkyrienJS] MixinFH not initialized, skipping");
            return;
        }

        ConcurrentLinkedQueue<?> queue = getQueue(this);
        if (queue == null) {
            LOGGER.debug("[ValkyrienJS] MixinFH queue is null");
            return;
        }

        int size = queue.size();
        long now = System.currentTimeMillis();

        // Throttled logging for queue size monitoring (every 10 seconds)
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            lastLogTime = now;
            if (size > 0) {
                LOGGER.info("[ValkyrienJS] Physics frame queue status: size={}, thread={}",
                    size, Thread.currentThread().getName());
            }
        }

        // Clear queue if severely backpressured
        if (size > 50) {
            clearCount++;
            Thread currentThread = Thread.currentThread();

            // Only log warning every 30 seconds to prevent console spam
            if (now - lastWarnLogTime > WARN_LOG_INTERVAL_MS) {
                lastWarnLogTime = now;
                LOGGER.warn("[ValkyrienJS] Physics thread detected severe physics frame queue backpressure! " +
                    "Clearing {} backed-up physics frames (clearCount={}). " +
                    "Current thread: {} (ID: {}), Timestamp: {}. " +
                    "This indicates the physics stage was stalled and is now resuming. " +
                    "The queue will be cleared to restore real-time synchronization.",
                    size, clearCount, currentThread.getName(), currentThread.getId(), now);
            } else {
                // Debug log for intermediate clears
                LOGGER.debug("[ValkyrienJS] Clearing {} physics frames (clearCount={}, totalDrops={})",
                    size, clearCount, clearCount);
            }

            queue.clear();
        }
    }

    /**
     * Redirect Thread.sleep calls in FH.a(FL) to prevent blocking the physics thread.
     * When the physics frame queue is full, VS2 would sleep for 1 second.
     * But since we're actively clearing frames, this sleep is unnecessary.
     */
    @Redirect(
        method = "a",
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
                    // Queue is backpressured, skip sleep to keep physics thread responsive
                    LOGGER.debug("[ValkyrienJS] Skipping Thread.sleep({}ms) because " +
                        "physics frame queue is backpressured (size={})", millis, queue.size());
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
