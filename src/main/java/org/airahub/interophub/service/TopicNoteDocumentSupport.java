package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class TopicNoteDocumentSupport {

    public boolean isEmptyDocument(String documentJson) {
        return extractPlainText(documentJson).isBlank();
    }

    public String extractPlainText(String documentJson) {
        if (documentJson == null || documentJson.isBlank()) {
            return "";
        }
        try {
            Object parsed = new org.json.JSONTokener(documentJson).nextValue();
            List<String> tokens = new ArrayList<>();
            collectText(parsed, tokens);
            return String.join(" ", tokens).replaceAll("\\s+", " ").trim();
        } catch (Exception ex) {
            return documentJson.trim();
        }
    }

    public String buildInitialDocument(String title) {
        String heading = title == null || title.isBlank() ? "Notes" : title.trim();
        JSONObject document = new JSONObject();
        document.put("type", "doc");
        JSONArray content = new JSONArray();
        JSONObject paragraph = new JSONObject();
        paragraph.put("type", "paragraph");
        JSONArray inline = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("type", "text");
        text.put("text", heading);
        inline.put(text);
        paragraph.put("content", inline);
        content.put(paragraph);
        document.put("content", content);
        return document.toString();
    }

    private void collectText(Object node, List<String> tokens) {
        if (node == null) {
            return;
        }
        if (node instanceof JSONObject jsonObject) {
            if (jsonObject.has("text")) {
                String text = jsonObject.optString("text", "").trim();
                if (!text.isEmpty()) {
                    tokens.add(text);
                }
            }
            Object content = jsonObject.opt("content");
            if (content != null) {
                collectText(content, tokens);
            }
            return;
        }
        if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                collectText(array.opt(i), tokens);
            }
        }
    }
}