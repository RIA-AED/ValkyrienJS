package dev.ignis.valkyrienjs.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = org.valkyrienskies.core.impl.shadow.CS.class, remap = false)
public class MixinCS {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienJS/ShipUnload");

    /**
     * @author ValkyrienJS
     * @reason Prevent ships from unloading when no players are nearby.
     * This fixes the issue where ships disappear on servers without players.
     */
    @Overwrite
    public boolean getShouldUnload() {
        return false;
    }
}
