package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import org.airahub.interophub.model.LegalTerm;

final class LegalTermsUiRenderer {
    private LegalTermsUiRenderer() {
    }

    static void renderTermsSection(PrintWriter out, List<LegalTerm> terms, Set<Long> selectedTermIds, String heading,
            String inputPrefix) {
        if (terms == null || terms.isEmpty()) {
            return;
        }

        out.println("      <fieldset class=\"aira-fieldset\">");
        out.println("        <legend class=\"aira-legend\">" + escapeHtml(orEmpty(heading)) + "</legend>");
        out.println("        <div class=\"aira-stack aira-stack--compact\">");
        for (LegalTerm term : terms) {
            String termIdText = term.getTermId() == null ? "0" : String.valueOf(term.getTermId());
            String titleText = escapeHtml(orEmpty(term.getTitle()));
            String shortText = escapeHtml(orEmpty(term.getShortText()));
            String fullText = orEmpty(term.getFullText()); // Do NOT escape full_text; it contains HTML links
            String fullTextUrl = escapeHtml(orEmpty(term.getFullTextUrl()));
            String checkedAttr = selectedTermIds != null && selectedTermIds.contains(term.getTermId()) ? " checked"
                    : "";

            out.println("          <div>");
            out.println("            <label class=\"aira-check\">");
            out.println("              <input type=\"checkbox\" name=\"" + escapeHtml(orEmpty(inputPrefix))
                    + termIdText + "\" value=\"1\"" + checkedAttr + " />");
            out.println("              <span><strong>" + titleText + ".</strong> " + shortText
                    + " <button type=\"button\" class=\"aira-button aira-button--link\" onclick=\"showLegalTerm('"
                    + termIdText + "')\">More details</button>"
                    + (fullTextUrl.isBlank() ? ""
                            : " <a class=\"aira-inline-link\" href=\"" + fullTextUrl
                                    + "\" target=\"_blank\" rel=\"noopener noreferrer\">Full policy link</a>")
                    + "</span>");
            out.println("            </label>");
            out.println("          </div>");

            out.println("          <dialog id=\"legal-term-modal-" + termIdText + "\" class=\"aira-dialog\">");
            out.println("            <h3 class=\"aira-dialog__title\">" + titleText + "</h3>");
            out.println("            <div class=\"aira-dialog__body\">");
            out.println("              <p>" + fullText + "</p>");
            if (!fullTextUrl.isBlank()) {
                out.println("              <p><a class=\"aira-inline-link\" href=\"" + fullTextUrl
                        + "\" target=\"_blank\" rel=\"noopener noreferrer\">Open full policy in new tab</a></p>");
            }
            out.println("            </div>");
            out.println("            <div class=\"aira-dialog__actions aira-form-actions aira-form-actions--end\">");
            out.println(
                    "              <button type=\"button\" class=\"aira-button aira-button--secondary\" onclick=\"hideLegalTerm('"
                            + termIdText + "')\">Close</button>");
            out.println("            </div>");
            out.println("          </dialog>");
        }
        out.println("        </div>");
        out.println("      </fieldset>");
    }

    static void renderTermsScript(PrintWriter out) {
        out.println("    <script>");
        out.println("      function showLegalTerm(termId) {");
        out.println("        var el = document.getElementById('legal-term-modal-' + termId);");
        out.println("        if (el && el.showModal) { el.showModal(); }");
        out.println("      }");
        out.println("      function hideLegalTerm(termId) {");
        out.println("        var el = document.getElementById('legal-term-modal-' + termId);");
        out.println("        if (el) { el.close(); }");
        out.println("      }");
        out.println("    </script>");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
