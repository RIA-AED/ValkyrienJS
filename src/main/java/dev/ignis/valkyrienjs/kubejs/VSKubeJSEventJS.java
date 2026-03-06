package dev.ignis.valkyrienjs.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.valkyrienskies.core.api.physics.ContactPoint;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ClientShipWorld;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.core.api.world.ServerShipWorld;

import java.util.Collection;

/**
 * VS 事件包装类 - 提供给 KubeJS 脚本使用的事件对象
 * 基于 org.valkyrienskies.core.api.events 包中的实际事件接口
 */
public class VSKubeJSEventJS {

    /**
     * 物理 tick 事件 - 在物理线程执行
     */
    public static class PhysTickEventJS extends EventJS {
        private final double delta;
        private final PhysLevel world;
        
        public PhysTickEventJS(double delta, PhysLevel world) {
            this.delta = delta;
            this.world = world;
        }
        
        public double getDelta() {
            return delta;
        }
        
        public PhysLevel getWorld() {
            return world;
        }
    }

    /**
     * 碰撞事件 - 在物理线程执行
     */
    public static class CollisionEventJS extends EventJS {
        private final Collection<ContactPoint> contactPoints;
        private final String dimensionId;
        private final PhysLevel physLevel;
        private final long shipIdA;
        private final long shipIdB;
        
        public CollisionEventJS(Collection<ContactPoint> contactPoints, String dimensionId, 
                                PhysLevel physLevel, long shipIdA, long shipIdB) {
            this.contactPoints = contactPoints;
            this.dimensionId = dimensionId;
            this.physLevel = physLevel;
            this.shipIdA = shipIdA;
            this.shipIdB = shipIdB;
        }
        
        public Collection<ContactPoint> getContactPoints() {
            return contactPoints;
        }
        
        public String getDimensionId() {
            return dimensionId;
        }
        
        public PhysLevel getPhysLevel() {
            return physLevel;
        }
        
        public long getShipIdA() {
            return shipIdA;
        }
        
        public long getShipIdB() {
            return shipIdB;
        }
    }

    /**
     * 合并事件 - 在游戏线程执行
     */
    public static class MergeEventJS extends EventJS {
        private String dimensionId;
        private final Vector3i newRoot;
        private final Vector3i oldRootA;
        private final Vector3i oldRootB;
        private final boolean stillPocket;
        private final int voxelType;
        
        public MergeEventJS(String dimensionId, Vector3ic newRoot, Vector3ic oldRootA, 
                           Vector3ic oldRootB, boolean stillPocket, int voxelType) {
            this.dimensionId = dimensionId;
            this.newRoot = new Vector3i(newRoot);
            this.oldRootA = new Vector3i(oldRootA);
            this.oldRootB = new Vector3i(oldRootB);
            this.stillPocket = stillPocket;
            this.voxelType = voxelType;
        }
        
        public String getDimensionId() {
            return dimensionId;
        }
        
        public void setDimensionId(String dimensionId) {
            this.dimensionId = dimensionId;
        }
        
        public Vector3i getNewRoot() {
            return newRoot;
        }
        
        public Vector3i getOldRootA() {
            return oldRootA;
        }
        
        public Vector3i getOldRootB() {
            return oldRootB;
        }
        
        public boolean isStillPocket() {
            return stillPocket;
        }
        
        public int getVoxelType() {
            return voxelType;
        }
    }

    /**
     * 分裂事件 - 在游戏线程执行
     */
    public static class SplitEventJS extends EventJS {
        private String dimensionId;
        private final Vector3i newRootA;
        private final Vector3i newRootB;
        private final Vector3i oldRoot;
        private final int voxelType;
        private final boolean wasPocket;
        
        public SplitEventJS(String dimensionId, Vector3ic newRootA, Vector3ic newRootB, 
                           Vector3ic oldRoot, int voxelType, boolean wasPocket) {
            this.dimensionId = dimensionId;
            this.newRootA = new Vector3i(newRootA);
            this.newRootB = new Vector3i(newRootB);
            this.oldRoot = new Vector3i(oldRoot);
            this.voxelType = voxelType;
            this.wasPocket = wasPocket;
        }
        
        public String getDimensionId() {
            return dimensionId;
        }
        
        public void setDimensionId(String dimensionId) {
            this.dimensionId = dimensionId;
        }
        
        public Vector3i getNewRootA() {
            return newRootA;
        }
        
        public Vector3i getNewRootB() {
            return newRootB;
        }
        
        public Vector3i getOldRoot() {
            return oldRoot;
        }
        
        public int getVoxelType() {
            return voxelType;
        }
        
        public boolean isWasPocket() {
            return wasPocket;
        }
    }

    /**
     * 服务端船只加载事件 - 在游戏线程执行
     */
    public static class ShipLoadEventJS extends EventJS {
        private final LoadedServerShip ship;
        
        public ShipLoadEventJS(LoadedServerShip ship) {
            this.ship = ship;
        }
        
        public LoadedServerShip getShip() {
            return ship;
        }
        
        public long getShipId() {
            return ship.getId();
        }
        
        public String getShipSlug() {
            return ship.getSlug();
        }
        
        public Vector3d getShipPosition() {
            return new Vector3d(ship.getTransform().getPositionInWorld());
        }
    }

    /**
     * 客户端船只加载事件 - 在游戏线程执行
     */
    public static class ShipLoadClientEventJS extends EventJS {
        private final ClientShip ship;
        
        public ShipLoadClientEventJS(ClientShip ship) {
            this.ship = ship;
        }
        
        public ClientShip getShip() {
            return ship;
        }
        
        public long getShipId() {
            return ship.getId();
        }
        
        public String getShipSlug() {
            return ship.getSlug();
        }
    }

    /**
     * 客户端船只卸载事件 - 在游戏线程执行
     */
    public static class ShipUnloadClientEventJS extends EventJS {
        private final ClientShip ship;
        
        public ShipUnloadClientEventJS(ClientShip ship) {
            this.ship = ship;
        }
        
        public ClientShip getShip() {
            return ship;
        }
        
        public long getShipId() {
            return ship.getId();
        }
        
        public String getShipSlug() {
            return ship.getSlug();
        }
    }

    /**
     * 开始更新渲染变换事件 - 客户端
     */
    public static class StartUpdateRenderTransformsEventJS extends EventJS {
        private final ClientShipWorld shipWorld;
        
        public StartUpdateRenderTransformsEventJS(ClientShipWorld shipWorld) {
            this.shipWorld = shipWorld;
        }
        
        public ClientShipWorld getShipWorld() {
            return shipWorld;
        }
    }

    /**
     * Tick 结束事件 - 服务端
     */
    public static class TickEndEventJS extends EventJS {
        private final ServerShipWorld world;
        
        public TickEndEventJS(ServerShipWorld world) {
            this.world = world;
        }
        
        public ServerShipWorld getWorld() {
            return world;
        }
    }
}
