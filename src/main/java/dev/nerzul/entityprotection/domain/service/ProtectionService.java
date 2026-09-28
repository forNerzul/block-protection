package dev.nerzul.entityprotection.domain.service;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.policy.AccessPolicy;
import dev.nerzul.entityprotection.domain.port.GlobalTrustRepository;
import dev.nerzul.entityprotection.domain.port.ProtectionRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application service orchestrating protection use cases against the two ports.
 * This is where the "only owner or admin may modify" business rule lives, so
 * every adapter (commands, listeners) shares the same behavior.
 */
public final class ProtectionService {

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

    public boolean isProtected(BlockPosition position) {
        return protectionRepository.find(position).isPresent();
    }

    public boolean canAccess(BlockPosition position, UUID requester, boolean adminBypass) {
        Optional<Protection> protection = protectionRepository.find(position);
        if (protection.isEmpty()) {
            return true;
        }
        Set<UUID> globallyTrusted = globalTrustRepository.trustedBy(protection.get().owner());
        return AccessPolicy.canAccess(requester, protection.get(), globallyTrusted, adminBypass);
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
        globalTrustRepository.trust(owner, target);
        return ModificationResult.SUCCESS;
    }

    public ModificationResult untrustAll(UUID owner, UUID target) {
        globalTrustRepository.untrust(owner, target);
        return ModificationResult.SUCCESS;
    }

    private boolean canModify(Protection protection, UUID requester, boolean adminBypass) {
        return adminBypass || protection.isOwner(requester);
    }
}
