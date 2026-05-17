package dev.ignis.valkyrienjs.mixin;

import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes a server hang caused by VS2's MixinEntitySectionStorage.shipSections() transforming
 * an AABB to ship space, which can produce extremely large or NaN coordinates.
 * This causes LongAVLTreeSet.subSet().iterator() to iterate over a massive range of section IDs,
 * effectively freezing the server thread and triggering a ServerHangWatchdog crash.
 *
 * This mixin adds a sanity check at the HEAD of forEachAccessibleNonEmptySection to bail out
 * if the AABB is invalid (NaN/Infinity) or unreasonably large.
 */
@Mixin(value = EntitySectionStorage.class, priority = 900)
public class MixinEntitySectionStorageFix<T extends EntityAccess> {

    @Inject(
            method = "forEachAccessibleNonEmptySection",
            at = @At("HEAD"),
            cancellable = true
    )
    private void valkyrienjs$preventHangOnInvalidAABB(AABB aabb, AbortableIterationConsumer<EntitySection<T>> consumer, CallbackInfo ci) {
        // Check for NaN or Infinity in AABB coordinates
        if (Double.isNaN(aabb.minX) || Double.isNaN(aabb.minY) || Double.isNaN(aabb.minZ) ||
                Double.isNaN(aabb.maxX) || Double.isNaN(aabb.maxY) || Double.isNaN(aabb.maxZ) ||
                Double.isInfinite(aabb.minX) || Double.isInfinite(aabb.minY) || Double.isInfinite(aabb.minZ) ||
                Double.isInfinite(aabb.maxX) || Double.isInfinite(aabb.maxY) || Double.isInfinite(aabb.maxZ)) {
            ci.cancel();
            return;
        }

        // Check if AABB is unreasonably large (likely a bad ship transform result)
        // Normal entity searches should never need an AABB larger than ~1000 blocks on any axis
        double maxSize = 1000.0;
        if (aabb.getXsize() > maxSize || aabb.getYsize() > maxSize || aabb.getZsize() > maxSize) {
            ci.cancel();
        }
    }
}
