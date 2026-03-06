package dev.ignis.costomvs.kubejs;

import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.VsCoreApi;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

/**
 * VS 事件处理器 - 将 VS 事件转发给 KubeJS
 * 基于 org.valkyrienskies.core.api.events 包中的实际事件接口
 */
@Mod.EventBusSubscriber(modid = dev.ignis.costomvs.Costomvs.MODID)
public class VSEventHandler {

    public static void register() {
        // 获取 VS API 实例（VsApi 继承自 VsCoreApi）
        VsCoreApi api = ValkyrienSkiesMod.getApi();
        
        // 注册物理 tick 事件（物理线程）
        api.getPhysTickEvent().on((event, handler) -> {
            VSKubeJSEvents.PHYS_TICK.post(
                ScriptType.SERVER,
                new VSKubeJSEventJS.PhysTickEventJS(event.getDelta(), event.getWorld())
            );
        });
        
        // 注册碰撞开始事件（物理线程）
        api.getCollisionStartEvent().on((event, handler) -> {
            VSKubeJSEvents.COLLISION.post(
                ScriptType.SERVER,
                new VSKubeJSEventJS.CollisionEventJS(
                    event.getContactPoints(),
                    event.getDimensionId(),
                    event.getPhysLevel(),
                    event.getShipIdA(),
                    event.getShipIdB()
                )
            );
        });
        
        // 注册合并事件（游戏线程）
        api.getMergeEvent().on((event, handler) -> {
            VSKubeJSEvents.MERGE.post(
                ScriptType.SERVER,
                new VSKubeJSEventJS.MergeEventJS(
                    event.getDimensionId(),
                    event.getNewRoot(),
                    event.getOldRootA(),
                    event.getOldRootB(),
                    event.getStillPocket(),
                    event.getVoxelType()
                )
            );
        });
        
        // 注册分裂事件（游戏线程）
        api.getSplitEvent().on((event, handler) -> {
            VSKubeJSEvents.SPLIT.post(
                ScriptType.SERVER,
                new VSKubeJSEventJS.SplitEventJS(
                    event.getDimensionId(),
                    event.getNewRootA(),
                    event.getNewRootB(),
                    event.getOldRoot(),
                    event.getVoxelType(),
                    event.getWasPocket()
                )
            );
        });
        
        // 注册服务端船只加载事件（游戏线程）
        api.getShipLoadEvent().on((event, handler) -> {
            VSKubeJSEvents.SHIP_LOAD_SERVER.post(
                ScriptType.SERVER,
                new VSKubeJSEventJS.ShipLoadEventJS(event.getShip())
            );
        });
        
        // 注册 tick 结束事件（服务端）
        api.getTickEndEvent().on((event, handler) -> {
            VSKubeJSEvents.TICK_END.post(
                ScriptType.SERVER,
                new VSKubeJSEventJS.TickEndEventJS(event.getWorld())
            );
        });
        
        // 注册客户端事件（仅客户端）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> registerClientEvents(api));
    }
    
    /**
     * 注册客户端事件
     */
    private static void registerClientEvents(VsCoreApi api) {
        // 注册客户端船只加载事件
        api.getShipLoadEventClient().on((event, handler) -> {
            VSKubeJSEvents.SHIP_LOAD_CLIENT.post(
                new VSKubeJSEventJS.ShipLoadClientEventJS(event.getShip())
            );
        });
        
        // 注册客户端船只卸载事件
        api.getShipUnloadEventClient().on((event, handler) -> {
            VSKubeJSEvents.SHIP_UNLOAD_CLIENT.post(
                new VSKubeJSEventJS.ShipUnloadClientEventJS(event.getShip())
            );
        });
        
        // 注册开始更新渲染变换事件
        api.getStartUpdateRenderTransformsEvent().on((event, handler) -> {
            VSKubeJSEvents.START_UPDATE_RENDER_TRANSFORMS.post(
                new VSKubeJSEventJS.StartUpdateRenderTransformsEventJS(event.getShipWorld())
            );
        });
    }
}
