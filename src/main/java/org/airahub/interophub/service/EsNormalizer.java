package org.airahub.interophub.service;

/**
 * Shared email normalization utility for the ES interest layer.
 * All ES tables that store email_normalized must use this method
 * to stay consistent with the existing auth_user normalization pattern.
 */
public final class EsNormalizer {

    private EsNormalizer() {
    }

    /**
     * Normalizes an email address: trims whitespace and lowercases.
     * Returns null if the input is null or blank after trimming.
     * Mirrors the logic in UserDao.findByEmail.
     */
    public static String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
