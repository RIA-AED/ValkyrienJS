package dev.ignis.valkyrienjs.mixin;

import dev.ignis.valkyrienjs.feature.player.ShipPlayerPositionHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerPositionRestore {
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void importLegacyVSReconnectAnchor(CompoundTag playerTag, CallbackInfo callbackInfo) {
        ShipPlayerPositionHandler.importLegacyReconnectAnchor((ServerPlayer) (Object) this, playerTag);
    }
}
