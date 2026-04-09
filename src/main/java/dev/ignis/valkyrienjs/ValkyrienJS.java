package dev.ignis.valkyrienjs;

import dev.ignis.valkyrienjs.feature.blocklimit.BlockLimitAPI;
import dev.ignis.valkyrienjs.feature.blocklimit.ShipBlockLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mod(ValkyrienJS.MODID)

public class ValkyrienJS {

    public static final String MODID = "valkyrienjs";

    @SuppressWarnings("removal")
    public ValkyrienJS() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        ValkyrienSkiesMod.getVsCore().registerAttachment(ShipBlockLimit.class);

        // 注册 Forge 事件监听
        registerBlockEvents();
    }

    private void registerBlockEvents() {
        // 方块放置事件 - 检查限制并取消放置
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }

            BlockPos pos = event.getPos();
            BlockState state = event.getPlacedBlock();

            // 检查是否可以放置
            if (!BlockLimitAPI.canPlaceAt(level, pos, state)) {
                event.setCanceled(true);
                return;
            }

            // 放置成功，增加计数
            BlockLimitAPI.onBlockPlaced(level, pos, state);
        });

        // 方块破坏事件
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                BlockState state = event.getState();
                BlockLimitAPI.onBlockRemoved(level, event.getPos(), state);
            }
        });
    }
}
