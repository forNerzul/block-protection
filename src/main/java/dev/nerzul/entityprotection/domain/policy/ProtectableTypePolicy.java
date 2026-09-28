package dev.nerzul.entityprotection.domain.policy;

/**
 * Port: classifies a material (identified by its Bukkit enum name, e.g. {@code "CHEST"})
 * into a {@link ProtectableCategory}. Implemented by a Paper adapter that has real
 * knowledge of the {@code Material} enum and block tags; the domain stays free of
 * Paper imports and only reasons about the resulting category.
 */
public interface ProtectableTypePolicy {

    ProtectableCategory classify(String materialName);
}
