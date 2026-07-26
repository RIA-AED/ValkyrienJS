package dev.ignis.valkyrienjs.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes a server crash caused by VS2's ship coordinate system corrupting entity positions.
 * When FallingBlockEntity (sand/gravel) spawns near a ship with broken transforms,
 * its position can become NaN/Infinity, causing SectionPos.asLong() to produce an
 * invalid section key that crashes Long2ObjectOpenHashMap with ArrayIndexOutOfBoundsException.
 *
 * This mixin intercepts addEntity (m_157538_) and rejects entities with invalid positions
 * before they can corrupt the entity section storage.
 */
@Mixin(value = PersistentEntitySectionManager.class, priority = 900)
public class MixinPersistentEntitySectionManagerFix<T extends EntityAccess> {

    private static final Logger LOGGER = LoggerFactory.getLogger("ValkyrienJS/EntitySectionFix");

    @Inject(
            method = "m_157538_",
            at = @At("HEAD"),
            cancellable = true
    )
    private void valkyrienjs$preventCrashOnInvalidPosition(T entity, boolean isWorldGen, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) {
            LOGGER.debug("Rejected null entity from being added to world");
            cir.setReturnValue(false);
            return;
        }

        // EntityAccess doesn't have getX/Y/Z, but Entity does
        // Check block position first (available on EntityAccess)
        BlockPos blockPos = entity.blockPosition();
        if (blockPos == null) {
            LOGGER.debug("Rejected entity with null blockPosition: {}", entity);
            cir.setReturnValue(false);
            return;
        }

        int bx = blockPos.getX();
        int by = blockPos.getY();
        int bz = blockPos.getZ();

        // Check for Integer.MIN_VALUE which can result from bad floor(NaN/Infinity)
        // Also check for unreasonably large values
        if (by < -1000 || by > 1000 || Math.abs(bx) > 30000000 || Math.abs(bz) > 30000000) {
            LOGGER.debug("Rejected entity with out-of-bounds block position [{}, {}, {}]: {}", bx, by, bz, entity);
            cir.setReturnValue(false);
            return;
        }

        // For Entity instances, also check the double position for NaN/Infinity
        if (entity instanceof Entity realEntity) {
            double x = realEntity.getX();
            double y = realEntity.getY();
            double z = realEntity.getZ();

            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
                    Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
                LOGGER.debug("Rejected entity with NaN/Infinity position [{}, {}, {}]: {}", x, y, z, entity);
                cir.setReturnValue(false);
            }
        }
    }
}
