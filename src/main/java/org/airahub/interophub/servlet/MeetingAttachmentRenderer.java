package org.airahub.interophub.servlet;

import java.util.Comparator;
import java.util.List;
import org.airahub.interophub.model.EsMeetingAgendaAttachment;
import org.airahub.interophub.service.StoredFileService;

final class MeetingAttachmentRenderer {
    private MeetingAttachmentRenderer() { }

    static String agenda(String base, List<EsMeetingAgendaAttachment> attachments) {
        StringBuilder html = new StringBuilder();
        for (EsMeetingAgendaAttachment attachment : ordered(attachments)) {
            var file = attachment.getStoredFile();
            String url = escape(StoredFileService.readUrl(base, file));
            String name = escape(file.getOriginalFilename());
            if (file.isImage()) {
                html.append("<p><a href=\"").append(url).append("\"><img src=\"").append(url)
                        .append("\" alt=\"").append(name)
                        .append("\" style=\"max-width:100%;height:auto;\" /></a></p>");
            } else {
                html.append("<p><a class=\"aira-inline-link\" href=\"").append(url)
                        .append("\" download>").append(name).append("</a></p>");
            }
        }
        return html.toString();
    }

    static String confluence(String externalBase, List<EsMeetingAgendaAttachment> attachments) {
        String base = externalBase.endsWith("/") ? externalBase.substring(0, externalBase.length() - 1) : externalBase;
        StringBuilder html = new StringBuilder();
        for (EsMeetingAgendaAttachment attachment : ordered(attachments)) {
            html.append("<p><a href=\"")
                    .append(escape(StoredFileService.readUrl(base, attachment.getStoredFile())))
                    .append("\">").append(escape(attachment.getStoredFile().getOriginalFilename()))
                    .append("</a></p>");
        }
        return html.toString();
    }

    static String workspace(String context, Long meetingId, Long itemId, String title, String csrf,
            List<EsMeetingAgendaAttachment> attachments, boolean canModify, String uploadProblem) {
        StringBuilder html = new StringBuilder("<h4 class=\"aira-section-title\">Agenda attachments</h4>");
        for (EsMeetingAgendaAttachment attachment : ordered(attachments)) {
            html.append("<div class=\"aira-cluster\"><a href=\"")
                    .append(escape(StoredFileService.readUrl(context, attachment.getStoredFile())))
                    .append("\">").append(escape(attachment.getStoredFile().getOriginalFilename()))
                    .append("</a>");
            if (canModify) {
                html.append(formStart(context, meetingId, itemId, csrf, "removeAttachment", false))
                        .append("<input type=\"hidden\" name=\"attachmentId\" value=\"")
                        .append(attachment.getAttachmentId()).append("\" />")
                        .append("<button type=\"submit\" class=\"aira-button aira-button--tertiary aira-button--small\"")
                        .append(" aria-label=\"Remove ").append(escape(attachment.getStoredFile().getOriginalFilename()))
                        .append(" from item\" title=\"Remove from item\"><span aria-hidden=\"true\">")
                        .append("&times;</span></button></form>");
            }
            html.append("</div>");
        }
        if (attachments.isEmpty()) {
            html.append("<p class=\"aira-meta\">No attachments for this item.</p>");
        }
        if (canModify && uploadProblem == null) {
            html.append(formStart(context, meetingId, itemId, csrf, "uploadAttachment", true))
                    .append("<label class=\"aira-label\">Image, PDF or PowerPoint (25 MiB max)")
                    .append("<input class=\"aira-input\" type=\"file\" name=\"attachmentFile\" required")
                    .append(" accept=\".png,.jpg,.jpeg,.webp,.gif,.pdf,.ppt,.pptx\" /></label>")
                    .append("<button type=\"submit\" class=\"aira-button aira-button--secondary\">")
                    .append("Upload document to selected item</button></form>");
        } else {
            html.append("<p class=\"aira-meta\">").append(escape(canModify ? uploadProblem
                    : "Attachment changes require Meeting Controls permission and an open, active agenda item."))
                    .append("</p>");
        }
        html.append("<div data-attachment-message role=\"status\" aria-live=\"polite\"></div>");
        return html.toString();
    }

    private static String formStart(String context, Long meetingId, Long itemId, String csrf, String action,
            boolean multipart) {
        return "<form data-meeting-attachment-form method=\"post\""
                + (multipart ? " enctype=\"multipart/form-data\"" : "")
                + " class=\"" + (multipart ? "aira-stack aira-stack--compact" : "aira-inline-form") + "\" action=\""
                + escape((context == null ? "" : context) + EsMeetingWorkspaceServlet.WORKSPACE_PATH
                        + "?meetingId=" + meetingId + "&itemId=" + itemId + "&action=" + action)
                + "\"><input type=\"hidden\" name=\"csrfToken\" value=\"" + escape(csrf) + "\" />";
    }

    private static List<EsMeetingAgendaAttachment> ordered(List<EsMeetingAgendaAttachment> attachments) {
        return attachments.stream().sorted(Comparator
                .comparing((EsMeetingAgendaAttachment attachment) -> !attachment.getStoredFile().isImage())
                .thenComparing(EsMeetingAgendaAttachment::getAttachedAt)
                .thenComparing(EsMeetingAgendaAttachment::getAttachmentId)).toList();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
