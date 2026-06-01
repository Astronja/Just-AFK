package com.justafk;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Singleton handler that manages AFK state for all players.
 *
 * On each server tick, AFK players are:
 * - Set invulnerable (no damage)
 * - Their velocity is zeroed (prevents external movement)
 * - Checked for self-initiated movement / input / interaction (exits AFK)
 * - Sent a title periodically
 */
public enum AfkPlayerHandler {
    INSTANCE;

    /** Players currently in AFK mode */
    private final Set<UUID> afkPlayers = new HashSet<>();

    /** Snapshot of position + rotation when entering AFK */
    private final Map<UUID, AfkSnapshot> afkSnapshots = new HashMap<>();

    /** Players who have sent an interaction/action packet this tick */
    private final Set<UUID> activeInputPlayers = new HashSet<>();

    /** Current server instance (set from tick callback) */
    private MinecraftServer currentServer = null;

    /** Tick counter for periodic title refresh */
    private int tickCounter = 0;

    // ─── Snapshot record ─────────────────────────────────────────────

    private record AfkSnapshot(Vec3d pos, float yaw, float pitch) {}

    // ─── Public API ──────────────────────────────────────────────────

    /**
     * Mark a player as having active input (called from interaction callbacks).
     * If they're AFK, they'll exit on the next tick.
     */
    public void markActiveInput(ServerPlayerEntity player) {
        activeInputPlayers.add(player.getUuid());
    }

    /**
     * Toggle a player's AFK state.
     *
     * @param player the target player
     * @return {@code true} if the player is now AFK, {@code false} if they exited
     */
    /**
     * Toggle a player's AFK state. Broadcasts the "*" message to everyone else.
     *
     * @param player the target player
     * @return {@code true} if the player is now AFK, {@code false} if they exited
     */
    public boolean toggleAfk(ServerPlayerEntity player) {
        return toggleAfk(player, player);
    }

    /**
     * Toggle a player's AFK state with control over who sees the "*" broadcast.
     *
     * @param player      the target player
     * @param excludeFromBroadcast player to exclude from the public "*" message (or null to broadcast to all)
     * @return {@code true} if the player is now AFK, {@code false} if they exited
     */
    public boolean toggleAfk(ServerPlayerEntity player, @Nullable ServerPlayerEntity excludeFromBroadcast) {
        UUID uuid = player.getUuid();

        if (afkPlayers.contains(uuid)) {
            // ── Exit AFK mode ────────────────────────────────────
            afkPlayers.remove(uuid);
            afkSnapshots.remove(uuid);
            player.setInvulnerable(false);

            // Clear any displayed title
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));

            // Restore tab-list name
            updateTabListName(player, false);

            // Public broadcast (exclude the player themselves)
            broadcastExcept(Text.literal("§7* " + player.getName().getString() + " is back from AFK"), excludeFromBroadcast);

