package org.airahub.interophub.model;

import java.util.function.Function;

/** Shared label lookup for varchar status codes stored on initiative rows. */
final class CodedLabel {

    private CodedLabel() {
    }

    static <E extends Enum<E>> String labelOf(Class<E> type, String code, Function<E, String> label) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return label.apply(Enum.valueOf(type, code.trim()));
        } catch (IllegalArgumentException ex) {
            return code.trim();
        }
    }
}
