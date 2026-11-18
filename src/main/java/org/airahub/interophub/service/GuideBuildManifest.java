package org.airahub.interophub.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads a guide's build manifest (schema 1), either from the
 * {@code <script type="application/json" id="guide-build-manifest">} element of
 * an uploaded guide or from the body of a manifest-only report. The manifest is
 * written by content-publishing-templates; see
 * docs/tasks/topic-reference-summary-annotations-and-guide-status.md, A2.
 */
public final class GuideBuildManifest {
    public static final int MAX_BYTES = 64 * 1024;
    public static final int SCHEMA = 1;
    static final int MAX_ERROR = 300;

    private static final Pattern SCRIPT = Pattern.compile("<script\\b([^>]*)>(.*?)</script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile(
            "\\bid\\s*=\\s*(?:\"guide-build-manifest\"|'guide-build-manifest'|guide-build-manifest(?=[\\s/>]|$))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE = Pattern.compile(
            "\\btype\\s*=\\s*(?:\"\\s*application/json\\s*\"|'\\s*application/json\\s*'|application/json(?=[\\s/>]|$))",
            Pattern.CASE_INSENSITIVE);

    private GuideBuildManifest() {
    }

    /** Fields copied out of a schema-1 manifest, plus the manifest itself. */
    public static final class Parsed {
        public Integer schema;
        public String processVersion;
        public String documentVersion;
        public String documentStatus;
        public LocalDateTime builtAt;
        public String sourceCommit;
        public Integer warningCount;
        public JSONObject manifest;
    }

    /** A manifest that cannot be read. The message is stored as {@code manifest_error}. */
    public static final class Invalid extends Exception {
        private final Integer schema;
        private final JSONObject manifest;

        Invalid(String message) {
            this(message, null, null);
        }

        Invalid(String message, Integer schema, JSONObject manifest) {
            super(message);
            this.schema = schema;
            this.manifest = manifest;
        }

        /** The schema number when the manifest parsed but its schema is not known. */
        public Integer schema() {
            return schema;
        }

        /** The parsed object when it is JSON but not a usable schema-1 manifest. */
        public JSONObject manifest() {
            return manifest;
        }
    }

