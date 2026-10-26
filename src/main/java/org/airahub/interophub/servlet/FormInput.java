package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Values from a failed post, so the same form can be shown again with the
 * error and the submitted fields still filled in.
 */
final class FormInput {

    static final FormInput NONE = new FormInput("", null, null, Map.of());

    private final String action;
    private final String error;
    private final String openDialog;
    private final Map<String, String> values;

    private FormInput(String action, String error, String openDialog, Map<String, String> values) {
        this.action = action;
        this.error = error;
        this.openDialog = openDialog;
        this.values = values;
    }

    static FormInput none() {
        return NONE;
    }

    static FormInput replay(HttpServletRequest request, String action, String error, String openDialog) {
        Map<String, String> values = new HashMap<>();
        request.getParameterMap().forEach((key, list) -> {
            if (list != null && list.length > 0 && list[0] != null) {
                values.put(key, String.join(",", list));
            }
        });
        return new FormInput(action == null ? "" : action, error, openDialog, values);
    }

    boolean failed(String action) {
        return error != null && this.action.equals(action);
    }

    String error() {
        return error;
    }

    String openDialog() {
        return openDialog;
    }

    String posted(String name) {
        String value = values.get(name);
        return value == null ? "" : value;
    }

    /** Posted value when this action failed; otherwise the value already stored. */
    String value(String action, String name, String current) {
        if (!failed(action)) {
            return current == null ? "" : current;
        }
        return posted(name);
    }
}
