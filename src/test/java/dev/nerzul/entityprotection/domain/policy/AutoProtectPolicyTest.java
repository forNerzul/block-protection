package dev.nerzul.entityprotection.domain.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoProtectPolicyTest {

    @Test
    void containersAreAlwaysAutoProtected() {
        assertTrue(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.CONTAINER, false));
        assertTrue(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.CONTAINER, true));
    }

    @Test
    void doorFamilyFollowsTheProtectDoorsSetting() {
        assertFalse(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.DOOR, false));
        assertTrue(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.DOOR, true));

        assertFalse(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.TRAPDOOR, false));
        assertTrue(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.TRAPDOOR, true));

        assertFalse(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.FENCE_GATE, false));
        assertTrue(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.FENCE_GATE, true));
    }

    @Test
    void notProtectableIsNeverAutoProtected() {
        assertFalse(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.NOT_PROTECTABLE, false));
        assertFalse(AutoProtectPolicy.isAutoProtectable(ProtectableCategory.NOT_PROTECTABLE, true));
    }
}
