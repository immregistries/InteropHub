package org.airahub.interophub.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.airahub.interophub.service.GuideRows.LabelRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.json.JSONObject;

/**
 * Display states, comment modes, label resolution, and comment scope for a
 * guide's labels. Plan: docs/tasks/topic-reference-version-display-and-comment-modes.md
 * (Sections 3.1 to 3.3). Pure rules; the stores load the facts.
 */
public final class GuideLabels {
    public static final String CURRENT = "CURRENT";
    public static final String LISTED = "LISTED";
    public static final String UNLISTED = "UNLISTED";
    public static final String AUTHORS_ONLY = "AUTHORS_ONLY";
    public static final String RETIRED = "RETIRED";
    public static final List<String> DISPLAY_STATES = List.of(CURRENT, LISTED, UNLISTED, AUTHORS_ONLY, RETIRED);

    public static final String OPEN = "OPEN";
    public static final String RELEASE = "RELEASE";
    public static final String CLOSED = "CLOSED";
    public static final List<String> COMMENT_MODES = List.of(OPEN, RELEASE, CLOSED);

    public static final int MAX_LABEL = 64;
    public static final int MAX_PAGE_URL = 2000;

    private static final Pattern HOSTED_LABEL = Pattern.compile(
            "^(?:latest|main|v[0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9][A-Za-z0-9.]*)?)$");

    private GuideLabels() {
    }

    /** The API spelling of a display state or comment mode: lower case. */
    public static String api(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** {@code current}, {@code listed}, ... into the stored value. Case is ignored. */
    public static String display(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!DISPLAY_STATES.contains(value)) {
            throw GuideApiException.invalidField("display",
                    "Display must be current, listed, unlisted, authors_only, or retired.");
        }
        return value;
    }

