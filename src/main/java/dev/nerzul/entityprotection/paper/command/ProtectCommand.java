package dev.nerzul.entityprotection.paper.command;

import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.service.ModificationResult;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
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

/**
 * {@code /protect trust|untrust|trustall|untrustall <player>} and
 * {@code /protect info|remove}, always acting on the block the player is
 * looking at (within {@link #TARGET_RANGE} blocks).
 */
public final class ProtectCommand implements CommandExecutor {

    private static final int TARGET_RANGE = 5;
    private static final String USAGE = ChatColor.RED
            + "Usage: /protect <trust|untrust|trustall|untrustall> <player> | /protect <info|remove>";

    private final ProtectionService protectionService;
    private final Messages messages;

    public ProtectCommand(ProtectionService protectionService, Messages messages) {
        this.protectionService = protectionService;
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

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> handleInfo(player);
            case "remove" -> handleRemove(player);
            case "trust" -> handleTrust(player, args);
            case "untrust" -> handleUntrust(player, args);
            case "trustall" -> handleTrustAll(player, args);
            case "untrustall" -> handleUntrustAll(player, args);
            default -> player.sendMessage(USAGE);
        }
        return true;
    }

    private void handleInfo(Player player) {
        Block block = requireTarget(player);
        if (block == null) {
            return;
        }
        Optional<Protection> protection = protectionService.find(BlockPositions.of(block));
        if (protection.isEmpty()) {
            player.sendMessage(messages.notProtected());
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
        boolean adminBypass = player.hasPermission(ProtectionAccessListener.ADMIN_PERMISSION);
        ModificationResult result = protectionService.removeProtection(
                BlockPositions.of(block), player.getUniqueId(), adminBypass);

        if (result == ModificationResult.SUCCESS) {
            Block otherHalf = DoorHalves.otherHalfOrNull(block);
            if (otherHalf != null) {
                protectionService.forceRemoveProtection(BlockPositions.of(otherHalf));
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
        protectionService.trustAll(player.getUniqueId(), target);
        player.sendMessage(messages.trustAllAdded(args[1]));
    }

    private void handleUntrustAll(Player player, String[] args) {
        UUID target = requirePlayerArgument(player, args);
        if (target == null) {
            return;
        }
        protectionService.untrustAll(player.getUniqueId(), target);
        player.sendMessage(messages.trustAllRemoved(args[1]));
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
        }
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
