package dev.ignis.costomvs.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * VS 事件定义 - 定义所有可监听的事件
 * 基于 org.valkyrienskies.core.api.events 包中的实际事件接口
 */
public interface VSKubeJSEvents {
    EventGroup GROUP = EventGroup.of("VSEvents");

    // 物理 tick 事件 - 物理线程
    EventHandler PHYS_TICK = GROUP.server("physTick", () -> VSKubeJSEventJS.PhysTickEventJS.class);
    
    // 碰撞事件 - 物理线程
    EventHandler COLLISION = GROUP.server("collision", () -> VSKubeJSEventJS.CollisionEventJS.class);
    
    // 合并事件 - 游戏线程
    EventHandler MERGE = GROUP.server("merge", () -> VSKubeJSEventJS.MergeEventJS.class);
    
    // 分裂事件 - 游戏线程
    EventHandler SPLIT = GROUP.server("split", () -> VSKubeJSEventJS.SplitEventJS.class);
    
    // 服务端船只加载事件 - 游戏线程
    EventHandler SHIP_LOAD_SERVER = GROUP.server("shipLoadServer", () -> VSKubeJSEventJS.ShipLoadEventJS.class);
    
    // 客户端船只加载事件 - 游戏线程
    EventHandler SHIP_LOAD_CLIENT = GROUP.client("shipLoadClient", () -> VSKubeJSEventJS.ShipLoadClientEventJS.class);
    
    // 客户端船只卸载事件 - 游戏线程
    EventHandler SHIP_UNLOAD_CLIENT = GROUP.client("shipUnloadClient", () -> VSKubeJSEventJS.ShipUnloadClientEventJS.class);
    
    // 开始更新渲染变换事件 - 客户端
    EventHandler START_UPDATE_RENDER_TRANSFORMS = GROUP.client("startUpdateRenderTransforms", () -> VSKubeJSEventJS.StartUpdateRenderTransformsEventJS.class);
    
    // tick 结束事件 - 服务端
    EventHandler TICK_END = GROUP.server("tickEnd", () -> VSKubeJSEventJS.TickEndEventJS.class);
}
