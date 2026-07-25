package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(value = org.valkyrienskies.core.impl.shadow.FN.class, remap = false)
public class MixinFN {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");
    private static final String FIELD_NAME = "b";
    private static Field queueField;
    private static boolean initialized = false;

    static {
        try {
            queueField = org.valkyrienskies.core.impl.shadow.FN.class.getDeclaredField(FIELD_NAME);
            queueField.setAccessible(true);
            initialized = true;
            LOGGER.info("[ValkyrienJS] MixinFN initialized successfully, field 'b' found");
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

    @Inject(method = "a", at = @At("HEAD"), remap = false)
    private void onPhysTickStart(CallbackInfo ci) {
        if (!initialized) return;
        ConcurrentLinkedQueue<?> queue = getQueue(this);
        if (queue != null && queue.size() > 100) {
            int droppedCount = queue.size();
            long currentTime = System.currentTimeMillis();
            Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Physics thread detected severe game frame queue backpressure! Dropping {} backed-up game frames. Current thread: {} (ID: {}), Timestamp: {}. This indicates the physics stage is running slower than the game stage. The queue will be cleared to restore real-time synchronization.", droppedCount, currentThread.getName(), currentThread.getId(), currentTime);
            queue.clear();
        }
    }
}
