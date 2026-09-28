package dev.nerzul.entityprotection.domain.service;

/**
 * Outcome of an attempt to modify a protection (trust/untrust/remove).
 */
public enum ModificationResult {
    SUCCESS,
    NOT_PROTECTED,
    FORBIDDEN,
    /** The change was authorized but persisting it failed (e.g. storage refused a corrupt-data overwrite). */
    STORAGE_ERROR
}
