package ru.vgoqcnss4002.staffjoin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StaffJoinConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "staff-join-notifier.json";

    public boolean enabled = true;
    public List<String> prefixes = new ArrayList<>(List.of(
            "st.moder",
            "st.helper",
            "moder",
            "helper",
            "admin",
            "owner"
    ));
    public String messageTemplate = "Игрок {rank} {player} зашёл на сервер";

    /**
     * The first seconds after your own connection are ignored, because the server sends the whole tab-list there.
     * 20 ticks = 1 second.
     */
    public int joinGraceTicks = 100;

    /**
     * How long the mod waits for display-name / scoreboard-team data after ADD_PLAYER.
     * 20 ticks = 1 second.
     */
    public int checkTicks = 200;

    public static StaffJoinConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                StaffJoinConfig config = GSON.fromJson(reader, StaffJoinConfig.class);
                if (config != null) {
                    return config.sanitizeAndSaveIfNeeded(path);
                }
            } catch (Exception exception) {
                StaffJoinNotifierClient.LOGGER.error("Could not read config {}. A default config will be used.", path, exception);
                try {
                    Files.move(path, path.resolveSibling(CONFIG_FILE_NAME + ".broken"));
                } catch (Exception moveException) {
                    StaffJoinNotifierClient.LOGGER.warn("Could not rename broken config {}.", path, moveException);
                }
            }
        }

        StaffJoinConfig config = new StaffJoinConfig();
        config.save(path);
        return config;
    }

    public List<String> sortedPrefixes() {
        if (prefixes == null || prefixes.isEmpty()) {
            return new StaffJoinConfig().prefixes;
        }

        return prefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private StaffJoinConfig sanitizeAndSaveIfNeeded(Path path) {
        boolean changed = false;

        if (prefixes == null || prefixes.isEmpty()) {
            prefixes = new StaffJoinConfig().prefixes;
            changed = true;
        }

        if (messageTemplate == null || messageTemplate.isBlank()) {
            messageTemplate = new StaffJoinConfig().messageTemplate;
            changed = true;
        }

        if (joinGraceTicks < 0) {
            joinGraceTicks = 0;
            changed = true;
        }

        if (checkTicks < 1) {
            checkTicks = 1;
            changed = true;
        }

        if (changed) {
            save(path);
        }

        return this;
    }

    private void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception exception) {
            StaffJoinNotifierClient.LOGGER.error("Could not save config {}.", path, exception);
        }
    }
}
