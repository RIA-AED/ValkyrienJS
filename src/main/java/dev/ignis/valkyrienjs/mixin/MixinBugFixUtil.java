package dev.ignis.valkyrienjs.mixin;

import dev.ignis.valkyrienjs.Config;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.util.BugFixUtil;

@Mixin(BugFixUtil.class)
public class MixinBugFixUtil {

    @Inject(
            method = "isCollisionBoxTooBig",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onIsCollisionBoxTooBig(AABB aabb, CallbackInfoReturnable<Boolean> cir) {
        double volume = aabb.getXsize() * aabb.getYsize() * aabb.getZsize();
        cir.setReturnValue(volume > Config.maxCollisionBoxVolume);
        cir.cancel();
    }
}
