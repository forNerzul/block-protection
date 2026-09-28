package dev.nerzul.entityprotection.domain.policy;

/**
 * Coarse classification of a block material for auto-protection purposes.
 */
public enum ProtectableCategory {
    /** Chests, barrels, shulker boxes, furnaces, dispensers, droppers, hoppers, etc. */
    CONTAINER,
    DOOR,
    TRAPDOOR,
    FENCE_GATE,
    /** Anything not covered by this plugin. */
    NOT_PROTECTABLE
}
