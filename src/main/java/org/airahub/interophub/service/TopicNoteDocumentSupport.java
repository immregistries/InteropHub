package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class TopicNoteDocumentSupport {

    static final int MAX_DOCUMENT_NODES = 2000;
    static final int MAX_DOCUMENT_DEPTH = 12;
    static final int MAX_TEXT_LENGTH = 20000;

    public boolean isEmptyDocument(String documentJson) {
        return normalizeDocument(documentJson).isMeaningfulTextEmpty();
    }

    public String extractPlainText(String documentJson) {
        return normalizeDocument(documentJson).plainText();
    }

    public String buildEmptyBulletDocument() {
        JSONObject document = new JSONObject();
        document.put("type", "doc");
        JSONArray content = new JSONArray();
        content.put(buildBulletListWithEmptyItem());
        document.put("content", content);
        return document.toString();
    }

    public String buildInitialDocument(String title) {
        String meaningfulTitle = title == null ? null : title.trim();
        if (meaningfulTitle == null || meaningfulTitle.isEmpty()) {
            return buildEmptyBulletDocument();
        }

        JSONObject document = new JSONObject();
        document.put("type", "doc");
        JSONArray rootContent = new JSONArray();
        JSONObject bulletList = new JSONObject();
        bulletList.put("type", "bulletList");
        JSONArray listContent = new JSONArray();
        JSONObject listItem = new JSONObject();
        listItem.put("type", "listItem");
        listItem.put("attrs", new JSONObject().put("nodeId", UUID.randomUUID().toString()));
        JSONArray itemContent = new JSONArray();
        JSONObject paragraph = new JSONObject();
        paragraph.put("type", "paragraph");
        paragraph.put("content",
                new JSONArray().put(new JSONObject().put("type", "text").put("text", meaningfulTitle)));
        itemContent.put(paragraph);
        listItem.put("content", itemContent);
        listContent.put(listItem);
        bulletList.put("content", listContent);
        rootContent.put(bulletList);
        document.put("content", rootContent);
        return document.toString();
    }

    public NormalizedTopicNoteDocument normalizeDocument(String documentJson) {
        JSONObject parsed = parseDocument(documentJson);
        ValidationState state = new ValidationState();
        JSONObject normalized = normalizeNode(parsed, state, 0, false);
        if (state.nodeCount > MAX_DOCUMENT_NODES) {
            throw new IllegalArgumentException("Document is too large.");
        }
        if (state.textLength > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Document text is too large.");
        }
        List<String> plainTextLines = new ArrayList<>();
        collectPlainTextLines(normalized, plainTextLines, 0);
        String plainText = String.join("\n", plainTextLines).trim();
        return new NormalizedTopicNoteDocument(normalized.toString(), plainText,
                state.textLength == 0 || plainText.isBlank(), state.nodeIds);
    }

    public List<ListItemAnchor> listListItemAnchors(String documentJson) {
        NormalizedTopicNoteDocument normalized = normalizeDocument(documentJson);
        JSONObject root = new JSONObject(normalized.json());
        List<ListItemAnchor> anchors = new ArrayList<>();
        collectListItemAnchors(root, anchors);
        anchors.sort(Comparator.comparingInt(ListItemAnchor::displayOrder));
        return anchors;
    }

    public ListItemAnchor findListItemAnchor(String documentJson, String sourceNodeId) {
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            return null;
        }
        String expected = sourceNodeId.trim();
        for (ListItemAnchor anchor : listListItemAnchors(documentJson)) {
            if (expected.equals(anchor.sourceNodeId())) {
                return anchor;
            }
        }
        return null;
    }

    private JSONObject parseDocument(String documentJson) {
        if (documentJson == null || documentJson.isBlank()) {
            return new JSONObject(buildEmptyBulletDocument());
        }
        Object parsed = new JSONTokener(documentJson).nextValue();
        if (!(parsed instanceof JSONObject jsonObject)) {
            throw new IllegalArgumentException("Document root must be an object.");
        }
        return jsonObject;
    }

    private JSONObject normalizeNode(JSONObject source, ValidationState state, int depth, boolean insideListItem) {
        if (depth > MAX_DOCUMENT_DEPTH) {
            throw new IllegalArgumentException("Document nesting is too deep.");
        }
        state.nodeCount++;

        String type = source.optString("type", "").trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Document nodes must include a type.");
        }
        ensureAllowedMarks(source.optJSONArray("marks"));

        return switch (type) {
            case "doc" -> normalizeDoc(source, state, depth);
            case "bulletList" -> normalizeBulletList(source, state, depth);
            case "listItem" -> normalizeListItem(source, state, depth);
            case "paragraph" -> normalizeParagraph(source, state, depth, insideListItem);
            case "text" -> normalizeText(source, state);
            default -> throw new IllegalArgumentException("Unsupported node type: " + type);
        };
    }

    private JSONObject normalizeDoc(JSONObject source, ValidationState state, int depth) {
        JSONObject normalized = new JSONObject();
        normalized.put("type", "doc");
        JSONArray content = normalizeContentArray(source.optJSONArray("content"), state, depth + 1, false);
        for (int index = 0; index < content.length(); index++) {
            String childType = content.getJSONObject(index).optString("type");
            if (!"bulletList".equals(childType)) {
                throw new IllegalArgumentException("Document root may only contain bullet lists.");
            }
        }
        normalized.put("content", content);
        return normalized;
    }

    private JSONObject normalizeBulletList(JSONObject source, ValidationState state, int depth) {
        JSONObject normalized = new JSONObject();
        normalized.put("type", "bulletList");
        JSONArray content = normalizeContentArray(source.optJSONArray("content"), state, depth + 1, false);
        for (int index = 0; index < content.length(); index++) {
            String childType = content.getJSONObject(index).optString("type");
            if (!"listItem".equals(childType)) {
                throw new IllegalArgumentException("Bullet lists may only contain list items.");
            }
        }
        normalized.put("content", content);
        return normalized;
    }

    private JSONObject normalizeListItem(JSONObject source, ValidationState state, int depth) {
        JSONObject normalized = new JSONObject();
        normalized.put("type", "listItem");

        JSONObject attrs = source.optJSONObject("attrs");
        String nodeId = attrs == null ? null : attrs.optString("nodeId", null);
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Every list item must include a nodeId.");
        }
        try {
            UUID.fromString(nodeId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("List item nodeId must be a UUID.");
        }
        if (!state.nodeIds.add(nodeId)) {
            throw new IllegalArgumentException("Document contains duplicate list item nodeIds.");
        }
        normalized.put("attrs", new JSONObject().put("nodeId", nodeId));

        JSONArray content = normalizeContentArray(source.optJSONArray("content"), state, depth + 1, true);
        for (int index = 0; index < content.length(); index++) {
            String childType = content.getJSONObject(index).optString("type");
            if (!"paragraph".equals(childType) && !"bulletList".equals(childType)) {
                throw new IllegalArgumentException("List items may only contain paragraphs and nested bullet lists.");
            }
        }
        normalized.put("content", content);
        return normalized;
    }

    private JSONObject normalizeParagraph(JSONObject source, ValidationState state, int depth, boolean insideListItem) {
        if (!insideListItem) {
            throw new IllegalArgumentException("Paragraphs are only allowed inside list items.");
        }
        JSONObject normalized = new JSONObject();
        normalized.put("type", "paragraph");
        JSONArray content = normalizeContentArray(source.optJSONArray("content"), state, depth + 1, false);
        for (int index = 0; index < content.length(); index++) {
            String childType = content.getJSONObject(index).optString("type");
            if (!"text".equals(childType)) {
                throw new IllegalArgumentException("Paragraphs may only contain text nodes.");
            }
        }
        if (content.length() > 0) {
            normalized.put("content", content);
        }
        return normalized;
    }

    private JSONObject normalizeText(JSONObject source, ValidationState state) {
        String text = source.optString("text", null);
        if (text == null) {
            throw new IllegalArgumentException("Text nodes must include text.");
        }
        state.textLength += text.trim().length();
        return new JSONObject().put("type", "text").put("text", text);
    }

    private JSONArray normalizeContentArray(JSONArray content, ValidationState state, int depth,
            boolean insideListItem) {
        JSONArray normalized = new JSONArray();
        if (content == null) {
            return normalized;
        }
        for (int index = 0; index < content.length(); index++) {
            Object child = content.opt(index);
            if (!(child instanceof JSONObject childObject)) {
                throw new IllegalArgumentException("Document content must contain objects.");
            }
            normalized.put(normalizeNode(childObject, state, depth, insideListItem));
        }
        return normalized;
    }

    private void ensureAllowedMarks(JSONArray marks) {
        if (marks == null || marks.isEmpty()) {
            return;
        }
        throw new IllegalArgumentException("Text formatting is not supported in topic notes.");
    }

    private void collectPlainTextLines(JSONObject node, List<String> lines, int indentLevel) {
        String type = node.optString("type", "");
        switch (type) {
            case "doc":
            case "bulletList":
                JSONArray blockChildren = node.optJSONArray("content");
                if (blockChildren == null) {
                    return;
                }
                for (int index = 0; index < blockChildren.length(); index++) {
                    collectPlainTextLines(blockChildren.getJSONObject(index), lines, indentLevel);
                }
                return;
            case "listItem":
                collectListItemText(node, lines, indentLevel);
                return;
            default:
                return;
        }
    }

    private void collectListItemText(JSONObject node, List<String> lines, int indentLevel) {
        JSONArray content = node.optJSONArray("content");
        if (content == null) {
            return;
        }

        StringBuilder currentLine = new StringBuilder();
        for (int index = 0; index < content.length(); index++) {
            JSONObject child = content.getJSONObject(index);
            String childType = child.optString("type", "");
            if ("paragraph".equals(childType)) {
                appendParagraphText(child, currentLine);
            } else if ("bulletList".equals(childType)) {
                if (currentLine.length() > 0 || !lines.isEmpty()) {
                    lines.add(indent(indentLevel) + "- " + currentLine.toString().trim());
                    currentLine.setLength(0);
                }
                collectPlainTextLines(child, lines, indentLevel + 1);
            }
        }

        String text = currentLine.toString().trim();
        if (!text.isEmpty()) {
            lines.add(indent(indentLevel) + "- " + text);
        }
    }

    private void appendParagraphText(JSONObject paragraph, StringBuilder target) {
        JSONArray content = paragraph.optJSONArray("content");
        if (content == null) {
            return;
        }
        for (int index = 0; index < content.length(); index++) {
            JSONObject child = content.getJSONObject(index);
            if (!"text".equals(child.optString("type", ""))) {
                continue;
            }
            String text = child.optString("text", "");
            if (target.length() > 0) {
                target.append(' ');
            }
            target.append(text.trim());
        }
    }

    private String indent(int indentLevel) {
        return "  ".repeat(Math.max(0, indentLevel));
    }

    private JSONObject buildBulletListWithEmptyItem() {
        return new JSONObject()
                .put("type", "bulletList")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "listItem")
                        .put("attrs", new JSONObject().put("nodeId", UUID.randomUUID().toString()))
                        .put("content", new JSONArray().put(new JSONObject().put("type", "paragraph")))));
    }

    private void collectListItemAnchors(JSONObject node, List<ListItemAnchor> anchors) {
        if (node == null) {
            return;
        }
        String type = node.optString("type", "");
        if ("listItem".equals(type)) {
            JSONObject attrs = node.optJSONObject("attrs");
            String nodeId = attrs == null ? null : attrs.optString("nodeId", null);
            if (nodeId != null && !nodeId.isBlank()) {
                anchors.add(new ListItemAnchor(nodeId, extractListItemText(node), anchors.size() + 1));
            }
        }

        JSONArray content = node.optJSONArray("content");
        if (content == null) {
            return;
        }
        for (int index = 0; index < content.length(); index++) {
            Object child = content.opt(index);
            if (child instanceof JSONObject childObject) {
                collectListItemAnchors(childObject, anchors);
            }
        }
    }

    private String extractListItemText(JSONObject listItem) {
        if (listItem == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        JSONArray content = listItem.optJSONArray("content");
        if (content == null) {
            return "";
        }
        for (int index = 0; index < content.length(); index++) {
            Object child = content.opt(index);
            if (!(child instanceof JSONObject childObject)) {
                continue;
            }
            if (!"paragraph".equals(childObject.optString("type", ""))) {
                continue;
            }
            appendParagraphText(childObject, text);
        }
        return text.toString().trim();
    }

    public record NormalizedTopicNoteDocument(String json, String plainText, boolean isMeaningfulTextEmpty,
            Set<String> nodeIds) {
    }

    public record ListItemAnchor(String sourceNodeId, String sourceText, int displayOrder) {
    }

    private static final class ValidationState {
        private final Set<String> nodeIds = new HashSet<>();
        private int nodeCount;
        private int textLength;
    }
}