package dev.ignis.valkyrienjs;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ValkyrienJS.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue MAX_COLLISION_BOX_SIZE = BUILDER
            .comment("Maximum collision box size on any axis (default: 500)")
            .defineInRange("maxCollisionBoxSize", 500, 1, Integer.MAX_VALUE);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int maxCollisionBoxSize;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxCollisionBoxSize = MAX_COLLISION_BOX_SIZE.get();
    }
}
