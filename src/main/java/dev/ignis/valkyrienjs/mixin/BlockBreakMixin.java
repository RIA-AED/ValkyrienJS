package dev.ignis.valkyrienjs.mixin;

import dev.ignis.valkyrienjs.feature.blocklimit.BlockLimitAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public class BlockBreakMixin {

    @Inject(method = "onRemove",
            at = @At("HEAD"))
    private void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                          boolean isMoving, CallbackInfo ci) {
        if (!state.is(newState.getBlock())) {
            BlockLimitAPI.onBlockRemoved(level, pos, state);
        }
    }
}
