package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mixin for FH class - handles physics frame queue backpressure
 * FH.e is ConcurrentLinkedQueue<FL> - the physics frame queue
 * When the physics thread stalls and then resumes, this queue accumulates frames.
 * This mixin clears the backlog to restore real-time sync.
 */
@Mixin(value = org.valkyrienskies.core.impl.shadow.FH.class, remap = false)
public class MixinFH {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");
    private static final String FIELD_NAME = "e"; // Physics frame queue field: ConcurrentLinkedQueue<FL>
    private static Field queueField;
    private static boolean initialized = false;
    private static int clearCount = 0;
    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 5000; // Log at most every 5 seconds

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

        // Throttled logging for queue size monitoring
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

            LOGGER.warn("[ValkyrienJS] Physics thread detected severe physics frame queue backpressure! " +
                "Clearing {} backed-up physics frames (clearCount={}). " +
                "Current thread: {} (ID: {}), Timestamp: {}. " +
                "This indicates the physics stage was stalled and is now resuming. " +
                "The queue will be cleared to restore real-time synchronization.",
                size, clearCount, currentThread.getName(), currentThread.getId(), now);

            queue.clear();
        }
    }
}
