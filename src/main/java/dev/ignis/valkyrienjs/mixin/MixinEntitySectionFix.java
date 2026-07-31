package dev.ignis.valkyrienjs.mixin;

import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

/**
 * Fixes NullPointerException in EntitySection.getEntities() caused by VS2's shipyard entity management.
 * VS2's MixinEntitySection may leave null entity references in the ClassInstanceMultiMap storage.
 * When HopperBlockEntity (or other blocks) iterate over entities in a section, they encounter null
 * entities and crash when calling getBoundingBox() on them.
 *
 * This mixin uses ModifyVariable to replace null entities with a safe dummy that has an empty bounding box,
 * causing the intersection check to fail and the entity to be skipped naturally.
 */
@Mixin(value = EntitySection.class, priority = 900)
public class MixinEntitySectionFix<T extends EntityAccess> {

    /**
     * Intercept the entity variable after it's assigned from the iterator.
     * If null, return a dummy EntityAccess whose bounding box won't intersect with any real AABB.
     */
    @ModifyVariable(
            method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At(value = "STORE", ordinal = 0),
            ordinal = 0
    )
    private EntityAccess valkyrienjs$skipNullEntity(EntityAccess entity) {
        if (entity == null) {
            // Return a dummy with an empty bounding box at origin
            // The intersects() check will fail, so this "entity" is skipped naturally
            return NullEntityAccess.INSTANCE;
        }
        return entity;
    }

    /**
     * Same protection for the typed overload.
     */
    @ModifyVariable(
            method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At(value = "STORE", ordinal = 0),
            ordinal = 0
    )
    private EntityAccess valkyrienjs$skipNullEntityTyped(EntityAccess entity) {
        if (entity == null) {
            return NullEntityAccess.INSTANCE;
        }
        return entity;
    }
}
