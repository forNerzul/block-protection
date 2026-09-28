package dev.nerzul.entityprotection.domain.service;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.policy.AccessPolicy;
import dev.nerzul.entityprotection.domain.policy.HopperTransferPolicy;
import dev.nerzul.entityprotection.domain.port.GlobalTrustRepository;
import dev.nerzul.entityprotection.domain.port.ProtectionRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application service orchestrating protection use cases against the two ports.
 * This is where the "only owner or admin may modify" business rule lives, so
 * every adapter (commands, listeners) shares the same behavior.
 */
public final class ProtectionService {

    private static final Logger LOGGER = Logger.getLogger(ProtectionService.class.getName());

    private final ProtectionRepository protectionRepository;
    private final GlobalTrustRepository globalTrustRepository;

    public ProtectionService(ProtectionRepository protectionRepository, GlobalTrustRepository globalTrustRepository) {
        this.protectionRepository = Objects.requireNonNull(protectionRepository);
        this.globalTrustRepository = Objects.requireNonNull(globalTrustRepository);
    }

    public Protection protect(BlockPosition position, UUID owner) {
        Protection protection = new Protection(owner);
        protectionRepository.save(position, protection);
        return protection;
    }

    /**
     * Protects {@code position} inheriting the owner and trusted players of an
     * existing protection (used when joining a double chest / door half).
     */
    public Protection protectLike(BlockPosition position, Protection existing) {
        Protection protection = new Protection(existing.owner(), existing.trustedPlayers());
        protectionRepository.save(position, protection);
        return protection;
    }

    public Optional<Protection> find(BlockPosition position) {
        return protectionRepository.find(position);
    }

    /**
     * Fails closed: if the lookup throws unexpectedly (e.g. corrupt storage),
     * this returns {@code true} — "assume protected" is the safe default for
     * callers deciding whether to preserve a block from destruction.
     */
    public boolean isProtected(BlockPosition position) {
        return safely(() -> protectionRepository.find(position).isPresent(), true, "isProtected(" + position + ")");
    }

    /**
     * Fails closed: if the lookup throws unexpectedly, this returns {@code false}
     * — "deny access" is the safe default regardless of which action (open, use,
     * break) the caller is gating.
     */
    public boolean canAccess(BlockPosition position, UUID requester, boolean adminBypass) {
        return safely(() -> {
            Optional<Protection> protection = protectionRepository.find(position);
            if (protection.isEmpty()) {
                return true;
            }
            Set<UUID> globallyTrusted = globalTrustRepository.trustedBy(protection.get().owner());
            return AccessPolicy.canAccess(requester, protection.get(), globallyTrusted, adminBypass);
        }, false, "canAccess(" + position + ", " + requester + ")");
    }

    /**
     * Hopper/hopper-minecart bypass prevention (see {@link HopperTransferPolicy}).
     * {@code source}/{@code destination} may be {@code null} when the moving
     * inventory has no block position (e.g. a hopper minecart), which this
     * treats the same as "unprotected". Fails closed: {@code false} on error.
     */
    public boolean canTransfer(BlockPosition source, BlockPosition destination) {
        if (source == null) {
            return true;
        }
        return safely(() -> {
            Optional<Protection> sourceProtection = protectionRepository.find(source);
            if (sourceProtection.isEmpty()) {
                return true;
            }
            Optional<Protection> destinationProtection = destination == null
                    ? Optional.empty()
                    : protectionRepository.find(destination);
            if (destinationProtection.isEmpty()) {
                return false;
            }
            Set<UUID> globallyTrusted = globalTrustRepository.trustedBy(sourceProtection.get().owner());
            return HopperTransferPolicy.isTransferAllowed(sourceProtection.get(), destinationProtection.get(), globallyTrusted);
        }, false, "canTransfer(" + source + " -> " + destination + ")");
    }

    private <T> T safely(Supplier<T> action, T safeFallback, String description) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Protection lookup failed for " + description
                    + "; failing closed with " + safeFallback, e);
            return safeFallback;
        }
    }

    public ModificationResult trust(BlockPosition position, UUID requester, UUID target, boolean adminBypass) {
        Optional<Protection> protection = protectionRepository.find(position);
        if (protection.isEmpty()) {
            return ModificationResult.NOT_PROTECTED;
        }
        if (!canModify(protection.get(), requester, adminBypass)) {
            return ModificationResult.FORBIDDEN;
        }
        protection.get().trust(target);
        protectionRepository.save(position, protection.get());
        return ModificationResult.SUCCESS;
    }

    public ModificationResult untrust(BlockPosition position, UUID requester, UUID target, boolean adminBypass) {
        Optional<Protection> protection = protectionRepository.find(position);
        if (protection.isEmpty()) {
            return ModificationResult.NOT_PROTECTED;
        }
        if (!canModify(protection.get(), requester, adminBypass)) {
            return ModificationResult.FORBIDDEN;
        }
        protection.get().untrust(target);
        protectionRepository.save(position, protection.get());
        return ModificationResult.SUCCESS;
    }

    public ModificationResult removeProtection(BlockPosition position, UUID requester, boolean adminBypass) {
        Optional<Protection> protection = protectionRepository.find(position);
        if (protection.isEmpty()) {
            return ModificationResult.NOT_PROTECTED;
        }
        if (!canModify(protection.get(), requester, adminBypass)) {
            return ModificationResult.FORBIDDEN;
        }
        protectionRepository.remove(position);
        return ModificationResult.SUCCESS;
    }

    /**
     * Removes protection data without an access check (used for cleanup after
     * explosions, fire, or the owner breaking the block).
     */
    public void forceRemoveProtection(BlockPosition position) {
        protectionRepository.remove(position);
    }

    public ModificationResult trustAll(UUID owner, UUID target) {
        return globalTrustRepository.trust(owner, target) ? ModificationResult.SUCCESS : ModificationResult.STORAGE_ERROR;
    }

    public ModificationResult untrustAll(UUID owner, UUID target) {
        return globalTrustRepository.untrust(owner, target) ? ModificationResult.SUCCESS : ModificationResult.STORAGE_ERROR;
    }

    private boolean canModify(Protection protection, UUID requester, boolean adminBypass) {
        return adminBypass || protection.isOwner(requester);
    }
}
