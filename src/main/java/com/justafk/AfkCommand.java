package com.justafk;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class AfkCommand {

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(CommandManager.literal("afk")
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    ServerPlayerEntity player = source.getPlayer();

                    if (player == null) {
                        source.sendFeedback(
                                () -> Text.literal("§cThis command can only be used by players."),
                                false
                        );
                        return 0;
                    }

                    boolean wasAfk = AfkPlayerHandler.INSTANCE.isAfk(player);
                    boolean nowAfk = AfkPlayerHandler.INSTANCE.toggleAfk(player);

                    if (nowAfk) {
                        source.sendFeedback(
                                () -> Text.literal("§4You are now in AFK mode.§r\n§7Type '/afk' or move to quit."),
                                false
                        );
                    } else if (wasAfk) {
                        source.sendFeedback(
                                () -> Text.literal("§aYou have exited AFK mode."),
                                false
                        );
                    }

                    return 1;
                })
        );
    }
}
