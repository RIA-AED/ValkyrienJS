package dev.ignis.valkyrienjs;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ValkyrienJS.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.LongValue MAX_COLLISION_BOX_VOLUME = BUILDER
            .comment("Maximum collision box volume threshold (default: 1 billion)")
            .defineInRange("maxCollisionBoxVolume", 1_000_000_000L, 1L, Long.MAX_VALUE);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static long maxCollisionBoxVolume;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxCollisionBoxVolume = MAX_COLLISION_BOX_VOLUME.get();
    }
}
