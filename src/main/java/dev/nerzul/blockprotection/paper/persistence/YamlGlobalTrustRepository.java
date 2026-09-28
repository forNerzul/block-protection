package dev.nerzul.blockprotection.paper.persistence;

import dev.nerzul.blockprotection.domain.port.GlobalTrustRepository;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper adapter storing, per owner, the set of players they globally trust on
 * every one of their protections. Backed by a single YAML file in the plugin's
 * data folder so it survives restarts independently of any chunk being loaded.
 *
 * <p>Robustness: invalid UUID entries are skipped with a warning rather than
 * failing the whole load; a file that fails to parse is never overwritten (a
 * timestamped backup is kept next to it and every write is refused until an
 * operator fixes or replaces it); every write is atomic (temp file + move);
 * and an in-memory change is rolled back if persisting it fails.
 */
public final class YamlGlobalTrustRepository implements GlobalTrustRepository {

    private static final String ROOT_SECTION = "owners";

    private final File file;
    private final Logger logger;
    private final YamlConfiguration configuration;
    private volatile boolean corrupted;

    public YamlGlobalTrustRepository(File file) {
        this(file, Logger.getLogger(YamlGlobalTrustRepository.class.getName()));
    }

    public YamlGlobalTrustRepository(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.configuration = load();
    }

    private YamlConfiguration load() {
        YamlConfiguration config = new YamlConfiguration();
        if (!file.exists()) {
            return config;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            config.loadFromString(content);
        } catch (IOException | InvalidConfigurationException e) {
            corrupted = true;
            logger.log(Level.SEVERE, "Failed to parse " + file
                    + "; refusing to overwrite it until it is fixed or restored. A copy was backed up alongside it.", e);
            backUpCorruptFile();
        }
        return config;
    }

    private void backUpCorruptFile() {
        Path backup = file.toPath().resolveSibling(file.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.copy(file.toPath(), backup, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Additionally failed to back up the corrupt file to " + backup, e);
        }
    }

    @Override
    public synchronized Set<UUID> trustedBy(UUID owner) {
        return parseUuids(configuration.getStringList(ROOT_SECTION + "." + owner));
    }

    @Override
    public synchronized boolean trust(UUID owner, UUID trustedPlayer) {
        List<String> previous = configuration.getStringList(ROOT_SECTION + "." + owner);
        Set<UUID> current = parseUuids(previous);
        if (!current.add(trustedPlayer)) {
            return true;
        }
        return write(owner, current, previous);
    }

    @Override
    public synchronized boolean untrust(UUID owner, UUID trustedPlayer) {
        List<String> previous = configuration.getStringList(ROOT_SECTION + "." + owner);
        Set<UUID> current = parseUuids(previous);
        if (!current.remove(trustedPlayer)) {
            return true;
        }
        return write(owner, current, previous);
    }

    private Set<UUID> parseUuids(List<String> raw) {
        Set<UUID> result = new HashSet<>();
        for (String value : raw) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Skipping invalid UUID '" + value + "' in " + file, e);
            }
        }
        return result;
    }

    /**
     * Applies {@code trusted} in memory and persists it; on any failure (a
     * previously corrupt file, or an I/O error while saving) the in-memory
     * value is rolled back to {@code previousValue} and {@code false} is
     * returned, so the caller can report the failure instead of assuming success.
     */
    private boolean write(UUID owner, Set<UUID> trusted, List<String> previousValue) {
        String path = ROOT_SECTION + "." + owner;
        if (corrupted) {
            logger.severe("Refusing to save global trust data: " + file + " failed to parse earlier and was never overwritten.");
            return false;
        }
        configuration.set(path, trusted.stream().map(UUID::toString).toList());
        if (persist()) {
            return true;
        }
        configuration.set(path, previousValue.isEmpty() ? null : previousValue);
        return false;
    }

    private boolean persist() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.log(Level.WARNING, "Could not create directory {0}", parent);
            }
            Path tempFile = Files.createTempFile(parent != null ? parent.toPath() : file.toPath().getParent(),
                    "global-trust", ".tmp");
            try {
                Files.writeString(tempFile, configuration.saveToString(), StandardCharsets.UTF_8);
                Files.move(tempFile, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tempFile);
            }
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save global trust data to " + file, e);
            return false;
        }
    }
}
