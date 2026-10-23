package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.airahub.interophub.model.Organization;

/**
 * Organization type codes, type-ahead ranking, and the checks for adding an
 * organization inline. Types are codes, not a lookup table, so a new type does
 * not need a schema change.
 */
public final class OrganizationDirectory {

    public record OrganizationType(String code, String label) {
    }

    public static final List<OrganizationType> TYPES = List.of(
            new OrganizationType("EHR", "EHR"),
            new OrganizationType("JURISDICTION", "Jurisdiction"),
            new OrganizationType("IIS_VENDOR", "IIS Vendor"),
            new OrganizationType("CDS", "CDS"),
            new OrganizationType("SCHOOL_INFORMATION_SYSTEM", "School Information System"),
            new OrganizationType("TESTING_VENDOR", "Testing Vendor"),
            new OrganizationType("STATE_AGENCY", "State Agency"),
            new OrganizationType("FEDERAL", "Federal"),
            new OrganizationType("HEALTH_SYSTEM", "Health System"),
            new OrganizationType("PHARMACY", "Pharmacy"),
            new OrganizationType("NATIONAL_ORG", "National Organization"),
            new OrganizationType("CONSULTANT", "Consultant"),
            new OrganizationType("OTHER", "Other"));

    public enum TypeMatchKind {
        BLANK,
        KNOWN,
        UNKNOWN
    }

    public record ResolvedType(TypeMatchKind kind, String code) {
    }

    private OrganizationDirectory() {
    }

    public static boolean isKnown(String code) {
        return find(code) != null;
    }

    public static String label(String code) {
        OrganizationType type = find(code);
        return type == null ? (code == null ? "" : code) : type.label();
    }

    public static ResolvedType resolveImportedType(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return new ResolvedType(TypeMatchKind.BLANK, null);
        }
        String compact = compact(trimmed);
        for (OrganizationType type : TYPES) {
            if (compact.equals(compact(type.code())) || compact.equals(compact(type.label()))) {
                return new ResolvedType(TypeMatchKind.KNOWN, type.code());
            }
        }
        return new ResolvedType(TypeMatchKind.UNKNOWN, null);
    }

    /**
     * @return an error message, or null when the name and type can be saved
     */
    public static String validationError(String name, String typeCode) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            return "Organization name is required.";
        }
        if (trimmed.length() > 200) {
            return "Organization name must be 200 characters or less.";
        }
        if (!isKnown(typeCode)) {
            return "Choose an organization type.";
        }
        return null;
    }

    public static String normalizeWebsite(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Website must be 500 characters or less.");
        }
        return trimmed;
    }

    public static String normalizeName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Exact name first, then names that start with the query, then names that
     * contain it. Inactive organizations are left out.
     */
    public static List<Organization> rank(List<Organization> organizations, String query, int limit) {
        if (organizations == null || limit <= 0) {
            return List.of();
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        record Scored(Organization organization, int score, String name) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Organization organization : organizations) {
            if (organization == null || !Boolean.TRUE.equals(organization.getActive())) {
                continue;
            }
            String name = organization.getOrganizationName() == null ? "" : organization.getOrganizationName();
            String lower = name.toLowerCase(Locale.ROOT);
            int score;
            if (lower.equals(needle)) {
                score = 0;
            } else if (lower.startsWith(needle)) {
                score = 1;
            } else if (lower.contains(needle)) {
                score = 2;
            } else {
                continue;
            }
            scored.add(new Scored(organization, score, lower));
        }
        scored.sort((a, b) -> {
            int byScore = Integer.compare(a.score(), b.score());
            return byScore != 0 ? byScore : a.name().compareTo(b.name());
        });
        List<Organization> matches = new ArrayList<>();
        for (Scored item : scored) {
            if (matches.size() >= limit) {
                break;
            }
            matches.add(item.organization());
        }
        return matches;
    }

    public static List<String> typeOrder() {
        List<String> codes = new ArrayList<>();
        for (OrganizationType type : TYPES) {
            codes.add(type.code());
        }
        return codes;
    }

    private static OrganizationType find(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (OrganizationType type : TYPES) {
            if (type.code().equals(code.trim())) {
                return type;
            }
        }
        return null;
    }

    private static String compact(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Keeps the canonical type order when summarizing committed organizations. */
    public static List<String> sortTypeCodes(Collection<String> codes) {
        Map<String, Integer> order = new LinkedHashMap<>();
        int index = 0;
        for (OrganizationType type : TYPES) {
            order.put(type.code(), index++);
        }
        List<String> sorted = new ArrayList<>();
        if (codes != null) {
            for (String code : codes) {
                if (code != null && !sorted.contains(code)) {
                    sorted.add(code);
                }
            }
        }
        sorted.sort((a, b) -> {
            int ao = order.getOrDefault(a, Integer.MAX_VALUE);
            int bo = order.getOrDefault(b, Integer.MAX_VALUE);
            if (ao != bo) {
                return Integer.compare(ao, bo);
            }
            return a.compareTo(b);
        });
        return sorted;
    }
}
