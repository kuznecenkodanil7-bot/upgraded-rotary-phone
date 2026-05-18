package ru.vgoqcnss4002.staffjoin;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StaffJoinNotifierClient implements ClientModInitializer {
    public static final String MOD_ID = "staff-join-notifier";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, Integer> PENDING_CHECKS = new HashMap<>();
    private static final Set<UUID> ANNOUNCED = new HashSet<>();

    private static StaffJoinConfig config;
    private static int joinGraceTicksRemaining = 0;

    @Override
    public void onInitializeClient() {
        config = StaffJoinConfig.load();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetForServerJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetAll());
        ClientTickEvents.END_CLIENT_TICK.register(StaffJoinNotifierClient::onEndClientTick);

        LOGGER.info("Staff Join Notifier initialized.");
    }

    public static void onPlayerListPacket(PlayerListS2CPacket packet) {
        if (!isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        if (packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
            for (PlayerListS2CPacket.Entry entry : packet.getPlayerAdditionEntries()) {
                UUID profileId = entry.profileId();
                if (profileId == null || isSelf(client, profileId)) {
                    continue;
                }

                if (joinGraceTicksRemaining > 0) {
                    ANNOUNCED.add(profileId);
                    continue;
                }

                if (!ANNOUNCED.contains(profileId)) {
                    PENDING_CHECKS.put(profileId, Math.max(1, config.checkTicks));
                }
            }
        }

        if (packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME)) {
            for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
                UUID profileId = entry.profileId();
                if (profileId != null && PENDING_CHECKS.containsKey(profileId) && tryAnnounce(client, profileId)) {
                    PENDING_CHECKS.remove(profileId);
                }
            }
        }
    }

    public static void onPlayerRemovePacket(PlayerRemoveS2CPacket packet) {
        for (UUID profileId : packet.profileIds()) {
            PENDING_CHECKS.remove(profileId);
            ANNOUNCED.remove(profileId);
        }
    }

    private static void onEndClientTick(MinecraftClient client) {
        if (!isEnabled()) {
            return;
        }

        if (joinGraceTicksRemaining > 0) {
            joinGraceTicksRemaining--;
        }

        if (PENDING_CHECKS.isEmpty()) {
            return;
        }

        List<UUID> profileIds = new ArrayList<>(PENDING_CHECKS.keySet());
        for (UUID profileId : profileIds) {
            if (ANNOUNCED.contains(profileId)) {
                PENDING_CHECKS.remove(profileId);
                continue;
            }

            if (tryAnnounce(client, profileId)) {
                PENDING_CHECKS.remove(profileId);
                continue;
            }

            int ticksLeft = PENDING_CHECKS.getOrDefault(profileId, 1) - 1;
            if (ticksLeft <= 0) {
                PENDING_CHECKS.remove(profileId);
            } else {
                PENDING_CHECKS.put(profileId, ticksLeft);
            }
        }
    }

    private static boolean tryAnnounce(MinecraftClient client, UUID profileId) {
        if (ANNOUNCED.contains(profileId)) {
            return true;
        }

        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null || client.player == null) {
            return false;
        }

        PlayerListEntry listEntry = networkHandler.getPlayerListEntry(profileId);
        if (listEntry == null) {
            return false;
        }

        String playerName = getPlayerName(listEntry);
        DetectedStaff detectedStaff = detectStaffPrefix(listEntry, playerName);
        if (detectedStaff == null) {
            return false;
        }

        String message = renderMessage(config.messageTemplate, playerName, detectedStaff.prefix());
        sendMessage(networkHandler, message);

        ANNOUNCED.add(profileId);
        LOGGER.info("Detected staff join: {} with prefix {}", playerName, detectedStaff.prefix());
        return true;
    }

    private static DetectedStaff detectStaffPrefix(PlayerListEntry listEntry, String playerName) {
        List<String> candidates = new ArrayList<>();

        Text displayName = listEntry.getDisplayName();
        if (displayName != null) {
            candidates.add(displayName.getString());
        }

        Team team = listEntry.getScoreboardTeam();
        if (team != null) {
            Text prefix = team.getPrefix();
            if (prefix != null) {
                candidates.add(prefix.getString() + " " + playerName);
            }

            candidates.add(Team.decorateName(team, Text.literal(playerName)).getString());
            candidates.add(team.getName() + " " + playerName);
        }

        candidates.add(playerName);

        for (String candidate : candidates) {
            String matchedPrefix = matchConfiguredPrefix(candidate);
            if (matchedPrefix != null) {
                return new DetectedStaff(matchedPrefix);
            }
        }

        return null;
    }

    private static String matchConfiguredPrefix(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return null;
        }

        for (String prefix : config.sortedPrefixes()) {
            String normalizedPrefix = normalize(prefix);
            if (normalizedPrefix.isBlank()) {
                continue;
            }

            if (normalizedValue.equals(normalizedPrefix)) {
                return prefix;
            }

            if (normalizedValue.startsWith(normalizedPrefix + " ")) {
                return prefix;
            }
        }

        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("§.", "")
                .replace('\u00A0', ' ')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}.]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String getPlayerName(PlayerListEntry listEntry) {
        GameProfile profile = listEntry.getProfile();
        if (profile != null && profile.name() != null && !profile.name().isBlank()) {
            return profile.name();
        }
        return "unknown";
    }

    private static String renderMessage(String template, String playerName, String prefix) {
        String message = template
                .replace("{player}", playerName)
                .replace("{rank}", prefix)
                .replace("{prefix}", prefix);

        if (message.length() > 256) {
            return message.substring(0, 256);
        }

        return message;
    }

    private static void sendMessage(ClientPlayNetworkHandler networkHandler, String message) {
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if (trimmed.startsWith("/") && trimmed.length() > 1) {
            networkHandler.sendChatCommand(trimmed.substring(1));
        } else {
            networkHandler.sendChatMessage(trimmed);
        }
    }

    private static boolean isSelf(MinecraftClient client, UUID profileId) {
        return client.player != null && client.player.getUuid().equals(profileId);
    }

    private static boolean isEnabled() {
        if (config == null) {
            config = StaffJoinConfig.load();
        }
        return config.enabled;
    }

    private static void resetForServerJoin() {
        PENDING_CHECKS.clear();
        ANNOUNCED.clear();
        joinGraceTicksRemaining = Math.max(0, config.joinGraceTicks);
    }

    private static void resetAll() {
        PENDING_CHECKS.clear();
        ANNOUNCED.clear();
        joinGraceTicksRemaining = 0;
    }

    private record DetectedStaff(String prefix) {
    }
}
