package dev.nerzul.blockprotection.paper.policy;

import dev.nerzul.blockprotection.domain.policy.ProtectableCategory;
import dev.nerzul.blockprotection.domain.policy.ProtectableTypePolicy;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Set;

/**
 * Paper adapter implementing the {@link ProtectableTypePolicy} port using the
 * real {@code Material} enum and block {@code Tag}s resolved from the actual
 * paper-api 26.2 jar (verified with {@code javap}, not guessed): copper chests,
 * copper doors/trapdoors and the smoker all exist in this version and are
 * covered here via tags rather than a hand-picked material list, so future
 * material additions of the same family are picked up automatically.
 */
public final class PaperProtectableTypePolicy implements ProtectableTypePolicy {

    /**
     * Containers with no dedicated block tag in the Bukkit API: the classic
     * chest/trapped chest (copper chest variants are covered by
     * {@link Tag#COPPER_CHESTS}), the barrel, the three furnace variants, the
     * three "hopper family" redstone containers, and the brewing stand
     * (confirmed a real, non-legacy {@code Material} implementing
     * {@code org.bukkit.block.Container} in the 26.2 jar via {@code javap}) so
     * an owner's own hopper can feed it. {@code ENDER_CHEST} is intentionally
     * excluded: its inventory is per-player, not per-block, so per-owner
     * protection does not apply to it.
     */
    private static final Set<Material> CONTAINER_MATERIALS = Set.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER,
            Material.BREWING_STAND
    );

    @Override
    public ProtectableCategory classify(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return ProtectableCategory.NOT_PROTECTABLE;
        }

        if (CONTAINER_MATERIALS.contains(material)
                || Tag.SHULKER_BOXES.isTagged(material)
                || Tag.COPPER_CHESTS.isTagged(material)) {
            return ProtectableCategory.CONTAINER;
        }
        if (Tag.DOORS.isTagged(material)) {
            return ProtectableCategory.DOOR;
        }
        if (Tag.TRAPDOORS.isTagged(material)) {
            return ProtectableCategory.TRAPDOOR;
        }
        if (Tag.FENCE_GATES.isTagged(material)) {
            return ProtectableCategory.FENCE_GATE;
        }
        return ProtectableCategory.NOT_PROTECTABLE;
    }
}
