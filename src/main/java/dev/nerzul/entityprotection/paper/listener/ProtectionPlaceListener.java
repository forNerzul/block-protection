package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.policy.AutoProtectPolicy;
import dev.nerzul.entityprotection.domain.policy.ProtectableCategory;
import dev.nerzul.entityprotection.domain.policy.ProtectableTypePolicy;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
import dev.nerzul.entityprotection.paper.config.ProtectionConfig;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import dev.nerzul.entityprotection.paper.util.DoorHalves;
import dev.nerzul.entityprotection.paper.util.PlayerNames;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Auto-protects a just-placed block when its material is protectable (always
 * for containers, opt-in via {@code protect-doors} for doors/trapdoors/fence
 * gates). Handles the two "two physical blocks, one protection" cases:
 * double chests (blocks whichever half would join a foreign chest) and doors
 * (protects both the top and bottom half together).
 */
public final class ProtectionPlaceListener implements Listener {

    private final ProtectionService protectionService;
    private final ProtectableTypePolicy classifier;
    private final ProtectionConfig config;
    private final Messages messages;

    public ProtectionPlaceListener(ProtectionService protectionService, ProtectableTypePolicy classifier,
                                    ProtectionConfig config, Messages messages) {
        this.protectionService = protectionService;
        this.classifier = classifier;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        ProtectableCategory category = classifier.classify(block.getType().name());
        if (!AutoProtectPolicy.isAutoProtectable(category, config.protectDoors())) {
            return;
        }

        UUID owner = event.getPlayer().getUniqueId();
        BlockPosition position = BlockPositions.of(block);

        if (category == ProtectableCategory.CONTAINER && block.getBlockData() instanceof Chest chestData) {
            handleChestPlacement(event, block, chestData, position, owner);
            return;
        }

        protectionService.protect(position, owner);

        if (category == ProtectableCategory.DOOR) {
            Block otherHalf = DoorHalves.otherHalfOrNull(block);
            if (otherHalf != null) {
                protectionService.protect(BlockPositions.of(otherHalf), owner);
            }
        }

        event.getPlayer().sendMessage(messages.protectedPlace());
    }

    private void handleChestPlacement(BlockPlaceEvent event, Block block, Chest chestData,
                                       BlockPosition position, UUID owner) {
        if (chestData.getType() == Chest.Type.SINGLE) {
            protectionService.protect(position, owner);
            event.getPlayer().sendMessage(messages.protectedPlace());
            return;
        }

        Block neighbor = findJoinedNeighbor(block, chestData);
        if (neighbor == null) {
            // Defensive fallback: blockdata says joined but no matching neighbor was found.
            protectionService.protect(position, owner);
            event.getPlayer().sendMessage(messages.protectedPlace());
            return;
        }

        Optional<Protection> neighborProtection = protectionService.find(BlockPositions.of(neighbor));
        if (neighborProtection.isPresent() && !neighborProtection.get().isOwner(owner)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage(messages.noAccess(PlayerNames.resolve(neighborProtection.get().owner())));
            return;
        }

        if (neighborProtection.isPresent()) {
            protectionService.protectLike(position, neighborProtection.get());
        } else {
            protectionService.protect(position, owner);
        }
        event.getPlayer().sendMessage(messages.protectedPlace());
    }

    /**
     * Scans the four cardinal neighbors for the other half of the double chest
     * Minecraft just formed by this placement (same material, joined type,
     * matching facing).
     */
    private Block findJoinedNeighbor(Block block, Chest chestData) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block candidate = block.getRelative(face);
            if (candidate.getType() != block.getType()) {
                continue;
            }
            if (!(candidate.getBlockData() instanceof Chest candidateData)) {
                continue;
            }
            if (candidateData.getType() == Chest.Type.SINGLE) {
                continue;
            }
            if (candidateData.getFacing() != chestData.getFacing()) {
                continue;
            }
            return candidate;
        }
        return null;
    }
}
