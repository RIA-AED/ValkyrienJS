package dev.ignis.valkyrienjs.mixin;

import dev.ignis.valkyrienjs.feature.blocklimit.BlockLimitAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public class BlockOnPlaceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("ValkyrienJS/BlockLimit");

    @Inject(method = "onPlace",
            at = @At("RETURN"))
    private void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving, CallbackInfo ci) {
        LOGGER.debug("[BlockLimit] onPlace called at {}: state={}, oldState={}, isMoving={}, sameBlock={}",
                pos, state.getBlock().getDescriptionId(), oldState.getBlock().getDescriptionId(),
                isMoving, state.is(oldState.getBlock()));

        // 只在方块真正改变时计数（避免重复计数）
        if (!state.is(oldState.getBlock())) {
            LOGGER.debug("[BlockLimit] Block changed, calling onBlockPlaced");
            BlockLimitAPI.onBlockPlaced(level, pos, state);
        } else {
            LOGGER.debug("[BlockLimit] Block not changed, skipping");
        }
    }
}
