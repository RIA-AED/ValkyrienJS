package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = org.valkyrienskies.core.impl.shadow.FJ.class, remap = false)
public class MixinFJ {

    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/PhysicsBackpressure");

    @Shadow
    public final java.util.concurrent.ConcurrentLinkedQueue<org.valkyrienskies.core.impl.shadow.FC> d = null;

    /**
     * 方案A：物理线程消费端丢弃积压帧
     * 在 FJ.a(double, boolean) 方法开头注入，如果队列积压过多则直接清空
     */
    @Inject(
            method = "a(DZ)Lorg/valkyrienskies/core/impl/shadow/FH;",
            at = @At("HEAD"),
            remap = false
    )
    private void onPhysTickStart(double dt, boolean isLastTick, CallbackInfoReturnable<org.valkyrienskies.core.impl.shadow.FH> cir) {
        if (d != null && d.size() > 100) {
            int droppedCount = d.size();
            long currentTime = System.currentTimeMillis();
            Thread currentThread = Thread.currentThread();

            LOGGER.warn(
                    "[ValkyrienJS] Physics thread detected severe game frame queue backpressure! " +
                            "Dropping {} backed-up game frames. " +
                            "Current thread: {} (ID: {}), " +
                            "Timestamp: {}. " +
                            "This indicates the physics stage is running slower than the game stage. " +
                            "The queue will be cleared to restore real-time synchronization.",
                    droppedCount,
                    currentThread.getName(),
                    currentThread.getId(),
                    currentTime
            );

            d.clear();
        }
    }

    /**
     * 增强原有的队列积压日志：将简单的 "Too many game frames..." 替换为包含详细信息的日志
     * 在 FJ.a(FC) 方法中，当队列 size >= 800 时，原代码会打印 warn 并 sleep 1000ms
     * 这里替换 Logger.warn(String) 调用，输出更详细的积压信息
     */
    @Redirect(
            method = "a(Lorg/valkyrienskies/core/impl/shadow/FC;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;)V",
                    ordinal = 0
            ),
            remap = false
    )
    private void redirectQueueFullLog(org.apache.logging.log4j.Logger logger, String originalMessage) {
        int queueSize = d != null ? d.size() : -1;
        Thread currentThread = Thread.currentThread();

        LOGGER.warn(
                "[ValkyrienJS] Game frame queue backpressure detected! " +
                        "Current queue size: {} (threshold: 800). " +
                        "Current thread: {} (ID: {}). " +
                        "This indicates the physics stage is running slower than the game stage, " +
                        "causing game frames to accumulate in the queue. " +
                        "The producer thread will sleep 1000ms to allow the consumer to catch up. " +
                        "Original message: {}",
                queueSize,
                currentThread.getName(),
                currentThread.getId(),
                originalMessage
        );
    }
}
