package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

public final class CommunicationBundleStructuredList {
    private CommunicationBundleStructuredList() { }

    public static String fromLines(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        if (content.length() > 50000) {
            throw new IllegalArgumentException("List content must be at most 50000 characters.");
        }
        var items = content.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        return items.isEmpty() ? null : new JSONArray(items).toString();
    }

    public static List<String> items(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var array = new JSONArray(json);
            var result = new ArrayList<String>();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (!(item instanceof String text) || text.isBlank()) {
                    throw new IllegalStateException("Structured list content must contain non-empty text items.");
                }
                result.add(text);
            }
            return List.copyOf(result);
        } catch (JSONException ex) {
            throw new IllegalStateException("The stored structured list is not a valid JSON text array.", ex);
        }
    }
}
