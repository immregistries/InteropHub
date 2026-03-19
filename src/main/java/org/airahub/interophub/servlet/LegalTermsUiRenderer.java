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

        out.println("      <div style=\"margin-top:1rem;\">");
        out.println("        <h3 style=\"margin:0 0 0.75rem 0;\">" + escapeHtml(orEmpty(heading)) + "</h3>");
        for (LegalTerm term : terms) {
            String termIdText = term.getTermId() == null ? "0" : String.valueOf(term.getTermId());
            String titleText = escapeHtml(orEmpty(term.getTitle()));
            String shortText = escapeHtml(orEmpty(term.getShortText()));
            String fullText = orEmpty(term.getFullText()); // Do NOT escape full_text; it contains HTML links
            String fullTextUrl = escapeHtml(orEmpty(term.getFullTextUrl()));
            String checkedAttr = selectedTermIds != null && selectedTermIds.contains(term.getTermId()) ? " checked"
                    : "";

            out.println("        <div style=\"margin-bottom:1rem; padding-bottom:0.25rem;\">");
            out.println("          <div style=\"font-weight:600; margin-bottom:0.25rem;\">" + titleText + "</div>");
            out.println("          <label style=\"display:block; margin-left:0.2rem;\">");
            out.println("            <input type=\"checkbox\" name=\"" + escapeHtml(orEmpty(inputPrefix)) + termIdText
                    + "\" value=\"1\"" + checkedAttr + " /> ");
            out.println("            " + shortText + " ");
            out.println("            <a href=\"#\" onclick=\"showLegalTerm('" + termIdText
                    + "'); return false;\">More details</a>");
            if (!fullTextUrl.isBlank()) {
                out.println("            &nbsp;|&nbsp;<a href=\"" + fullTextUrl
                        + "\" target=\"_blank\" rel=\"noopener noreferrer\">Full policy link</a>");
            }
            out.println("          </label>");
            out.println("        </div>");

            out.println("        <div id=\"legal-term-modal-" + termIdText
                    + "\" style=\"display:none; position:fixed; inset:0; background:rgba(0,0,0,0.55); z-index:1000;\">");
            out.println(
                    "          <div style=\"background:#fff; max-width:760px; margin:6vh auto; padding:1rem 1.2rem; border-radius:6px; max-height:85vh; overflow:auto;\">");
            out.println("            <h3 style=\"margin-top:0;\">" + titleText + "</h3>");
            out.println("            <p style=\"white-space:pre-wrap;\">" + fullText + "</p>");
            if (!fullTextUrl.isBlank()) {
                out.println("            <p><a href=\"" + fullTextUrl
                        + "\" target=\"_blank\" rel=\"noopener noreferrer\">Open full policy in new tab</a></p>");
            }
            out.println("            <p><button type=\"button\" onclick=\"hideLegalTerm('" + termIdText
                    + "')\">Close</button></p>");
            out.println("          </div>");
            out.println("        </div>");
        }
        out.println("      </div>");
    }

    static void renderTermsScript(PrintWriter out) {
        out.println("    <script>");
        out.println("      function showLegalTerm(termId) {");
        out.println("        var el = document.getElementById('legal-term-modal-' + termId);");
        out.println("        if (el) { el.style.display = 'block'; }");
        out.println("      }");
        out.println("      function hideLegalTerm(termId) {");
        out.println("        var el = document.getElementById('legal-term-modal-' + termId);");
        out.println("        if (el) { el.style.display = 'none'; }");
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
