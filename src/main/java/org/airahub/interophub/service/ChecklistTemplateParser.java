package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a checklist template Markdown file. Every problem is reported together,
 * with the line it came from, so an upload can be corrected in one pass.
 */
public final class ChecklistTemplateParser {

    public static final Set<String> KINDS = Set.of("INITIATIVE", "SMALL_GROUP");
    public static final Set<String> ANCHORS = Set.of("start", "end", "cycle-start", "working-day", "closeout");

    public record Item(String key, String phase, String title, String guidance, String dueAnchor,
            Integer dueOffsetDays, String checkCode, int displayOrder, int lineNumber) {
    }

    public record ParseError(int lineNumber, String message) {
    }

    public record Parsed(String title, String kind, Integer version, List<Item> items, List<ParseError> errors) {

        public boolean ok() {
            return errors.isEmpty();
        }

        public String errorText() {
            StringBuilder text = new StringBuilder();
            for (ParseError error : errors) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append("Line ").append(error.lineNumber()).append(": ").append(error.message());
            }
            return text.toString();
        }
    }

    private record NumberedLine(int number, String text) {
    }

    private static final Pattern ITEM_LINE = Pattern.compile("^-\\s+(?:\\[([^\\]]*)\\]\\s*)?(.*)$");
    private static final Pattern MARKER = Pattern.compile("\\((due|check):\\s*([^)]*)\\)");
    private static final Pattern DUE = Pattern.compile("^(start|end|cycle-start|working-day|closeout)(?:\\s*([+-])\\s*(\\d+)d)?$");
    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private ChecklistTemplateParser() {
    }

    public static Parsed parse(String markdown) {
        List<ParseError> errors = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        Map<String, Integer> keys = new LinkedHashMap<>();
        String title = null;
        String kind = null;
        Integer version = null;
        int kindLine = 0;
        int versionLine = 0;
        String phase = null;
        ItemDraft draft = null;
        int order = 0;

        if (markdown == null || markdown.isBlank()) {
            errors.add(new ParseError(1, "The template is empty."));
            return new Parsed(null, null, null, List.of(), List.copyOf(errors));
        }

        for (NumberedLine line : visibleLines(markdown)) {
            String text = line.text();
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (draft != null && startsWithIndent(text) && !trimmed.startsWith("- ") && !trimmed.startsWith("#")) {
                draft.guidance.add(trimmed);
                continue;
            }
            if (draft != null) {
                order = finish(draft, items, keys, errors, order);
                draft = null;
            }
            if (trimmed.startsWith("## ")) {
                phase = trimToNull(trimmed.substring(3));
                if (phase == null) {
                    errors.add(new ParseError(line.number(), "A phase needs a name."));
                }
                continue;
            }
            if (trimmed.startsWith("# ")) {
                if (title == null) {
                    title = trimToNull(trimmed.substring(2));
                }
                continue;
            }
            if (trimmed.startsWith("kind:")) {
                if (kind != null) {
                    errors.add(new ParseError(line.number(), "kind is already set."));
                }
                kind = trimToNull(trimmed.substring("kind:".length()));
                kindLine = line.number();
                if (kind == null || !KINDS.contains(kind)) {
                    errors.add(new ParseError(line.number(),
                            "Unknown kind. Use INITIATIVE or SMALL_GROUP."));
                }
                continue;
            }
            if (trimmed.startsWith("version:")) {
                if (version != null || versionLine > 0) {
                    errors.add(new ParseError(line.number(), "version is already set."));
                }
                versionLine = line.number();
                String raw = trimToNull(trimmed.substring("version:".length()));
                if (raw == null || !raw.matches("[1-9]\\d*")) {
                    errors.add(new ParseError(line.number(), "version must be a whole number."));
                } else {
                    version = Integer.valueOf(raw);
                }
                continue;
            }
            Matcher item = ITEM_LINE.matcher(trimmed);
            if (item.matches()) {
                if (phase == null) {
                    errors.add(new ParseError(line.number(), "An item has to be under a phase heading."));
                }
                draft = new ItemDraft(line.number(), phase, item.group(1), item.group(2) == null ? "" : item.group(2));
                continue;
            }
            errors.add(new ParseError(line.number(), "This line is not a phase, an item, kind, or version."));
        }
        if (draft != null) {
            finish(draft, items, keys, errors, order);
        }
        if (kindLine == 0) {
            errors.add(new ParseError(1, "kind is missing."));
        }
        if (versionLine == 0) {
            errors.add(new ParseError(1, "version is missing."));
        }
        if (title == null) {
            title = kind == null ? "Checklist" : ("INITIATIVE".equals(kind) ? "Initiative Checklist" : "Small Group Checklist");
        }
        return new Parsed(title, kind != null && KINDS.contains(kind) ? kind : null, version, List.copyOf(items),
                List.copyOf(errors));
    }

    private static int finish(ItemDraft draft, List<Item> items, Map<String, Integer> keys, List<ParseError> errors,
            int order) {
        String remainder = draft.remainder;
        String dueRaw = null;
        String check = null;
        int dueCount = 0;
        int checkCount = 0;
        Matcher markers = MARKER.matcher(remainder);
        StringBuilder title = new StringBuilder();
        int cursor = 0;
        while (markers.find()) {
            title.append(remainder, cursor, markers.start());
            cursor = markers.end();
            String type = markers.group(1);
            String value = markers.group(2) == null ? "" : markers.group(2).trim();
            if ("due".equals(type)) {
                dueCount++;
                dueRaw = value;
            } else {
                checkCount++;
                check = value;
            }
        }
        title.append(remainder.substring(cursor));
        String itemTitle = title.toString().replaceAll("\\s+", " ").trim();
        if (dueCount > 1) {
            errors.add(new ParseError(draft.line, "An item can have only one due rule."));
        }
        if (checkCount > 1) {
            errors.add(new ParseError(draft.line, "An item can have only one check."));
        }
        String anchor = null;
        Integer offset = null;
        if (dueRaw != null) {
            Matcher due = DUE.matcher(dueRaw.replace(" ", ""));
            if (!due.matches() || !ANCHORS.contains(due.group(1))) {
                errors.add(new ParseError(draft.line, "Unknown due anchor \"" + dueRaw + "\"."));
            } else {
                anchor = due.group(1);
                if (due.group(2) == null) {
                    offset = 0;
                } else {
                    int days = Integer.parseInt(due.group(3));
                    offset = "-".equals(due.group(2)) ? -days : days;
                }
            }
        }
        if (check != null && check.isBlank()) {
            errors.add(new ParseError(draft.line, "A check needs a code."));
            check = null;
        }
        if (itemTitle.isEmpty()) {
            errors.add(new ParseError(draft.line, "An item needs a title."));
            itemTitle = "Untitled";
        }
        String key = draft.explicitKey == null ? null : draft.explicitKey.trim();
        boolean generated = false;
        if (key == null || key.isEmpty()) {
            key = slug(itemTitle);
            generated = true;
            if (key.isEmpty()) {
                errors.add(new ParseError(draft.line, "A key could not be made from the title."));
                key = "item";
            }
        } else if (!KEY.matcher(key).matches()) {
            errors.add(new ParseError(draft.line, "Item key \"" + key + "\" must use lowercase letters, numbers, and hyphens."));
        }
        Integer previous = keys.putIfAbsent(key, draft.line);
        if (previous != null) {
            errors.add(new ParseError(draft.line, (generated ? "Generated key \"" : "Duplicate key \"") + key
                    + "\" is already used on line " + previous + "."));
        }
        String guidance = draft.guidance.isEmpty() ? null : String.join("\n", draft.guidance);
        order++;
        items.add(new Item(key, draft.phase == null ? "" : draft.phase, itemTitle, guidance, anchor, offset,
                check == null || check.isBlank() ? null : check, order, draft.line));
        return order;
    }

    static String slug(String title) {
        String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-+$", "");
        }
        return slug;
    }

    private static List<NumberedLine> visibleLines(String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n", -1);
        List<NumberedLine> lines = new ArrayList<>();
        boolean inComment = false;
        for (int index = 0; index < raw.length; index++) {
            String line = raw[index];
            StringBuilder visible = new StringBuilder();
            int cursor = 0;
            while (cursor < line.length()) {
                if (inComment) {
                    int end = line.indexOf("-->", cursor);
                    if (end < 0) {
                        cursor = line.length();
                    } else {
                        inComment = false;
                        cursor = end + 3;
                    }
                } else {
                    int start = line.indexOf("<!--", cursor);
                    if (start < 0) {
                        visible.append(line.substring(cursor));
                        break;
                    }
                    visible.append(line, cursor, start);
                    inComment = true;
                    cursor = start + 4;
                }
            }
            String text = visible.toString();
            if (text.isBlank() && (inComment || !line.isBlank())) {
                continue;
            }
            lines.add(new NumberedLine(index + 1, text));
        }
        return lines;
    }

    private static boolean startsWithIndent(String text) {
        return !text.isEmpty() && (text.charAt(0) == ' ' || text.charAt(0) == '\t');
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class ItemDraft {
        private final int line;
        private final String phase;
        private final String explicitKey;
        private final String remainder;
        private final List<String> guidance = new ArrayList<>();

        private ItemDraft(int line, String phase, String explicitKey, String remainder) {
            this.line = line;
            this.phase = phase;
            this.explicitKey = explicitKey;
            this.remainder = remainder;
        }
    }
}
