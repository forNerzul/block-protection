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
import dev.nerzul.entityprotection.paper.util.ChestJoinDirection;
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
import java.util.logging.Logger;

/**
 * Auto-protects a just-placed block when its material is protectable (always
 * for containers, opt-in via {@code protect-doors} for doors/trapdoors/fence
 * gates). Handles the two "two physical blocks, one protection" cases:
 * double chests (blocks whichever half would join a foreign chest) and doors
 * (protects both the top and bottom half together).
 */
public final class ProtectionPlaceListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(ProtectionPlaceListener.class.getName());

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
            boolean adminBypass = event.getPlayer().hasPermission(ProtectionAccessListener.ADMIN_PERMISSION);
            handleChestPlacement(event, block, chestData, position, owner, adminBypass);
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
                                       BlockPosition position, UUID owner, boolean adminBypass) {
        if (chestData.getType() == Chest.Type.SINGLE) {
            protectionService.protect(position, owner);
            event.getPlayer().sendMessage(messages.protectedPlace());
            return;
        }

        Block neighbor = findJoinedNeighbor(block, chestData);
        if (neighbor == null) {
            // chestData.getType() says this placement joined something, but the
            // deterministic partner position (see findJoinedNeighbor) does not
            // hold a matching chest. This should not happen; fail closed rather
            // than silently protecting it as an unverified, possibly-joined
            // single chest (that was the previous, fail-open fallback).
            LOGGER.severe("Chest at " + position + " reports being joined (" + chestData.getType()
                    + ") but its deterministic partner position does not hold a matching chest; denying the placement.");
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.storageError());
            return;
        }

        Optional<Protection> neighborProtection = protectionService.find(BlockPositions.of(neighbor));
        if (neighborProtection.isPresent() && !neighborProtection.get().isOwner(owner) && !adminBypass) {
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

    /** Finds the joined neighbor via {@link ChestJoinDirection}, not the neighbor's own (possibly stale) {@code Chest.Type}. */
    private Block findJoinedNeighbor(Block block, Chest chestData) {
        BlockFace direction = ChestJoinDirection.partnerDirection(chestData.getFacing(), chestData.getType());
        Block candidate = block.getRelative(direction);
        if (candidate.getType() != block.getType()) {
            return null;
        }
        if (!(candidate.getBlockData() instanceof Chest candidateData)) {
            return null;
        }
        if (candidateData.getFacing() != chestData.getFacing()) {
            return null;
        }
        return candidate;
    }
}
