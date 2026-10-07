package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.airahub.interophub.model.EsMeetingAgendaAttachment;
import org.airahub.interophub.model.StoredFile;
import org.junit.jupiter.api.Test;

class MeetingAttachmentRendererTest {
    @Test
    void imagesPrecedeDocumentsWithoutChangingOrderWithinEachGroup() {
        List<EsMeetingAgendaAttachment> attachments = List.of(
                attachment(1L, "first.pdf", "application/pdf"),
                attachment(2L, "second.png", "image/png"),
                attachment(3L, "third.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                attachment(4L, "fourth.jpeg", "image/jpeg"));
        String html = MeetingAttachmentRenderer.agenda("/hub", attachments);
        assertTrue(html.indexOf("second.png") < html.indexOf("fourth.jpeg"));
        assertTrue(html.indexOf("fourth.jpeg") < html.indexOf("first.pdf"));
        assertTrue(html.indexOf("first.pdf") < html.indexOf("third.pptx"));
        assertTrue(html.contains("max-width:100%;height:auto;"));
        assertTrue(html.contains("download"));
        assertEquals("", MeetingAttachmentRenderer.agenda("/hub", List.of()));
    }

    @Test
    void filenamesAreEscapedAndExportUsesAbsoluteLinksNotEmbeddedImages() {
        List<EsMeetingAgendaAttachment> attachments = List.of(attachment(1L, "<a & \"x\">.png", "image/png"));
        String html = MeetingAttachmentRenderer.agenda("/hub", attachments);
        assertFalse(html.contains("<a &"));
        assertTrue(html.contains("&lt;a &amp; &quot;x&quot;&gt;.png"));
        String exported = MeetingAttachmentRenderer.confluence("https://example.org/hub/", attachments);
        assertTrue(exported.contains("href=\"https://example.org/hub/files/"));
        assertFalse(exported.contains("/hub//"));
        assertFalse(exported.contains("<img"));
    }

    @Test
    void workspaceOmitsRepeatedTitleAndWarningAndGatesWriteForms() {
        List<EsMeetingAgendaAttachment> attachments = List.of(attachment(1L, "slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        String writable = MeetingAttachmentRenderer.workspace("/hub", 65L, 204L,
                "Selected <item>", "token", attachments, true, null);
        assertFalse(writable.contains("Selected &lt;item&gt;"));
        assertFalse(writable.contains("Selected agenda item:"));
        assertFalse(writable.contains("Anyone with a file URL"));
        assertFalse(writable.contains("Keep authoritative copies"));
        assertTrue(writable.contains("meetingId=65&amp;itemId=204&amp;action=uploadAttachment"));
        assertTrue(writable.contains("name=\"csrfToken\" value=\"token\""));
        assertTrue(writable.contains("Remove from item"));
        assertTrue(writable.contains("class=\"aira-cluster\""));
        assertTrue(writable.contains("class=\"aira-inline-form\""));
        assertTrue(writable.contains("aira-button--small"));
        assertTrue(writable.contains("aria-label=\"Remove slides.pptx from item\""));
        assertTrue(writable.contains("<span aria-hidden=\"true\">&times;</span>"));
        assertFalse(writable.contains(">Remove from item</button>"));
        String readOnly = MeetingAttachmentRenderer.workspace("/hub", 65L, 204L,
                "Selected item", "token", attachments, false, null);
        assertTrue(readOnly.contains("slides.pptx"));
        assertFalse(readOnly.contains("<form"));
    }

    @Test
    void unavailableStorageDisablesUploadWithoutPreventingDetachment() {
        List<EsMeetingAgendaAttachment> attachments = List.of(attachment(1L, "image.png", "image/png"));
        String html = MeetingAttachmentRenderer.workspace("/hub", 65L, 204L,
                "Selected item", "token", attachments, true, "Local storage unavailable.");
        assertTrue(html.contains("Local storage unavailable."));
        assertTrue(html.contains("Remove from item"));
        assertFalse(html.contains("action=uploadAttachment"));
    }

    private static EsMeetingAgendaAttachment attachment(Long id, String name, String type) {
        StoredFile file = new StoredFile();
        file.setPublicId(UUID.randomUUID().toString());
        file.setOriginalFilename(name);
        file.setContentType(type);
        EsMeetingAgendaAttachment attachment = new EsMeetingAgendaAttachment();
        attachment.setAttachmentId(id);
        attachment.setStoredFile(file);
        attachment.setAttachedAt(LocalDateTime.of(2026, 10, 7, 12, 0));
        return attachment;
    }
}
