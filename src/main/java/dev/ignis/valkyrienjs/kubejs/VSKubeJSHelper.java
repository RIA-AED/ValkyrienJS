package dev.ignis.valkyrienjs.kubejs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ClientShipWorld;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.core.api.world.ShipWorld;
import org.valkyrienskies.mod.api.VsApi;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.List;

/**
 * VS API 辅助类 - 为 KubeJS 提供简化的 API 访问
 */
public class VSKubeJSHelper {

    /**
     * 获取 VsApi 实例
     */
    public static VsApi getApi() {
        return ValkyrienSkiesMod.getApi();
    }

    /**
     * 检查实体是否正在骑乘飞船
     */
    public static boolean isShipMountingEntity(Entity entity) {
        return getApi().isShipMountingEntity(entity);
    }

    /**
     * 获取实体骑乘的飞船
     */
    public static Ship getShipMountedTo(Entity entity) {
        return getApi().getShipMountedTo(entity);
    }

    /**
     * 获取实体在飞船坐标系中的位置
     */
    public static Vector3d getMountPosInShip(Entity entity) {
        Vector3dc pos = getApi().getMountPosInShip(entity);
        return pos != null ? new Vector3d(pos) : null;
    }

    /**
     * 获取实体在飞船坐标系中的位置（带插值）
     */
    public static Vector3d getMountPosInShip(Entity entity, float partialTicks) {
        Vector3dc pos = getApi().getMountPosInShip(entity, partialTicks);
        return pos != null ? new Vector3d(pos) : null;
    }

    /**
     * 获取服务端飞船世界
     */
    public static ServerShipWorld getServerShipWorld(MinecraftServer server) {
        return getApi().getServerShipWorld(server);
    }

    /**
     * 获取指定世界的飞船世界
     */
    public static ShipWorld getShipWorld(Level level) {
        return getApi().getShipWorld(level);
    }

    /**
     * 检查指定区块是否在飞船场中
     */
    public static boolean isChunkInShipyard(Level level, int chunkX, int chunkZ) {
        return getApi().isChunkInShipyard(level, chunkX, chunkZ);
    }

    /**
     * 获取管理指定方块位置的飞船
     */
    public static Ship getShipManagingBlock(Level level, BlockPos pos) {
        return getApi().getShipManagingBlock(level, pos);
    }

    /**
     * 获取管理指定区块的飞船
     */
    public static Ship getShipManagingChunk(Level level, ChunkPos pos) {
        return getApi().getShipManagingChunk(level, pos);
    }

    /**
     * 获取管理指定区块坐标的飞船
     */
    public static Ship getShipManagingChunk(Level level, int chunkX, int chunkZ) {
        return getApi().getShipManagingChunk(level, chunkX, chunkZ);
    }

    /**
     * 获取与指定位置相交的所有飞船
     */
    public static List<Ship> getShipsIntersecting(Level level, double x, double y, double z) {
        Iterable<Ship> ships = getApi().getShipsIntersecting(level, x, y, z);
        List<Ship> result = new ArrayList<>();
        ships.forEach(result::add);
        return result;
    }

    /**
     * 获取飞船在世界中的位置
     */
    public static Vector3d getShipPosition(Ship ship) {
        Vector3dc pos = ship.getTransform().getPositionInWorld();
        return new Vector3d(pos);
    }

    /**
     * 获取飞船的 ID
     */
    public static long getShipId(Ship ship) {
        return ship.getId();
    }

    /**
     * 获取飞船的 slug（可读标识符）
     */
    public static String getShipSlug(Ship ship) {
        return ship.getSlug();
    }

    /**
     * 检查位置是否在飞船场中
     */
    public static boolean isBlockInShipyard(Level level, BlockPos pos) {
        return isChunkInShipyard(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * 获取所有加载的飞船列表
     */
    public static List<Ship> getAllShips(Level level) {
        ShipWorld shipWorld = getShipWorld(level);
        if (shipWorld == null) return new ArrayList<>();
        
        List<Ship> result = new ArrayList<>();
        shipWorld.getAllShips().forEach(result::add);
        return result;
    }

    /**
     * 通过 BlockPos 获取飞船，如果该位置不在任何飞船上则返回 null
     */
    public static Ship getShipByBlockPos(Level level, BlockPos pos) {
        return getShipManagingBlock(level, pos);
    }

    /**
     * 获取管理指定方块位置的已加载飞船（用于 BlockLimitAPI）
     * 返回 LoadedServerShip 而不是 Ship，确保可以操作附件
     */
    public static LoadedServerShip getLoadedShipManagingBlock(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);
    }

    /**
     * 将飞船上的坐标转换为世界坐标
     * @param ship 飞船对象
     * @param shipPos 飞船坐标系中的位置（Minecraft Vec3）
     * @return 世界坐标，如果 ship 为 null 则返回 null
     */
    public static Vec3 shipPosToWorldPos(Ship ship, Vec3 shipPos) {
        if (ship == null || shipPos == null) {
            return null;
        }
        Vector3d shipPosJoml = new Vector3d(shipPos.x, shipPos.y, shipPos.z);
        Vector3d worldPos = ship.getShipToWorld().transformPosition(shipPosJoml);
        return new Vec3(worldPos.x, worldPos.y, worldPos.z);
    }

    /**
     * 将飞船上的 BlockPos 转换为世界坐标的 Vec3
     * @param ship 飞船对象
     * @param shipBlockPos 飞船坐标系中的 BlockPos
     * @return 世界坐标的 Vec3，如果 ship 为 null 则返回 null
     */
    public static Vec3 shipBlockPosToWorldVec3(Ship ship, BlockPos shipBlockPos) {
        return shipPosToWorldPos(ship, new Vec3(shipBlockPos.getX(), shipBlockPos.getY(), shipBlockPos.getZ()));
    }
}
