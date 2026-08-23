package dev.ignis.valkyrienjs.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.munitions.fuzes.ProximityFuzeItem;

/**
 * The proximity fuze scans ahead of the projectile with Level.clip rays; VS2 makes those rays
 * include ships, so a shell flying near a VS ship detonates mid-air. Shipyard blocks are meant
 * to be transparent to cannon fire (see MixinAbstractCannonProjectile), so a scan ray that hits
 * a ship block reports a miss instead of triggering detonation.
 *
 * <p>Entity proximity detection is intentionally untouched: entities ride ships at their world
 * position and remain legitimate targets.
 */
@Mixin(value = ProximityFuzeItem.class, remap = false)
public abstract class MixinProximityFuze {

    // Runtime target is the SRG name: Level.clip(ClipContext)
    @Redirect(
            method = "onProjectileClip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_45547_(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
                    remap = false
            ),
            remap = false
    )
    private BlockHitResult valkyrienjs$proximityScanIgnoresShips(Level level, ClipContext context) {
        BlockHitResult result = level.clip(context);
        if (result.getType() != HitResult.Type.MISS
                && VSGameUtilsKt.getShipManagingPos(level, result.getBlockPos()) != null) {
            return BlockHitResult.miss(result.getLocation(), Direction.UP, result.getBlockPos());
        }
        return result;
    }
}
