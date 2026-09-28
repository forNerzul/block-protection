package dev.nerzul.blockprotection;

import dev.nerzul.blockprotection.domain.policy.ProtectableTypePolicy;
import dev.nerzul.blockprotection.domain.port.GlobalTrustRepository;
import dev.nerzul.blockprotection.domain.port.ProtectionRepository;
import dev.nerzul.blockprotection.domain.service.ProtectionService;
import dev.nerzul.blockprotection.paper.command.ProtectCommand;
import dev.nerzul.blockprotection.paper.command.ProtectTabCompleter;
import dev.nerzul.blockprotection.paper.config.Messages;
import dev.nerzul.blockprotection.paper.config.ProtectionConfig;
import dev.nerzul.blockprotection.paper.listener.ProtectionAccessListener;
import dev.nerzul.blockprotection.paper.listener.ProtectionBreakListener;
import dev.nerzul.blockprotection.paper.listener.ProtectionBurnListener;
import dev.nerzul.blockprotection.paper.listener.ProtectionExplosionListener;
import dev.nerzul.blockprotection.paper.listener.ProtectionHopperListener;
import dev.nerzul.blockprotection.paper.listener.ProtectionPhysicsCleanupListener;
import dev.nerzul.blockprotection.paper.listener.ProtectionPistonListener;
import dev.nerzul.blockprotection.paper.listener.ProtectionPlaceListener;
import dev.nerzul.blockprotection.paper.persistence.ChunkProtectionRepository;
import dev.nerzul.blockprotection.paper.persistence.YamlGlobalTrustRepository;
import dev.nerzul.blockprotection.paper.policy.PaperProtectableTypePolicy;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Entry point of the BlockProtection plugin: wires the pure domain
 * ({@link ProtectionService} over its two ports) to the Paper adapters
 * (chunk-backed protection storage, YAML global trust, listeners, and the
 * {@code /protect} command).
 */
public final class BlockProtectionPlugin extends JavaPlugin {

    private ProtectionService protectionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ProtectableTypePolicy classifier = new PaperProtectableTypePolicy();
        ProtectionConfig config = new ProtectionConfig(getConfig());
        Messages messages = new Messages(getConfig());

        ProtectionRepository protectionRepository = new ChunkProtectionRepository(this, classifier, config);
        GlobalTrustRepository globalTrustRepository =
                new YamlGlobalTrustRepository(new File(getDataFolder(), "global-trust.yml"), getLogger());
        protectionService = new ProtectionService(protectionRepository, globalTrustRepository);

        registerListeners(classifier, config, messages);
        registerCommand(classifier, config, messages);

        getLogger().info("BlockProtection enabled.");
    }

    private void registerCommand(ProtectableTypePolicy classifier, ProtectionConfig config, Messages messages) {
        var protectCommand = getCommand("protect");
        if (protectCommand == null) {
            getLogger().severe("Command 'protect' is missing from plugin.yml; /protect will not work.");
            return;
        }
        protectCommand.setExecutor(new ProtectCommand(protectionService, classifier, config, messages));
        protectCommand.setTabCompleter(new ProtectTabCompleter());
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
        pluginManager.registerEvents(new ProtectionPhysicsCleanupListener(protectionService), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("BlockProtection disabled.");
    }
}
