package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(value = org.valkyrienskies.core.impl.shadow.FN.class, remap = false)
public class MixinFN {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");
    private static final String FIELD_NAME = "b";
    private static Field queueField;
    private static boolean initialized = false;
    private static int callCount = 0;

    static {
        LOGGER.info("[ValkyrienJS] MixinFN static initializer started");
        try {
            queueField = org.valkyrienskies.core.impl.shadow.FN.class.getDeclaredField(FIELD_NAME);
            queueField.setAccessible(true);
            initialized = true;
            LOGGER.info("[ValkyrienJS] MixinFN initialized successfully, field 'b' found: {}", queueField);
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
            ConcurrentLinkedQueue<Object> queue = (ConcurrentLinkedQueue<Object>) queueField.get(instance);
            if (queue == null) {
                LOGGER.warn("[ValkyrienJS] Queue is null for instance: {}", instance);
            }
            return queue;
        } catch (IllegalAccessException e) {
            LOGGER.error("[ValkyrienJS] Failed to access field '{}'", FIELD_NAME, e);
            return null;
        }
    }

    // 注入到物理线程 tick 方法 a(double, boolean) 开头
    // 使用更宽松的匹配方式
    @Inject(
        method = "a(DZ)Lorg/valkyrienskies/core/impl/shadow/FL;",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void onPhysTickStart(double dt, boolean isLastTick, CallbackInfoReturnable<org.valkyrienskies.core.impl.shadow.FL> cir) {
        callCount++;
        if (callCount <= 5) {
            LOGGER.info("[ValkyrienJS] onPhysTickStart called! dt={}, isLastTick={}, callCount={}", dt, isLastTick, callCount);
        }
        
        if (!initialized) {
            if (callCount <= 5) {
                LOGGER.warn("[ValkyrienJS] Not initialized, skipping queue check");
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
        if (size > 100) {
            int droppedCount = size;
            long currentTime = System.currentTimeMillis();
            Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Physics thread detected severe game frame queue backpressure! Dropping {} backed-up game frames. Current thread: {} (ID: {}), Timestamp: {}. This indicates the physics stage is running slower than the game stage. The queue will be cleared to restore real-time synchronization.", 
                droppedCount, currentThread.getName(), currentThread.getId(), currentTime);
            queue.clear();
        }
    }

    // 注入到游戏帧添加方法 a(FG) 开头
    @Inject(
        method = "a(Lorg/valkyrienskies/core/impl/shadow/FG;)V",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void onGameFrameAdd(org.valkyrienskies.core.impl.shadow.FG frame, CallbackInfo ci) {
        LOGGER.info("[ValkyrienJS] onGameFrameAdd called!");
        
        if (!initialized) {
            LOGGER.warn("[ValkyrienJS] Not initialized in onGameFrameAdd");
            return;
        }
        
        ConcurrentLinkedQueue<?> queue = getQueue(this);
        if (queue == null) {
            LOGGER.warn("[ValkyrienJS] Queue is null in onGameFrameAdd");
            return;
        }
        
        int size = queue.size();
        if (size >= 800) {
            Thread currentThread = Thread.currentThread();
            LOGGER.warn("[ValkyrienJS] Game frame queue backpressure detected! Current queue size: {} (threshold: 800). Current thread: {} (ID: {}). This indicates the physics stage is running slower than the game stage, causing game frames to accumulate in the queue.", 
                size, currentThread.getName(), currentThread.getId());
        }
    }

    // 替换原来的 warn 日志调用 - 在 a(FG) 方法中
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "a(Lorg/valkyrienskies/core/impl/shadow/FG;)V",
        at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;)V"),
        remap = false,
        require = 0
    )
    private String modifyWarnMessage(String originalMessage) {
        LOGGER.info("[ValkyrienJS] modifyWarnMessage called! Original: {}", originalMessage);
        
        if (!initialized) {
            return originalMessage;
        }
        
        ConcurrentLinkedQueue<?> queue = getQueue(this);
        int queueSize = queue != null ? queue.size() : -1;
        Thread currentThread = Thread.currentThread();
        
        String enhancedMessage = String.format(
            "[ValkyrienJS] Game frame queue backpressure detected! Current queue size: %d (threshold: 800). Current thread: %s (ID: %d). This indicates the physics stage is running slower than the game stage, causing game frames to accumulate in the queue. Original message: %s",
            queueSize, currentThread.getName(), currentThread.getId(), originalMessage
        );
        
        LOGGER.warn(enhancedMessage);
        return enhancedMessage;
    }
}