    /** {@code open}, {@code release}, or {@code closed} into the stored value. Case is ignored. */
    public static String commentMode(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!COMMENT_MODES.contains(value)) {
            throw GuideApiException.invalidField("commentMode", "Comment mode must be open, release, or closed.");
        }
        return value;
    }

    public static String displayName(String display) {
        if (display == null) {
            return "Unlisted";
        }
        return switch (display) {
            case CURRENT -> "Current";
            case LISTED -> "Listed";
            case AUTHORS_ONLY -> "Authors only";
            case RETIRED -> "Retired";
            default -> "Unlisted";
        };
    }

    public static String modeName(String mode) {
        if (mode == null) {
            return "Open";
        }
        return switch (mode) {
            case RELEASE -> "Release";
            case CLOSED -> "Closed";
            default -> "Open";
        };
    }

    /** Readers see the label on the topic page and through GET /labels. */
    public static boolean readerListed(String display) {
        return CURRENT.equals(display) || LISTED.equals(display);
    }

    /** Readers may learn that the label exists (GET /labels). Authors only and Retired are left out. */
    public static boolean readerKnown(String display) {
        return !AUTHORS_ONLY.equals(display) && !RETIRED.equals(display);
    }

    /**
     * Plan 3.3. The label a page address belongs to, or null.
     * <ul>
     * <li>Hosted: {@code {hub}/guide/view/{documentKey}/{label}/...} gives {@code label}.</li>
     * <li>External: the longest registered URL of the document that the address starts with gives that binding's label.</li>
     * </ul>
     * The query and fragment are ignored.
     */
    public static String resolve(String pageUrl, String documentKey, String hubBase, List<UrlBinding> urls) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return null;
        }
        String address = strip(pageUrl.trim());
        if (hubBase != null && !hubBase.isBlank() && documentKey != null) {
            String base = hubBase.endsWith("/") ? hubBase.substring(0, hubBase.length() - 1) : hubBase;
            String prefix = base + "/guide/view/" + documentKey + "/";
            if (startsWithIgnoringOriginCase(address, prefix)) {
                String rest = address.substring(prefix.length());
                int slash = rest.indexOf('/');
                String segment = slash < 0 ? rest : rest.substring(0, slash);
                String label;
                try {
                    label = URLDecoder.decode(segment, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
                return label.length() <= MAX_LABEL && HOSTED_LABEL.matcher(label).matches() ? label : null;
            }
        }
        UrlBinding best = null;
        if (urls != null) {
            for (UrlBinding binding : urls) {
                if (binding == null || binding.url == null || binding.url.isBlank() || binding.label == null) {
                    continue;
                }
                String registered = binding.url;
                boolean matches = startsWithIgnoringOriginCase(address, registered)
                        || (registered.endsWith("/")
                                && equalsIgnoringOriginCase(address, registered.substring(0, registered.length() - 1)));
                if (matches && (best == null || registered.length() > best.url.length())) {
                    best = binding;
                }
            }
        }
        return best == null ? null : best.label;
    }

    private static String strip(String url) {
        int cut = url.length();
        int hash = url.indexOf('#');
        if (hash >= 0) {
            cut = Math.min(cut, hash);
        }
        int query = url.indexOf('?');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        return url.substring(0, cut);
    }

    /** Scheme and host compare without case; the path compares exactly. */
    private static boolean startsWithIgnoringOriginCase(String address, String prefix) {
        int origin = originEnd(prefix);
        if (address.length() < prefix.length()) {
            return false;
        }
        return address.regionMatches(true, 0, prefix, 0, origin)
                && address.regionMatches(false, origin, prefix, origin, prefix.length() - origin);
    }

    private static boolean equalsIgnoringOriginCase(String address, String other) {
        return address.length() == other.length() && startsWithIgnoringOriginCase(address, other);
    }

    private static int originEnd(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return 0;
        }
        int slash = url.indexOf('/', scheme + 3);
        return slash < 0 ? url.length() : slash;
    }

    /** What InteropHub knows about one guide's labels. */
    public static final class Facts {
        /** Author settings, by label. */
        public final Map<String, LabelRow> settings = new LinkedHashMap<>();
        /** Newest hosted upload, by label. */
        public final Map<String, LocalDateTime> uploads = new LinkedHashMap<>();
        /** Newest es_guide_build received_at, by label. */
        public final Map<String, LocalDateTime> builds = new LinkedHashMap<>();
        /** Registered guide URL labels, in registration order. */
        public final List<String> urlLabels = new ArrayList<>();

        public static Facts empty() {
            return new Facts();
        }

        /**
         * Known labels (plan 5.2): uploaded, registered, named in a build, or
         * already configured. Ordered latest, main, then newest publication first,
         * then by name.
         */
        public List<String> known() {
            Set<String> labels = new LinkedHashSet<>();
            labels.addAll(urlLabels);
            labels.addAll(uploads.keySet());
            labels.addAll(builds.keySet());
            labels.addAll(settings.keySet());
            labels.removeIf(label -> label == null || label.isBlank());
            List<String> ordered = new ArrayList<>(labels);
            ordered.sort(Comparator.comparingInt(GuideLabels::rank)
                    .thenComparing((String label) -> publishedAt(label),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Comparator.naturalOrder()));
            return ordered;
        }

        public boolean isKnown(String label) {
            return label != null && (urlLabels.contains(label) || uploads.containsKey(label)
                    || builds.containsKey(label) || settings.containsKey(label));
        }

        /** The setting for a label, or the defaults (UNLISTED / OPEN) when it has none. */
        public LabelRow setting(String label) {
            LabelRow row = settings.get(label);
            if (row != null) {
                return row;
            }
            LabelRow defaults = new LabelRow();
            defaults.label = label;
            return defaults;
        }

        public String display(String label) {
            return setting(label).display;
        }

        /** The CURRENT label, or null. */
        public String current() {
            for (LabelRow row : settings.values()) {
                if (CURRENT.equals(row.display)) {
                    return row.label;
                }
            }
            return null;
        }

        /**
         * Plan 3.2: the newest hosted upload; else the newest build's received_at;
         * else, while the mode is Release, when it was set to Release.
         */
        public LocalDateTime publishedAt(String label) {
            if (uploads.containsKey(label)) {
                return uploads.get(label);
            }
            if (builds.containsKey(label)) {
                return builds.get(label);
            }
            LabelRow row = settings.get(label);
            return row != null && RELEASE.equals(row.commentMode) ? row.commentModeSetAt : null;
        }

        /** The policy for a known label, or null for an unknown one (treated as Open, unfiltered). */
        public Policy policy(String label) {
            if (!isKnown(label)) {
                return null;
            }
            LabelRow row = setting(label);
            return new Policy(label, row.display, row.commentMode, publishedAt(label));
        }
    }

    /** How one label shows comments. */
    public record Policy(String label, String display, String commentMode, LocalDateTime publishedAt) {
        public boolean closed() {
            return CLOSED.equals(commentMode);
        }

        public boolean release() {
            return RELEASE.equals(commentMode);
        }

        /**
         * Plan 3.2 scope: written on this label, or created before its publication
         * time. Untagged items count by creation time. Judged on the top-level item.
         */
        public boolean inScope(String madeOnLabel, LocalDateTime createdAt) {
            if (label.equals(madeOnLabel)) {
                return true;
            }
            return publishedAt != null && createdAt != null && createdAt.isBefore(publishedAt);
        }

        /** The value reported as {@code inScope}: everything is in scope on an Open label. */
        public boolean reportedInScope(String madeOnLabel, LocalDateTime createdAt) {
            return !release() && !closed() || inScope(madeOnLabel, createdAt);
        }

        /** Whether the item is returned. Closed shows nothing; authors see everything else. */
        public boolean shows(boolean author, String madeOnLabel, LocalDateTime createdAt) {
            if (closed()) {
                return false;
            }
            if (author || !release()) {
                return true;
            }
            return inScope(madeOnLabel, createdAt);
        }

        public JSONObject json() {
            JSONObject json = new JSONObject();
            json.put("label", label);
            json.put("display", api(display));
            json.put("commentMode", api(commentMode));
            String published = ApiTime.format(publishedAt);
            json.put("publishedAt", published == null ? JSONObject.NULL : published);
            return json;
        }
    }

    static int rank(String label) {
        if ("latest".equals(label)) {
            return 0;
        }
        if ("main".equals(label)) {
            return 1;
        }
        return 2;
    }
}
