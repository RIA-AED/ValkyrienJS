package dev.ignis.valkyrienjs.feature.blocklimit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.primitives.AABBic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class BlockLimitAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger("ValkyrienJS/BlockLimit");

    /**
     * 为指定船只设置方块限制
     * 如果该方块已有当前数量记录，保留；否则扫描船上方块初始化 currentCount
     */
    public static void setLimit(ServerShip ship, String blockId, int maxCount) {
        withLimit(ship, limit -> {
            Map<String, BlockLimitEntry> limits = limit.getBlockLimits();
            BlockLimitEntry existing = limits.get(blockId);

            if (existing != null) {
                limits.put(blockId, new BlockLimitEntry(blockId, maxCount, existing.getCurrentCount()));
            } else {
                int currentCount = scanBlocks(ship, blockId);
                limits.put(blockId, new BlockLimitEntry(blockId, maxCount, currentCount));
            }
        });
    }

    /**
     * 移除指定方块的限制
     */
    public static void removeLimit(ServerShip ship, String blockId) {
        withLimit(ship, limit -> {
            limit.getBlockLimits().remove(blockId);
        });
    }

    /**
     * 获取指定方块的限制信息
     */
    public static Optional<BlockLimitEntry> getLimit(ServerShip ship, String blockId) {
        return ShipBlockLimit.get(ship)
                .map(l -> l.getBlockLimits().get(blockId));
    }

    /**
     * 获取船只所有限制
     */
    public static Map<String, BlockLimitEntry> getAllLimits(ServerShip ship) {
        return ShipBlockLimit.get(ship)
                .map(l -> Collections.unmodifiableMap(l.getBlockLimits()))
                .orElse(Collections.emptyMap());
    }

    /**
     * 更新最大数量（保留当前计数）
     */
    public static void updateMaxCount(ServerShip ship, String blockId, int maxCount) {
        withLimit(ship, limit -> {
            Map<String, BlockLimitEntry> limits = limit.getBlockLimits();
            BlockLimitEntry entry = limits.get(blockId);
            if (entry != null) {
                limits.put(blockId, new BlockLimitEntry(blockId, maxCount, entry.getCurrentCount()));
            }
        });
    }

    /**
     * 手动设置当前数量
     */
    public static void setCurrentCount(ServerShip ship, String blockId, int count) {
        withLimit(ship, limit -> {
            BlockLimitEntry entry = limit.getBlockLimits().get(blockId);
            if (entry != null) {
                entry.setCurrentCount(count);
            }
        });
    }

    /**
     * 检查是否可以放置
     */
    public static boolean canPlace(ServerShip ship, String blockId) {
        return ShipBlockLimit.get(ship)
                .map(l -> l.getBlockLimits().get(blockId))
                .map(BlockLimitEntry::canPlace)
                .orElse(true);
    }

    /**
     * 获取当前数量（无限制返回0）
     */
    public static int getCurrentCount(ServerShip ship, String blockId) {
        return ShipBlockLimit.get(ship)
                .map(l -> l.getBlockLimits().get(blockId))
                .map(BlockLimitEntry::getCurrentCount)
                .orElse(0);
    }

    /**
     * 重新扫描船上所有受限制方块的数量
     */
    public static void rescanAllLimits(ServerShip ship) {
        withLimit(ship, limit -> {
            for (BlockLimitEntry entry : limit.getBlockLimits().values()) {
                int count = scanBlocks(ship, entry.getBlockId());
                entry.setCurrentCount(count);
            }
        });
    }

    /**
     * 增加指定方块的计数
     */
    public static void incrementCount(ServerShip ship, String blockId) {
        withLimit(ship, limit -> {
            BlockLimitEntry entry = limit.getBlockLimits().get(blockId);
            if (entry != null) {
                entry.increment();
            }
        });
    }

    /**
     * 减少指定方块的计数
     */
    public static void decrementCount(ServerShip ship, String blockId) {
        withLimit(ship, limit -> {
            BlockLimitEntry entry = limit.getBlockLimits().get(blockId);
            if (entry != null) {
                entry.decrement();
            }
        });
    }

    /**
     * 从位置获取船只并检查是否可以放置
     */
    public static boolean canPlaceAt(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }

        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);
        if (ship == null) {
            return true;
        }

        String blockId = getBlockId(state.getBlock());
        return canPlace(ship, blockId);
    }

    /**
     * 从位置获取船只并增加计数
     */
    public static void onBlockPlaced(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);
        if (ship == null) {
            return;
        }

        String blockId = getBlockId(state.getBlock());
        incrementCount(ship, blockId);
    }

    /**
     * 从位置获取船只并减少计数
     */
    public static void onBlockRemoved(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);
        if (ship == null) {
            return;
        }

        String blockId = getBlockId(state.getBlock());
        decrementCount(ship, blockId);
    }

    // ========== 内部方法 ==========

    private static void withLimit(ServerShip ship, Consumer<ShipBlockLimit> action) {
        if (ship instanceof LoadedServerShip loadedShip) {
            ShipBlockLimit limit = ShipBlockLimit.getOrCreate(loadedShip);
            action.accept(limit);
        }
    }

    /**
     * 扫描船上指定方块的数量
     */
    private static int scanBlocks(ServerShip ship, String blockId) {
        if (!(ship instanceof LoadedServerShip loadedShip)) {
            return 0;
        }

        // 通过 ValkyrienSkiesMod.currentServer 获取 Server，再获取 Level
        MinecraftServer server = ValkyrienSkiesMod.getCurrentServer();
        if (server == null) {
            return 0;
        }

        // 获取船只所在的ServerLevel - 通过VSGameUtilsKt
        ServerLevel shipLevel = VSGameUtilsKt.getLevelFromDimensionId(server,ship.getChunkClaimDimension());
        if (shipLevel == null) {
            return 0;
        }

        AABBic aabb = ship.getShipAABB();
        if (aabb == null) {
            LOGGER.debug("[BlockLimit] Ship {} has no AABB, returning 0", ship.getId());
            return 0;
        }

        // 输出扫描范围调试信息
        LOGGER.debug("[BlockLimit] Scanning ship {} for block {} in range X[{},{}] Y[{},{}] Z[{},{}]",
                ship.getId(), blockId,
                aabb.minX(), aabb.maxX(),
                aabb.minY(), aabb.maxY(),
                aabb.minZ(), aabb.maxZ());

        int count = 0;
        int checkedBlocks = 0;
        int matchedBlocks = 0;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        // 遍历船只AABB内的所有方块
        for (int x = aabb.minX(); x <= aabb.maxX(); x++) {
            for (int y = aabb.minY(); y <= aabb.maxY(); y++) {
                for (int z = aabb.minZ(); z <= aabb.maxZ(); z++) {
                    mutablePos.set(x, y, z);
                    if (shipLevel.isLoaded(mutablePos)) {
                        checkedBlocks++;
                        BlockState state = shipLevel.getBlockState(mutablePos);
                        if (!state.isAir()) {
                            String id = getBlockId(state.getBlock());
                            if (id.equals(blockId)) {
                                count++;
                                matchedBlocks++;
                            }
                        }
                    }
                }
            }
        }

        LOGGER.debug("[BlockLimit] Scan complete for ship {} block {}: checked {} blocks, found {} matches",
                ship.getId(), blockId, checkedBlocks, matchedBlocks);

        return count;
    }

    private static String getBlockId(Block block) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)).toString();
    }
}
