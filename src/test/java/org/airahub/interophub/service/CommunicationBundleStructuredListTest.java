package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunicationBundleStructuredListTest {
    @Test
    void lineItemsRoundTripWithQuotesUnicodeAndBlankLines() {
        String json = CommunicationBundleStructuredList.fromLines("  One \"quoted\" item \r\n\r\n Two \n");
        assertEquals(List.of("One \"quoted\" item", "Two"), CommunicationBundleStructuredList.items(json));
        assertNull(CommunicationBundleStructuredList.fromLines(" \n "));
        assertEquals(List.of(), CommunicationBundleStructuredList.items(null));
        assertThrows(IllegalArgumentException.class, () -> CommunicationBundleStructuredList.fromLines("x".repeat(50001)));
    }

    @Test
    void invalidStoredShapesFailExplicitly() {
        assertThrows(IllegalStateException.class, () -> CommunicationBundleStructuredList.items("{}"));
        assertThrows(IllegalStateException.class, () -> CommunicationBundleStructuredList.items("[1]"));
        assertThrows(IllegalStateException.class, () -> CommunicationBundleStructuredList.items("[null]"));
        assertThrows(IllegalStateException.class, () -> CommunicationBundleStructuredList.items("[\" \"]"));
    }
}
