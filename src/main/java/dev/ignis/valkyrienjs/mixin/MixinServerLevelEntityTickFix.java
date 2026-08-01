package dev.ignis.valkyrienjs.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.NoSuchElementException;

/**
 * Fixes a NoSuchElementException crash in PersistentEntitySectionManager.entityTick()
 * caused by VS2 modifying entity sections (removing shipyard entities) during iteration.
 *
 * When ServerLevel.tick() calls entityManager.tick(), the entity manager iterates over
 * all sections to collect entities. If VS2 concurrently removes entities from sections
 * (e.g., when a ship unloads), the ArrayList iterator throws NoSuchElementException
 * because its internal cursor exceeds the now-shrunken list size.
 *
 * This mixin wraps the entityManager.tick() call in a try-catch to prevent the server
 * from crashing. The entity tick will simply be skipped for this frame.
 */
@Mixin(ServerLevel.class)
public class MixinServerLevelEntityTickFix {

    private static final Logger LOGGER = LoggerFactory.getLogger("ValkyrienJS/EntityTickFix");

    @Redirect(
            method = "m_8643_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;m_157554_()V"
            ),
            require = 0
    )
    private void safeEntityTick(PersistentEntitySectionManager<?> manager) {
        try {
            manager.m_157554_();
        } catch (NoSuchElementException e) {
            LOGGER.warn("[ValkyrienJS] Caught NoSuchElementException during entity tick, " +
                    "likely caused by VS2 modifying entity sections during iteration. " +
                    "Skipping entity tick for this frame to prevent crash.", e);
        }
    }
}
