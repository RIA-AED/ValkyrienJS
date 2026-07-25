package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mixin for FN class - handles game frame queue backpressure
 * FN.b is ConcurrentLinkedQueue<FG> - the game frame queue (received from game thread)
 * When physics thread is slower than game thread, game frames accumulate here.
 * This mixin drops old frames to restore real-time synchronization.
 */
@Mixin(value = org.valkyrienskies.core.impl.shadow.FN.class, remap = false)
public class MixinFN {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");
    private static final String FIELD_NAME = "b"; // Game frame queue: ConcurrentLinkedQueue<FG>
    private static Field queueField;
    private static boolean initialized = false;
    private static int callCount = 0;
    private static int dropCount = 0;
    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 5000; // Log at most every 5 seconds

    static {
        LOGGER.info("[ValkyrienJS] MixinFN static initializer started");
        try {
            queueField = org.valkyrienskies.core.impl.shadow.FN.class.getDeclaredField(FIELD_NAME);
            queueField.setAccessible(true);
            initialized = true;
            LOGGER.info("[ValkyrienJS] MixinFN initialized successfully, field '{}' found: type={}",
                FIELD_NAME, queueField.getType().getName());
        } catch (NoSuchFieldException e) {
            LOGGER.error("[ValkyrienJS] Failed to find field '{}' in FN class", FIELD_NAME, e);
        } catch (Exception e) {
            LOGGER.error("[ValkyrienJS] Unexpected error in MixinFN static initializer", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentLinkedQueue<Object> getQueue(Object instance) {
        if (queueField == null) {
            LOGGER.warn("[ValkyrienJS] queueField is null!");
            return null;
        }
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
     * Inject into FN.a(double, boolean) - the physics thread tick method.
     * This is called on the physics thread. We check if the game frame queue
     * has accumulated too many frames (indicating physics is slower than game)
     * and drop old frames if necessary.
     */
    @Inject(
        method = "a(DZ)Lorg/valkyrienskies/core/impl/shadow/FL;",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void onPhysTickStart(double dt, boolean isLastTick, CallbackInfoReturnable<org.valkyrienskies.core.impl.shadow.FL> cir) {
        callCount++;
        if (callCount <= 3) {
            LOGGER.info("[ValkyrienJS] onPhysTickStart called! dt={}, isLastTick={}, callCount={}", dt, isLastTick, callCount);
        }

        if (!initialized) {
            if (callCount <= 5) {
                LOGGER.warn("[ValkyrienJS] MixinFN not initialized, skipping queue check");
            }
            return;
        }

        ConcurrentLinkedQueue<?> queue = getQueue(this);
        if (queue == null) {
            if (callCount <= 5) {
                LOGGER.warn("[ValkyrienJS] Queue is null in onPhysTickStart");
            }
            return;
        }

        int size = queue.size();
        long now = System.currentTimeMillis();

        // Throttled logging for queue status monitoring
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            lastLogTime = now;
            if (size > 0) {
                LOGGER.info("[ValkyrienJS] Game frame queue status: size={}, thread={}, drops={}",
                    size, Thread.currentThread().getName(), dropCount);
            }
        }

        // Drop old frames if severely backpressured
        if (size > 100) {
            dropCount++;
            int droppedCount = size;
            Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Physics thread detected severe game frame queue backpressure! " +
                "Dropping {} backed-up game frames (totalDrops={}). " +
                "Current thread: {} (ID: {}), Timestamp: {}. " +
                "This indicates the physics stage is running slower than the game stage. " +
                "The queue will be cleared to restore real-time synchronization.",
                droppedCount, dropCount, currentThread.getName(), currentThread.getId(), now);
            queue.clear();
        }
    }

    /**
     * Inject into FN.a(FG) - the game frame add method.
     * This is called when the game thread adds a frame to the queue.
     * We log queue size when it gets large.
     */
    @Inject(
        method = "a(Lorg/valkyrienskies/core/impl/shadow/FG;)V",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void onGameFrameAdd(org.valkyrienskies.core.impl.shadow.FG frame, CallbackInfo ci) {
        if (!initialized) {
            return;
        }

        ConcurrentLinkedQueue<?> queue = getQueue(this);
        if (queue == null) {
            return;
        }

        int size = queue.size();
        if (size >= 800) {
            Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Game frame queue backpressure detected! " +
                "Current queue size: {} (threshold: 800). " +
                "Current thread: {} (ID: {}). " +
                "This indicates the physics stage is running slower than the game stage, " +
                "causing game frames to accumulate in the queue.",
                size, currentThread.getName(), currentThread.getId());
        }
    }
}
