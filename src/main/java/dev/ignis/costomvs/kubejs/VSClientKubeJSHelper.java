package dev.ignis.costomvs.kubejs;

import net.minecraft.client.Minecraft;
import org.valkyrienskies.core.api.world.ClientShipWorld;
import org.valkyrienskies.mod.api.VsApi;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

/**
 * 客户端专用的 VS API 辅助类
 */
public class VSClientKubeJSHelper {

    /**
     * 获取客户端飞船世界
     */
    public static ClientShipWorld getClientShipWorld() {
        return ValkyrienSkiesMod.getApi().getClientShipWorld(Minecraft.getInstance());
    }

    /**
     * 获取客户端飞船世界（简化版）
     */
    public static ClientShipWorld getShipWorld() {
        return ValkyrienSkiesMod.getApi().getClientShipWorld();
    }
}
