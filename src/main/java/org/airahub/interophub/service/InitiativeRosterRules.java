package org.airahub.interophub.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;

/**
 * Roster rules that do not touch the database: status changes, who may edit a
 * timeline entry, the representation summary, and the "needs a contact" flag.
 */
public final class InitiativeRosterRules {

    public enum PlannedKind {
        STATUS_CHANGE,
        COMMENT
    }

    public record PlannedEntry(PlannedKind kind, String fromValue, String toValue, String note) {
    }

    public record ContactName(String firstName, String lastName) {
    }

    public record TypeCount(String code, String label, int count) {
    }

    public record Representation(List<TypeCount> committedByType, int committedOrganizations,
            boolean jurisdictionPresent, boolean atLeastThreeOrganizations) {
    }

    private static final Set<String> CURRENT_CONTACT_STATUSES = Set.of(
            "PROSPECT", "INVITED", "APPLIED", "JOINED");

    private InitiativeRosterRules() {
    }

    /**
     * A changed status becomes a status-change entry, which can carry a note.
     * The same status with a note is a comment. Nothing is recorded when neither
     * the status nor the note changed.
     */
    public static PlannedEntry planStatusUpdate(String currentCode, String requestedCode, String note) {
        String next = trimToNull(requestedCode);
        if (next == null) {
            next = trimToNull(currentCode);
        }
        String cleanNote = trimToNull(note);
        boolean changed = !java.util.Objects.equals(trimToNull(currentCode), next);
        if (changed) {
            return new PlannedEntry(PlannedKind.STATUS_CHANGE, trimToNull(currentCode), next, cleanNote);
        }
        if (cleanNote != null) {
            return new PlannedEntry(PlannedKind.COMMENT, null, null, cleanNote);
        }
        return null;
    }

    /** Status-change entries stay as written. Authors may edit their own comments. */
    public static boolean canEditEntry(EsInitiativeTimelineEntry.EntryType entryType, Long authorUserId,
            Long editorUserId) {
        return entryType == EsInitiativeTimelineEntry.EntryType.COMMENT
                && authorUserId != null
                && authorUserId.equals(editorUserId);
    }

    /**
     * An organization needs a contact when nobody currently named there is a
     * prospect, invited, applied, or joined. Declined, not accepted, and left
     * contacts do not clear the flag.
     */
    public static boolean needsContact(Collection<String> memberStatusCodes) {
        if (memberStatusCodes == null || memberStatusCodes.isEmpty()) {
            return true;
        }
        for (String code : memberStatusCodes) {
            if (code != null && CURRENT_CONTACT_STATUSES.contains(code)) {
                return false;
            }
        }
        return true;
    }

    public static Representation summarize(Collection<String> committedTypeCodes) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (committedTypeCodes != null) {
            for (String code : committedTypeCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                counts.merge(code, 1, Integer::sum);
            }
        }
        List<TypeCount> byType = new java.util.ArrayList<>();
        for (String code : OrganizationDirectory.sortTypeCodes(counts.keySet())) {
            byType.add(new TypeCount(code, OrganizationDirectory.label(code), counts.get(code)));
        }
        int total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        return new Representation(byType, total, counts.getOrDefault("JURISDICTION", 0) > 0, total >= 3);
    }

    public static boolean memberMatches(String memberStatus, Long initiativeOrganizationId, String organizationType,
            String statusFilter, Long organizationFilter, String typeFilter) {
        if (trimToNull(statusFilter) != null && !statusFilter.equals(memberStatus)) {
            return false;
        }
        if (organizationFilter != null && !organizationFilter.equals(initiativeOrganizationId)) {
            return false;
        }
        if (trimToNull(typeFilter) != null && !typeFilter.equals(organizationType)) {
            return false;
        }
        return true;
    }

    public static ContactName splitContactName(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return new ContactName(null, null);
        }
        String collapsed = trimmed.replaceAll("\\s+", " ");
        int space = collapsed.lastIndexOf(' ');
        if (space < 0) {
            return new ContactName(limit(collapsed, 100), null);
        }
        return new ContactName(limit(collapsed.substring(0, space), 100), limit(collapsed.substring(space + 1), 100));
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
