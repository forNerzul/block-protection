package dev.nerzul.entityprotection.domain.service;

/**
 * Outcome of an attempt to modify a protection (trust/untrust/remove).
 */
public enum ModificationResult {
    SUCCESS,
    NOT_PROTECTED,
    FORBIDDEN
}
