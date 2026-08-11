package dev.ignis.valkyrienjs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

import dev.ignis.valkyrienjs.feature.blocklimit.BlockLimitAPI;

public final class BlockLimitCommand {
    private BlockLimitCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("valkyrienjs")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("blocklimit")
                        .then(Commands.literal("rescan")
                                .then(Commands.argument("slug", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (LoadedServerShip ship : ValkyrienSkiesMod.getApi()
                                                    .getServerShipWorld(context.getSource().getServer())
                                                    .getLoadedShips()) {
                                                builder.suggest(ship.getSlug());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> rescan(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "slug")))))));
    }

    private static int rescan(CommandSourceStack source, String slug) {
        for (LoadedServerShip ship : ValkyrienSkiesMod.getApi()
                .getServerShipWorld(source.getServer())
                .getLoadedShips()) {
            if (ship.getSlug().equals(slug)) {
                BlockLimitAPI.rescanAllLimits(ship);
                int limitCount = BlockLimitAPI.getAllLimits(ship).size();
                source.sendSuccess(
                        () -> Component.literal("Recalculated " + limitCount
                                + " block limits for ship " + slug),
                        true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("No loaded ship found with slug " + slug));
        return 0;
    }
}
