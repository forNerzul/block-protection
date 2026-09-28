package dev.nerzul.entityprotection.paper.command;

import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.policy.AutoProtectPolicy;
import dev.nerzul.entityprotection.domain.policy.ProtectableTypePolicy;
import dev.nerzul.entityprotection.domain.service.ModificationResult;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
import dev.nerzul.entityprotection.paper.config.ProtectionConfig;
import dev.nerzul.entityprotection.paper.listener.ProtectionAccessListener;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import dev.nerzul.entityprotection.paper.util.DoorHalves;
import dev.nerzul.entityprotection.paper.util.PlayerNames;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /protect trust|untrust|trustall|untrustall <player>} and
 * {@code /protect info|remove}, always acting on the block the player is
 * looking at (within {@link #TARGET_RANGE} blocks).
 */
public final class ProtectCommand implements CommandExecutor {

    private static final Logger LOGGER = Logger.getLogger(ProtectCommand.class.getName());
    private static final int TARGET_RANGE = 5;
    private static final String USAGE = ChatColor.RED
            + "Usage: /protect <trust|untrust|trustall|untrustall> <player> | /protect <info|remove>";

    private final ProtectionService protectionService;
    private final ProtectableTypePolicy classifier;
    private final ProtectionConfig config;
    private final Messages messages;

    public ProtectCommand(ProtectionService protectionService, ProtectableTypePolicy classifier,
                           ProtectionConfig config, Messages messages) {
        this.protectionService = protectionService;
        this.classifier = classifier;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(USAGE);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "info" -> handleInfo(player);
                case "remove" -> handleRemove(player);
                case "trust" -> handleTrust(player, args);
                case "untrust" -> handleUntrust(player, args);
                case "trustall" -> handleTrustAll(player, args);
                case "untrustall" -> handleUntrustAll(player, args);
                default -> player.sendMessage(USAGE);
            }
        } catch (RuntimeException e) {
            // Fail closed: an unexpected storage error must never look like success.
            LOGGER.log(Level.SEVERE, "Unexpected error handling /protect " + String.join(" ", args), e);
            player.sendMessage(messages.storageError());
        }
        return true;
    }

    private void handleInfo(Player player) {
        Block block = requireTarget(player);
        if (block == null) {
            return;
        }
        Optional<Protection> protection = findOrExplain(player, block);
        if (protection.isEmpty()) {
            return;
        }
        Protection value = protection.get();
        player.sendMessage(messages.infoHeader());
        player.sendMessage(messages.infoOwner(PlayerNames.resolve(value.owner())));
        player.sendMessage(messages.infoTrusted(value.trustedPlayers(), PlayerNames::resolve));
    }

    private void handleRemove(Player player) {
        Block block = requireTarget(player);
        if (block == null) {
            return;
        }
        Optional<Protection> protection = findOrExplain(player, block);
        if (protection.isEmpty()) {
            return;
        }
        boolean adminBypass = player.hasPermission(ProtectionAccessListener.ADMIN_PERMISSION);
        ModificationResult result = protectionService.removeProtection(
                BlockPositions.of(block), player.getUniqueId(), adminBypass);

        if (result == ModificationResult.SUCCESS) {
            Block otherHalf = DoorHalves.otherHalfOrNull(block);
            if (otherHalf != null && otherHalf.getType() == block.getType()) {
                protectionService.find(BlockPositions.of(otherHalf))
                        .filter(protection.get()::sharesOwnerWith)
                        .ifPresent(ignored -> protectionService.forceRemoveProtection(BlockPositions.of(otherHalf)));
            }
        }
        respond(player, result, messages.removed());
    }

    private void handleTrust(Player player, String[] args) {
        UUID target = requirePlayerArgument(player, args);
        if (target == null) {
            return;
        }
        Block block = requireTarget(player);
        if (block == null) {
            return;
        }
        if (findOrExplain(player, block).isEmpty()) {
            return;
        }
        boolean adminBypass = player.hasPermission(ProtectionAccessListener.ADMIN_PERMISSION);
        ModificationResult result = protectionService.trust(
                BlockPositions.of(block), player.getUniqueId(), target, adminBypass);
        applyToOtherHalfIfDoor(block, otherHalf ->
                protectionService.trust(BlockPositions.of(otherHalf), player.getUniqueId(), target, adminBypass), result);
        respond(player, result, messages.trustSingleAdded(args[1]));
    }

    private void handleUntrust(Player player, String[] args) {
        UUID target = requirePlayerArgument(player, args);
        if (target == null) {
            return;
        }
        Block block = requireTarget(player);
        if (block == null) {
            return;
        }
        if (findOrExplain(player, block).isEmpty()) {
            return;
        }
        boolean adminBypass = player.hasPermission(ProtectionAccessListener.ADMIN_PERMISSION);
        ModificationResult result = protectionService.untrust(
                BlockPositions.of(block), player.getUniqueId(), target, adminBypass);
        applyToOtherHalfIfDoor(block, otherHalf ->
                protectionService.untrust(BlockPositions.of(otherHalf), player.getUniqueId(), target, adminBypass), result);
        respond(player, result, messages.trustSingleRemoved(args[1]));
    }

    private void handleTrustAll(Player player, String[] args) {
        UUID target = requirePlayerArgument(player, args);
        if (target == null) {
            return;
        }
        ModificationResult result = protectionService.trustAll(player.getUniqueId(), target);
        respond(player, result, messages.trustAllAdded(args[1]));
    }

    private void handleUntrustAll(Player player, String[] args) {
        UUID target = requirePlayerArgument(player, args);
        if (target == null) {
            return;
        }
        ModificationResult result = protectionService.untrustAll(player.getUniqueId(), target);
        respond(player, result, messages.trustAllRemoved(args[1]));
    }

    private void applyToOtherHalfIfDoor(Block block, java.util.function.Consumer<Block> action, ModificationResult primaryResult) {
        if (primaryResult != ModificationResult.SUCCESS) {
            return;
        }
        Block otherHalf = DoorHalves.otherHalfOrNull(block);
        if (otherHalf != null) {
            action.accept(otherHalf);
        }
    }

    private void respond(Player player, ModificationResult result, String successMessage) {
        switch (result) {
            case SUCCESS -> player.sendMessage(successMessage);
            case NOT_PROTECTED -> player.sendMessage(messages.notProtected());
            case FORBIDDEN -> player.sendMessage(messages.notOwner());
            case STORAGE_ERROR -> player.sendMessage(messages.storageError());
        }
    }

    /**
     * Looks up the protection at {@code block}, or explains why there is none:
     * "not protectable" for a material this plugin never protects (or doors
     * while {@code protect-doors} is off), "not protected" otherwise.
     */
    private Optional<Protection> findOrExplain(Player player, Block block) {
        Optional<Protection> protection = protectionService.find(BlockPositions.of(block));
        if (protection.isPresent()) {
            return protection;
        }
        if (isCurrentlyProtectable(block)) {
            player.sendMessage(messages.notProtected());
        } else {
            player.sendMessage(messages.notProtectable());
        }
        return Optional.empty();
    }

    private boolean isCurrentlyProtectable(Block block) {
        var category = classifier.classify(block.getType().name());
        return AutoProtectPolicy.isAutoProtectable(category, config.protectDoors());
    }

    private Block requireTarget(Player player) {
        Block block = player.getTargetBlockExact(TARGET_RANGE);
        if (block == null) {
            player.sendMessage(messages.noTargetBlock());
        }
        return block;
    }

    private UUID requirePlayerArgument(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(USAGE);
            return null;
        }
        UUID uuid = PlayerNames.resolveUuid(args[1]);
        if (uuid == null) {
            player.sendMessage(messages.playerNotFound());
        }
        return uuid;
    }
}
