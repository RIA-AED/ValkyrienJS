package dev.ignis.valkyrienjs.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

/**
 * CBC normally lets large projectiles penetrate blocks and lets impact fuzes detonate from the
 * returned impact result. A shipyard block must instead absorb the projectile without invoking
 * CBC's normal block-impact path: no block destruction, penetration, impact explosion, or fuze.
 */
@Mixin(value = {AbstractBigCannonProjectile.class, AbstractAutocannonProjectile.class}, remap = false)
public abstract class MixinAbstractCannonProjectile {

    @Inject(
            method = "calculateBlockPenetration",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void valkyrienjs$absorbProjectileOnShip(
            ProjectileContext projectileContext,
            BlockState state,
            BlockHitResult hitResult,
            CallbackInfoReturnable<AbstractCannonProjectile.ImpactResult> cir
    ) {
        AbstractCannonProjectile projectile = (AbstractCannonProjectile) (Object) this;
        Level level = projectile.level();
        if (VSGameUtilsKt.getShipManagingPos(level, hitResult.getBlockPos()) == null) {
            return;
        }

        projectile.discard();
        cir.setReturnValue(new AbstractCannonProjectile.ImpactResult(
                AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP,
                true
        ));
    }
}