            return false;
        } else {
            // ── Enter AFK mode ───────────────────────────────────
            afkPlayers.add(uuid);
            afkSnapshots.put(uuid, new AfkSnapshot(
                    new Vec3d(player.getX(), player.getY(), player.getZ()),
                    player.getYaw(),
                    player.getPitch()
            ));
            player.setInvulnerable(true);
            player.setVelocity(Vec3d.ZERO);

            // Send title immediately on entering
            sendAfkTitle(player);

            // Set tab-list name to grey/italic
            updateTabListName(player, true);

            // Public broadcast (exclude the player themselves)
            broadcastExcept(Text.literal("§7* " + player.getName().getString() + " went AFK"), excludeFromBroadcast);

            return true;
        }
    }

    /**
     * If the player is AFK, returns the grey/italic display name for the tab list.
     * Called from the mixin override of {@code ServerPlayerEntity.getPlayerListName()}.
     */
    public Text getAfkDisplayName(ServerPlayerEntity player) {
        if (afkPlayers.contains(player.getUuid())) {
            return Text.literal("§7§o" + player.getName().getString());
        }
        return null;
    }

    /**
     * Silently remove a player from AFK mode — no message, no broadcast.
     * Used when the player joins or disconnects.
     */
    public void exitAfkSilently(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!afkPlayers.contains(uuid)) return;

        afkPlayers.remove(uuid);
        afkSnapshots.remove(uuid);
        player.setInvulnerable(false);
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));

        // Restore tab-list name
        updateTabListName(player, false);
    }

    // ─── Tick logic ─────────────────────────────────────────────────

    /**
     * Called every server tick to process all AFK players.
     */
    public void onServerTick(MinecraftServer server) {
        currentServer = server;
        if (afkPlayers.isEmpty()) return;

        tickCounter++;
        boolean sendTitleThisTick = (tickCounter % 40 == 0); // every 2 seconds

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (!afkPlayers.contains(uuid)) continue;

            boolean shouldExit = false;

            // ── 1. Check interaction / action input flag ───────
            if (activeInputPlayers.remove(uuid)) {
                shouldExit = true;
            }

            // ── 2. Check keyboard input (from PlayerInput) ─────
            if (!shouldExit) {
                var input = player.getPlayerInput();
                if (input != null) {
                    if (input.forward() || input.backward()
                            || input.left() || input.right()
                            || input.jump() || input.sneak() || input.sprint()) {
                        shouldExit = true;
                    }
                }
            }

            // ── 3. Check position + rotation changes ───────────
            if (!shouldExit) {
                AfkSnapshot snap = afkSnapshots.get(uuid);
                if (snap != null) {
                    double dx = player.getX() - snap.pos().getX();
                    double dz = player.getZ() - snap.pos().getZ();
                    double dy = player.getY() - snap.pos().getY();

                    // Horizontal movement
                    if (dx * dx + dz * dz > 0.0001) {
                        shouldExit = true;
                    }

                    // Vertical movement (gravity-resistant threshold)
                    if (!shouldExit && Math.abs(dy) > 0.06) {
                        shouldExit = true;
                    }

                    // Yaw / pitch change (mouse movement) — exclude tiny escape-key jitter
                    if (!shouldExit) {
                        float yawDiff = Math.abs(player.getYaw() - snap.yaw());
                        float pitchDiff = Math.abs(player.getPitch() - snap.pitch());
                        if (yawDiff > 5.0f || pitchDiff > 5.0f) {
                            shouldExit = true;
                        }
                    }
                }
            }

            // ── Exit if triggered ──────────────────────────────
            if (shouldExit) {
                toggleAfk(player);
                player.sendMessage(
                        Text.literal("§aYou have exited AFK mode."),
                        false
                );
                continue;
            }

            // ── Maintain invulnerability ─────────────────────────
            if (!player.isInvulnerable()) {
                player.setInvulnerable(true);
            }

            // ── Zero velocity to prevent external movement ───────
            if (player.getVelocity().lengthSquared() > 0.0001) {
                player.setVelocity(Vec3d.ZERO);
            }

            // ── Send title periodically ──────────────────────────
            if (sendTitleThisTick) {
                sendAfkTitle(player);
            }
        }
    }

    // ─── Tab-list display — scoreboard team approach ──────────────

    /** Name of the temporary team for AFK players */
    private static final String AFK_TEAM_NAME = "justafk_afk";

    /**
     * Update the tab-list display name for the player.
     * The mixin makes {@code getPlayerListName()} return the AFK name,
     * so we just broadcast an UPDATE_DISPLAY_NAME packet for this player.
     */
    private void updateTabListName(ServerPlayerEntity player, boolean afkStyle) {
        if (currentServer == null) return;

        // The mixin ServerPlayerEntityMixin overrides getPlayerListName() to return
        // our grey/italic name when the player is AFK. The packet constructor reads
        // getPlayerListName() and includes it as the display name.
        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME,
                player
        );

        // Send to all players so the tab list updates
        for (ServerPlayerEntity p : currentServer.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(packet);
        }
    }

    // ─── Broadcast helper ──────────────────────────────────────────

    /**
     * Send a chat message to all players except {@code exclude}.
     */
    private void broadcastExcept(Text message, @Nullable ServerPlayerEntity exclude) {
        if (currentServer == null) return;
        for (ServerPlayerEntity p : currentServer.getPlayerManager().getPlayerList()) {
            if (p.equals(exclude)) continue;
            p.sendMessage(message, false);
        }
    }

    // ─── Title helper ───────────────────────────────────────────────

    private void sendAfkTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 80, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("§eYou are now AFK")
        ));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal("§7Type '/afk' or move to quit AFK mode")
        ));
    }
}
