package dev.ignis.valkyrienjs.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

/**
 * 修复射线检测导致的 chunk 加载死锁问题
 * 在调用 vanillaClip 前检查 chunk 是否已加载
 */
@Mixin(RaycastUtilsKt.class)
public class RaycastUtilsMixin {

    /**
     * 在 vanillaClip 方法执行前检查 chunk 加载状态
     * 如果射线穿过未加载的 chunk，直接返回 MISS 结果避免死锁
     */
    @Inject(
        method = "vanillaClip",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void preVanillaClip(
        BlockGetter blockGetter,
        ClipContext context,
        CallbackInfoReturnable<BlockHitResult> cir
    ) {
        // 只对 Level 类型进行检查
        if (!(blockGetter instanceof Level level)) {
            return;
        }

        // 获取射线起点和终点
        var from = context.getFrom();
        var to = context.getTo();

        // 计算射线经过的区块范围
        int minChunkX = Math.min(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4;
        int maxChunkX = Math.max(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4;
        int minChunkZ = Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4;
        int maxChunkZ = Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4;

        // 检查射线经过的所有 chunk 是否已加载
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    // 如果有任何 chunk 未加载，返回 MISS 结果
                    var missVec = from.subtract(to);
                    var missResult = BlockHitResult.miss(
                        to,
                        net.minecraft.core.Direction.getNearest(missVec.x, missVec.y, missVec.z),
                        BlockPos.containing(to)
                    );
                    cir.setReturnValue(missResult);
                    return;
                }
            }
        }
    }
}
