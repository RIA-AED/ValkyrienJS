package dev.ignis.valkyrienjs.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.valkyrienskies.mod.api.VsApi;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

/**
 * KubeJS 插件主类 - 注册 VS API 绑定和事件
 */
public class VSKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerEvents() {
        VSKubeJSEvents.GROUP.register();
    }

    @Override
    public void afterInit() {
        // 注册事件监听器
        VSEventHandler.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        // 暴露 VsApi 给 KubeJS 脚本
        event.add("VSApi", VsApi.class);
        event.add("ValkyrienSkies", ValkyrienSkiesMod.class);
        
        // 暴露辅助类
        event.add("VSHelper", VSKubeJSHelper.class);
        
        // 客户端专用绑定
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            event.add("VSClientHelper", VSClientKubeJSHelper.class);
        });
    }
}
