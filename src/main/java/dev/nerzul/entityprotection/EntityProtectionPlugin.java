package dev.nerzul.entityprotection;

import dev.nerzul.entityprotection.domain.policy.ProtectableTypePolicy;
import dev.nerzul.entityprotection.domain.port.GlobalTrustRepository;
import dev.nerzul.entityprotection.domain.port.ProtectionRepository;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
import dev.nerzul.entityprotection.paper.config.ProtectionConfig;
import dev.nerzul.entityprotection.paper.listener.ProtectionAccessListener;
import dev.nerzul.entityprotection.paper.listener.ProtectionBreakListener;
import dev.nerzul.entityprotection.paper.listener.ProtectionBurnListener;
import dev.nerzul.entityprotection.paper.listener.ProtectionExplosionListener;
import dev.nerzul.entityprotection.paper.listener.ProtectionHopperListener;
import dev.nerzul.entityprotection.paper.listener.ProtectionPistonListener;
import dev.nerzul.entityprotection.paper.listener.ProtectionPlaceListener;
import dev.nerzul.entityprotection.paper.persistence.ChunkProtectionRepository;
import dev.nerzul.entityprotection.paper.persistence.YamlGlobalTrustRepository;
import dev.nerzul.entityprotection.paper.policy.PaperProtectableTypePolicy;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Entry point of the EntityProtection plugin: wires the pure domain
 * ({@link ProtectionService} over its two ports) to the Paper adapters
 * (chunk-backed protection storage, YAML global trust, listeners, and the
 * {@code /protect} command).
 */
public final class EntityProtectionPlugin extends JavaPlugin {

    private ProtectionService protectionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ProtectionRepository protectionRepository = new ChunkProtectionRepository(this);
        GlobalTrustRepository globalTrustRepository =
                new YamlGlobalTrustRepository(new File(getDataFolder(), "global-trust.yml"), getLogger());
        protectionService = new ProtectionService(protectionRepository, globalTrustRepository);

        ProtectableTypePolicy classifier = new PaperProtectableTypePolicy();
        ProtectionConfig config = new ProtectionConfig(getConfig());
        Messages messages = new Messages(getConfig());

        registerListeners(classifier, config, messages);

        getLogger().info("EntityProtection enabled.");
    }

    private void registerListeners(ProtectableTypePolicy classifier, ProtectionConfig config, Messages messages) {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ProtectionPlaceListener(protectionService, classifier, config, messages), this);
        pluginManager.registerEvents(new ProtectionAccessListener(protectionService, messages), this);
        pluginManager.registerEvents(new ProtectionBreakListener(protectionService, messages), this);
        pluginManager.registerEvents(new ProtectionHopperListener(protectionService), this);
        pluginManager.registerEvents(new ProtectionExplosionListener(protectionService), this);
        pluginManager.registerEvents(new ProtectionPistonListener(protectionService), this);
        pluginManager.registerEvents(new ProtectionBurnListener(protectionService), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("EntityProtection disabled.");
    }
}
