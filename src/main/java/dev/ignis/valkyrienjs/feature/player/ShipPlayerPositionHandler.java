package dev.ignis.valkyrienjs.feature.player;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps logout and respawn positions attached to a VS ship instead of to stale world coordinates.
 *
 * SimpleLogin's protect_coord plugin stores an absolute world position and reapplies it after
 * authentication. That position becomes empty air after the ship moves. VS also only writes its
 * relative reconnect position while its short-lived dragging flag is active. This handler records
 * a relative position before other logout handlers move the player, waits for authentication, and
 * reapplies it after the target ship has loaded.
 */
public final class ShipPlayerPositionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShipPlayerPositionHandler.class);

    private static final String DATA_ROOT = "ValkyrienJSShipPositions";
    private static final String LOGOUT_ANCHOR = "Logout";
    private static final String SPAWN_ANCHOR = "Spawn";
    private static final int MAX_PLACEMENT_TICKS = 200;
    private static final int POST_AUTH_DELAY_TICKS = 5;
    private static final double SHIP_BOUNDS_MARGIN = 4.0;

    private final Map<UUID, PendingPlacement> pendingPlacements = new HashMap<>();

    private static Method simpleLoginInstanceMethod;
    private static Method simpleLoginStatusMethod;
    private static boolean simpleLoginReflectionInitialized;
    private static boolean simpleLoginReflectionWarningLogged;

    /** Capture before SimpleLogin's NORMAL-priority logout handler teleports the player to spawn. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        pendingPlacements.remove(player.getUUID());
        Ship ship = findCurrentShip(player);
        if (ship == null) {
            removeAnchor(player, LOGOUT_ANCHOR);
            return;
        }

        Vector3d localPosition = ship.getWorldToShip().transformPosition(
                new Vector3d(player.getX(), player.getY(), player.getZ()));
        writeAnchor(player, LOGOUT_ANCHOR, new ShipAnchor(
                ship.getId(), localPosition.x, localPosition.y, localPosition.z, false, false));
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ShipAnchor anchor = readAnchor(player, LOGOUT_ANCHOR);
        if (anchor != null) {
            pendingPlacements.put(player.getUUID(), PendingPlacement.forLogin(anchor));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos spawnPos = event.getNewSpawn();
        ServerLevel level = player.getServer().getLevel(event.getSpawnLevel());
        if (spawnPos == null || level == null) {
            removeAnchor(player, SPAWN_ANCHOR);
            return;
        }

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, spawnPos);
        if (ship == null) {
            removeAnchor(player, SPAWN_ANCHOR);
            return;
        }

        writeAnchor(player, SPAWN_ANCHOR, new ShipAnchor(
                ship.getId(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), true, event.isForced()));
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        ShipAnchor anchor = readAnchor(player, SPAWN_ANCHOR);
        if (anchor == null) {
            return;
        }
        if (!spawnAnchorStillSelected(player, anchor)) {
            removeAnchor(player, SPAWN_ANCHOR);
            return;
        }
        pendingPlacements.put(player.getUUID(), PendingPlacement.forRespawn(anchor));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingPlacements.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, PendingPlacement>> iterator = pendingPlacements.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingPlacement> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isRemoved()) {
                iterator.remove();
                continue;
            }

            PendingPlacement pending = entry.getValue();
            if (pending.waitForAuthentication) {
                if (!isAuthenticationComplete(player)) {
                    continue;
                }
                if (pending.authenticationCompletedTick < 0) {
                    pending.authenticationCompletedTick = server.getTickCount();
                    continue;
                }
                if (server.getTickCount() - pending.authenticationCompletedTick < POST_AUTH_DELAY_TICKS) {
                    continue;
                }
            }

            PlacementResult result = placeOnShip(player, pending);
            if (result == PlacementResult.COMPLETE) {
                if (pending.waitForAuthentication) {
                    removeAnchor(player, LOGOUT_ANCHOR);
                }
                iterator.remove();
            } else if (result == PlacementResult.INVALID) {
                removeAnchor(player, pending.waitForAuthentication ? LOGOUT_ANCHOR : SPAWN_ANCHOR);
                iterator.remove();
            } else if (++pending.placementTicks >= MAX_PLACEMENT_TICKS) {
                LOGGER.warn("Timed out restoring {} to VS ship {}", player.getGameProfile().getName(), pending.anchor.shipId);
                iterator.remove();
            }
        }
    }

    /**
     * Imports VS's existing reconnect fields for players whose data predates this fix. A newly
     * captured anchor always wins because VS's old fields can already contain SimpleLogin's lobby
     * position.
     */
    public static void importLegacyReconnectAnchor(ServerPlayer player, CompoundTag playerTag) {
        if (readAnchor(player, LOGOUT_ANCHOR) != null || !playerTag.contains("LastShipId", Tag.TAG_ANY_NUMERIC)) {
            return;
        }
        if (!playerTag.contains("RelativeShipX", Tag.TAG_ANY_NUMERIC)
                || !playerTag.contains("RelativeShipY", Tag.TAG_ANY_NUMERIC)
                || !playerTag.contains("RelativeShipZ", Tag.TAG_ANY_NUMERIC)) {
            return;
        }

        writeAnchor(player, LOGOUT_ANCHOR, new ShipAnchor(
                playerTag.getLong("LastShipId"),
                playerTag.getDouble("RelativeShipX"),
                playerTag.getDouble("RelativeShipY"),
                playerTag.getDouble("RelativeShipZ"),
                false,
                false));
    }

    private static PlacementResult placeOnShip(ServerPlayer player, PendingPlacement pending) {
        MinecraftServer server = player.getServer();
        ServerShipWorld shipWorld = ValkyrienSkiesMod.getApi().getServerShipWorld(server);
        ServerShip ship = shipWorld.getAllShips().getById(pending.anchor.shipId);
        if (ship == null) {
            return PlacementResult.INVALID;
        }
        LoadedServerShip loadedShip = shipWorld.getLoadedShips().getById(ship.getId());
        if (loadedShip == null) {
            return PlacementResult.RETRY;
        }

        ServerLevel targetLevel = VSGameUtilsKt.getLevelFromDimensionId(server, ship.getChunkClaimDimension());
        if (targetLevel == null) {
            return PlacementResult.RETRY;
        }

        Vector3d localTarget;
        if (pending.anchor.blockAnchor) {
            BlockPos spawnPos = BlockPos.containing(
                    pending.anchor.x, pending.anchor.y, pending.anchor.z);
            Optional<Vec3> respawnPosition = Player.findRespawnPositionAndUseSpawnBlock(
                    targetLevel, spawnPos, player.getRespawnAngle(), pending.anchor.forced, false);
            if (respawnPosition.isPresent()) {
                Vec3 position = respawnPosition.get();
                localTarget = new Vector3d(position.x, position.y, position.z);
            } else if (targetLevel.getBlockState(spawnPos).is(Blocks.RESPAWN_ANCHOR)) {
                // PlayerList may already have consumed the anchor's final charge before this event.
                localTarget = findSafeLocalSpawn(targetLevel, spawnPos);
            } else {
                return PlacementResult.INVALID;
            }
        } else {
            localTarget = new Vector3d(pending.anchor.x, pending.anchor.y, pending.anchor.z);
        }
        Vector3d worldTarget = ship.getShipToWorld().transformPosition(localTarget, new Vector3d());

        player.teleportTo(targetLevel, worldTarget.x, worldTarget.y, worldTarget.z,
                Set.of(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        ((IEntityDraggingInformationProvider) player).vs$dragImmediately(ship);


        pending.loadedTicks++;
        return pending.loadedTicks >= 2 ? PlacementResult.COMPLETE : PlacementResult.RETRY;
    }

    private static Vector3d findSafeLocalSpawn(ServerLevel level, BlockPos spawnBlock) {
        for (int radius = 1; radius <= 3; radius++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos feet = spawnBlock.offset(dx, dy, dz);
                        if (isFree(level, feet)
                                && isFree(level, feet.above())
                                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                            return new Vector3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return new Vector3d(spawnBlock.getX() + 0.5, spawnBlock.getY() + 1.1, spawnBlock.getZ() + 0.5);
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean spawnAnchorStillSelected(ServerPlayer player, ShipAnchor anchor) {
        BlockPos respawnPos = player.getRespawnPosition();
        if (respawnPos == null || !respawnPos.equals(BlockPos.containing(anchor.x, anchor.y, anchor.z))) {
            return false;
        }

        ServerShipWorld shipWorld = ValkyrienSkiesMod.getApi().getServerShipWorld(player.getServer());
        ServerShip ship = shipWorld.getAllShips().getById(anchor.shipId);
        if (ship == null) {
            return false;
        }
        ServerLevel level = VSGameUtilsKt.getLevelFromDimensionId(player.getServer(), ship.getChunkClaimDimension());
        if (level == null) {
            return false;
        }
        if (!level.dimension().equals(player.getRespawnDimension())) {
            return false;
        }
        BlockState state = level.getBlockState(respawnPos);
        if (state.is(Blocks.RESPAWN_ANCHOR)) {
            return true;
        }
        return Player.findRespawnPositionAndUseSpawnBlock(
                level, respawnPos, player.getRespawnAngle(), anchor.forced, false).isPresent();
    }


    private static Ship findCurrentShip(ServerPlayer player) {
        ServerShipWorld shipWorld = ValkyrienSkiesMod.getApi().getServerShipWorld(player.getServer());
        Ship mountedShip = ValkyrienSkiesMod.getApi().getShipMountedTo(player);
        if (mountedShip != null) {
            return mountedShip;
        }

        EntityDraggingInformation dragging = ((IEntityDraggingInformationProvider) player).getDraggingInformation();
        Long lastShipId = dragging.getLastShipStoodOn();
        if (lastShipId != null) {
            ServerShip lastShip = shipWorld.getAllShips().getById(lastShipId);
            if (lastShip != null && playerInsideShipBounds(player, lastShip)) {
                return lastShip;
            }
        }

        for (LoadedServerShip ship : shipWorld.getLoadedShips()) {
            if (playerInsideShipBounds(player, ship)) {
                return ship;
            }
        }
        return null;
    }

    private static boolean playerInsideShipBounds(ServerPlayer player, Ship ship) {
        if (!ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(player.level()))) {
            return false;
        }
        AABBic bounds = ship.getShipAABB();
        if (bounds == null) {
            return false;
        }
        Vector3d local = ship.getWorldToShip().transformPosition(
                new Vector3d(player.getX(), player.getY(), player.getZ()), new Vector3d());
        return local.x >= bounds.minX() - SHIP_BOUNDS_MARGIN
                && local.x <= bounds.maxX() + SHIP_BOUNDS_MARGIN
                && local.y >= bounds.minY() - SHIP_BOUNDS_MARGIN
                && local.y <= bounds.maxY() + SHIP_BOUNDS_MARGIN
                && local.z >= bounds.minZ() - SHIP_BOUNDS_MARGIN
                && local.z <= bounds.maxZ() + SHIP_BOUNDS_MARGIN;
    }

    private static boolean isAuthenticationComplete(ServerPlayer player) {
        if (!ModList.get().isLoaded("simplelogin")) {
            return true;
        }
        try {
            initializeSimpleLoginReflection();
            if (simpleLoginInstanceMethod == null || simpleLoginStatusMethod == null) {
                return true;
            }
            Object handler = simpleLoginInstanceMethod.invoke(null);
            return (boolean) simpleLoginStatusMethod.invoke(handler, player.getGameProfile().getName());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!simpleLoginReflectionWarningLogged) {
                simpleLoginReflectionWarningLogged = true;
                LOGGER.warn("Unable to query SimpleLogin state; using normal post-login delay", exception);
            }
            return true;
        }
    }

    private static synchronized void initializeSimpleLoginReflection() throws ReflectiveOperationException {
        if (simpleLoginReflectionInitialized) {
            return;
        }
        simpleLoginReflectionInitialized = true;
        Class<?> handlerClass = Class.forName("top.seraphjack.simplelogin.server.handler.PlayerLoginHandler");
        simpleLoginInstanceMethod = handlerClass.getMethod("instance");
        simpleLoginStatusMethod = handlerClass.getMethod("hasPlayerLoggedIn", String.class);
    }

    private static CompoundTag persistedPositions(ServerPlayer player) {
        CompoundTag forgeData = player.getPersistentData();
        if (!forgeData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            forgeData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = forgeData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(DATA_ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(DATA_ROOT, new CompoundTag());
        }
        return persisted.getCompound(DATA_ROOT);
    }

    private static void writeAnchor(ServerPlayer player, String key, ShipAnchor anchor) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ShipId", anchor.shipId);
        tag.putDouble("X", anchor.x);
        tag.putDouble("Y", anchor.y);
        tag.putDouble("Z", anchor.z);
        tag.putBoolean("BlockAnchor", anchor.blockAnchor);
        tag.putBoolean("Forced", anchor.forced);
        persistedPositions(player).put(key, tag);
    }

    private static ShipAnchor readAnchor(ServerPlayer player, String key) {
        CompoundTag root = persistedPositions(player);
        if (!root.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = root.getCompound(key);
        if (!tag.contains("ShipId", Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        return new ShipAnchor(tag.getLong("ShipId"), tag.getDouble("X"), tag.getDouble("Y"),
                tag.getDouble("Z"), tag.getBoolean("BlockAnchor"), tag.getBoolean("Forced"));
    }

    private static void removeAnchor(ServerPlayer player, String key) {
        persistedPositions(player).remove(key);
    }

    private record ShipAnchor(long shipId, double x, double y, double z, boolean blockAnchor, boolean forced) {
    }

    private static final class PendingPlacement {
        private final ShipAnchor anchor;
        private final boolean waitForAuthentication;
        private int authenticationCompletedTick = -1;
        private int placementTicks;
        private int loadedTicks;

        private PendingPlacement(ShipAnchor anchor, boolean waitForAuthentication) {
            this.anchor = anchor;
            this.waitForAuthentication = waitForAuthentication;
        }

        private static PendingPlacement forLogin(ShipAnchor anchor) {
            return new PendingPlacement(anchor, true);
        }

        private static PendingPlacement forRespawn(ShipAnchor anchor) {
            return new PendingPlacement(anchor, false);
        }
    }

    private enum PlacementResult {
        COMPLETE,
        RETRY,
        INVALID
    }
}
