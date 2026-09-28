package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import dev.nerzul.entityprotection.paper.util.PlayerNames;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;

import static dev.nerzul.entityprotection.paper.listener.ProtectionAccessListener.ADMIN_PERMISSION;

/**
 * Denies breaking a protected block to anyone without access, and cleans up
 * protection data (both halves, for a door) once an authorized break goes through.
 */
public final class ProtectionBreakListener implements Listener {

    private final ProtectionService protectionService;
    private final Messages messages;

    public ProtectionBreakListener(ProtectionService protectionService, Messages messages) {
        this.protectionService = protectionService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockPosition position = BlockPositions.of(block);
        Optional<Protection> protection = protectionService.find(position);
        if (protection.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        boolean adminBypass = player.hasPermission(ADMIN_PERMISSION);
        if (!protectionService.canAccess(position, player.getUniqueId(), adminBypass)) {
            event.setCancelled(true);
            player.sendMessage(messages.noAccess(PlayerNames.resolve(protection.get().owner())));
            return;
        }

        protectionService.forceRemoveProtection(position);
        cleanupDoorPair(block);
    }

    private void cleanupDoorPair(Block block) {
        if (!(block.getBlockData() instanceof Bisected bisected)) {
            return;
        }
        Block otherHalf = bisected.getHalf() == Bisected.Half.BOTTOM
                ? block.getRelative(BlockFace.UP)
                : block.getRelative(BlockFace.DOWN);
        protectionService.forceRemoveProtection(BlockPositions.of(otherHalf));
    }
}