    /**
     * The text of the manifest element, or null when the guide has none. Throws
     * when the element is present but cannot be used (wrong type, over the cap).
     */
    public static String extract(byte[] html) throws Invalid {
        if (html == null || html.length == 0) {
            return null;
        }
        String text = new String(html, StandardCharsets.UTF_8);
        Matcher matcher = SCRIPT.matcher(text);
        while (matcher.find()) {
            String attributes = matcher.group(1);
            if (!ID.matcher(attributes).find()) {
                continue;
            }
            if (!TYPE.matcher(attributes).find()) {
                throw new Invalid("The build manifest element is not type application/json.");
            }
            String body = matcher.group(2);
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                throw new Invalid("The build manifest is larger than 64 KB.");
            }
            return body;
        }
        return null;
    }

    public static Parsed parse(String json) throws Invalid {
        if (json == null || json.isBlank()) {
            throw new Invalid("The build manifest is empty.");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new Invalid("The build manifest is larger than 64 KB.");
        }
        JSONObject manifest;
        try {
            manifest = new JSONObject(json.trim());
        } catch (JSONException ex) {
            throw new Invalid("The build manifest is not valid JSON.");
        }
        return parse(manifest);
    }

    public static Parsed parse(JSONObject manifest) throws Invalid {
        if (manifest == null) {
            throw new Invalid("The build manifest is missing.");
        }
        if (manifest.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new Invalid("The build manifest is larger than 64 KB.");
        }
        Object rawSchema = manifest.opt("schema");
        if (rawSchema == null || rawSchema == JSONObject.NULL) {
            throw new Invalid("The build manifest has no schema number.", null, manifest);
        }
        if (!(rawSchema instanceof Integer) && !(rawSchema instanceof Long)) {
            throw new Invalid("The build manifest schema is not a whole number.", null, manifest);
        }
        long schemaValue = ((Number) rawSchema).longValue();
        Integer schema = schemaValue >= Integer.MIN_VALUE && schemaValue <= Integer.MAX_VALUE
                ? (int) schemaValue : null;
        if (schema == null || schema != SCHEMA) {
            throw new Invalid("Unknown build manifest schema: " + schemaValue + ".", schema, manifest);
        }
        Parsed parsed = new Parsed();
        parsed.schema = schema;
        parsed.manifest = manifest;
        try {
            parsed.processVersion = text(manifest, "processVersion", 32, true);
            Object releases = manifest.opt("processReleases");
            if (releases != null && releases != JSONObject.NULL) {
                if (!(releases instanceof JSONArray array)) {
                    throw new Invalid("processReleases must be a list.");
                }
                for (int i = 0; i < array.length(); i++) {
                    if (!(array.opt(i) instanceof JSONObject release)) {
                        throw new Invalid("Each processReleases entry must be an object.");
                    }
                    text(release, "version", 32, true);
                }
            }
            JSONObject document = object(manifest, "document");
            if (document != null) {
                parsed.documentVersion = text(document, "version", 64, false);
                parsed.documentStatus = text(document, "status", 40, false);
            }
            JSONObject build = object(manifest, "build");
            if (build != null) {
                String builtAt = text(build, "builtAt", 64, false);
                if (builtAt != null) {
                    try {
                        parsed.builtAt = ApiTime.parse(builtAt);
                    } catch (DateTimeParseException ex) {
                        throw new Invalid("build.builtAt is not an ISO-8601 UTC time.");
                    }
                }
                parsed.sourceCommit = text(build, "sourceCommit", 64, false);
            }
            JSONObject checks = object(manifest, "checks");
            if (checks != null) {
                Object warnings = checks.opt("warnings");
                if (warnings != null && warnings != JSONObject.NULL) {
                    if (!(warnings instanceof JSONArray array)) {
                        throw new Invalid("checks.warnings must be a list.");
                    }
                    parsed.warningCount = array.length();
                }
            }
        } catch (Invalid ex) {
            throw new Invalid(ex.getMessage(), schema, manifest);
        }
        return parsed;
    }

    /**
     * The build row for an uploaded guide. Never throws: no manifest leaves the
     * manifest fields null, and an unusable one sets {@code manifestError}.
     */
    public static GuideRows.BuildRow uploadRow(byte[] html) {
        GuideRows.BuildRow row = new GuideRows.BuildRow();
        row.source = "UPLOAD";
        try {
            String text = extract(html);
            if (text != null) {
                fill(row, parse(text));
            }
        } catch (Invalid ex) {
            fillError(row, ex);
        } catch (RuntimeException ex) {
            fillError(row, new Invalid("The build manifest could not be read."));
        }
        return row;
    }

    static void fill(GuideRows.BuildRow row, Parsed parsed) {
        row.manifestSchema = parsed.schema;
        row.processVersion = parsed.processVersion;
        row.documentVersion = parsed.documentVersion;
        row.documentStatus = parsed.documentStatus;
        row.builtAt = parsed.builtAt;
        row.sourceCommit = parsed.sourceCommit;
        row.warningCount = parsed.warningCount;
        row.manifestJson = parsed.manifest.toString();
        row.manifestError = null;
    }

    static void fillError(GuideRows.BuildRow row, Invalid ex) {
        row.manifestSchema = ex.schema();
        row.manifestJson = ex.manifest() == null ? null : ex.manifest().toString();
        row.manifestError = errorText(ex.getMessage());
    }

    /** Fits a message into {@code manifest_error}. */
    public static String errorText(String message) {
        String text = message == null || message.isBlank() ? "The build manifest could not be read." : message;
        return text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR - 1) + "…";
    }

    private static JSONObject object(JSONObject parent, String field) throws Invalid {
        Object value = parent.opt(field);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (!(value instanceof JSONObject object)) {
            throw new Invalid(field + " must be an object.");
        }
        return object;
    }

    private static String text(JSONObject parent, String field, int max, boolean required) throws Invalid {
        Object value = parent.opt(field);
        if (value == null || value == JSONObject.NULL) {
            if (required) {
                throw new Invalid("The build manifest has no " + field + ".");
            }
            return null;
        }
        if (!(value instanceof String text)) {
            throw new Invalid(field + " must be text.");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw new Invalid("The build manifest has no " + field + ".");
            }
            return null;
        }
        if (trimmed.length() > max) {
            throw new Invalid(field + " is longer than " + max + " characters.");
        }
        return trimmed;
    }
}
