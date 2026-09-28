package dev.nerzul.entityprotection.paper.persistence;

import dev.nerzul.entityprotection.domain.port.GlobalTrustRepository;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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
 */
public final class YamlGlobalTrustRepository implements GlobalTrustRepository {

    private static final String ROOT_SECTION = "owners";

    private final File file;
    private final Logger logger;
    private final YamlConfiguration configuration;

    public YamlGlobalTrustRepository(File file) {
        this(file, Logger.getLogger(YamlGlobalTrustRepository.class.getName()));
    }

    public YamlGlobalTrustRepository(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.configuration = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    @Override
    public synchronized Set<UUID> trustedBy(UUID owner) {
        List<String> stored = configuration.getStringList(ROOT_SECTION + "." + owner);
        Set<UUID> trusted = new HashSet<>();
        for (String raw : stored) {
            trusted.add(UUID.fromString(raw));
        }
        return trusted;
    }

    @Override
    public synchronized void trust(UUID owner, UUID trustedPlayer) {
        Set<UUID> current = trustedBy(owner);
        current.add(trustedPlayer);
        write(owner, current);
    }

    @Override
    public synchronized void untrust(UUID owner, UUID trustedPlayer) {
        Set<UUID> current = trustedBy(owner);
        if (current.remove(trustedPlayer)) {
            write(owner, current);
        }
    }

    private void write(UUID owner, Set<UUID> trusted) {
        List<String> asStrings = trusted.stream().map(UUID::toString).toList();
        configuration.set(ROOT_SECTION + "." + owner, asStrings);
        save();
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.log(Level.WARNING, "Could not create directory {0}", parent);
            }
            configuration.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save global trust data to " + file, e);
        }
    }
}
