package com.justafk;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AfkMod implements ModInitializer {
    public static final String MOD_ID = "just-afk";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Just AFK");

        // Register the /afk command
        CommandRegistrationCallback.EVENT.register(AfkCommand::register);

        // Register the server tick handler
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // ── Join / disconnect — silently clean up AFK state ────────

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            AfkPlayerHandler.INSTANCE.exitAfkSilently(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            AfkPlayerHandler.INSTANCE.exitAfkSilently(player);
        });

        // ── Interaction callbacks (exit AFK on any player action) ──

        // Left-click block
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                AfkPlayerHandler.INSTANCE.markActiveInput(sp);
            }
            return ActionResult.PASS;
        });

        // Left-click entity
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                AfkPlayerHandler.INSTANCE.markActiveInput(sp);
            }
            return ActionResult.PASS;
        });

        // Right-click block
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                AfkPlayerHandler.INSTANCE.markActiveInput(sp);
            }
            return ActionResult.PASS;
        });

        // Right-click entity
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                AfkPlayerHandler.INSTANCE.markActiveInput(sp);
            }
            return ActionResult.PASS;
        });

        // Block breaking (after start — covers the whole mining process)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                AfkPlayerHandler.INSTANCE.markActiveInput(sp);
            }
        });
    }

    private void onServerTick(MinecraftServer server) {
        AfkPlayerHandler.INSTANCE.onServerTick(server);
    }
}
