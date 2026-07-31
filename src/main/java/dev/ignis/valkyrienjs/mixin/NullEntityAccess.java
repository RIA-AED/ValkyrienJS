package dev.ignis.valkyrienjs.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * A dummy EntityAccess implementation used to safely skip null entities in EntitySection iteration.
 * Its bounding box is empty and positioned at origin, so it will never intersect with any real AABB.
 */
public enum NullEntityAccess implements EntityAccess {
    INSTANCE;

    private static final AABB EMPTY_AABB = new AABB(0, 0, 0, 0, 0, 0);

    @Override
    public int getId() {
        return -1;
    }

    @Override
    public UUID getUUID() {
        return new UUID(0, 0);
    }

    @Override
    public BlockPos blockPosition() {
        return BlockPos.ZERO;
    }

    @Override
    public AABB getBoundingBox() {
        return EMPTY_AABB;
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback callback) {
    }

    @Override
    public Stream<? extends EntityAccess> getSelfAndPassengers() {
        return Stream.of(this);
    }

    @Override
    public Stream<? extends EntityAccess> getPassengersAndSelf() {
        return Stream.of(this);
    }

    @Override
    public void setRemoved(Entity.RemovalReason reason) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }
}
