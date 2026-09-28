package dev.nerzul.blockprotection.domain.policy;

/**
 * Pure decision of whether a just-placed block of the given category should be
 * auto-protected, given the server's {@code protect-doors} configuration flag.
 */
public final class AutoProtectPolicy {

    private AutoProtectPolicy() {
    }

    public static boolean isAutoProtectable(ProtectableCategory category, boolean protectDoors) {
        return switch (category) {
            case CONTAINER -> true;
            case DOOR, TRAPDOOR, FENCE_GATE -> protectDoors;
            case NOT_PROTECTABLE -> false;
        };
    }
}
