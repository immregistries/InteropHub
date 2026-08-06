package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;

/**
 * Admin-only: browse ES topics by Topic Space and create new ones. Editing an
 * existing topic now happens at {@code /es/topic-edit/{id}}
 * ({@link EsTopicEditServlet}), which is also open to Topic Champions/Support
 * for that topic.
 */
public class AdminEsTopicServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/topics";

    private final EsTopicDao esTopicDao;
    private final EsTopicMeetingDao esTopicMeetingDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsNeighborhoodDao esNeighborhoodDao;
    private final EsTopicSpaceDao topicSpaceDao;

    public AdminEsTopicServlet() {
        this.esTopicDao = new EsTopicDao();
        this.esTopicMeetingDao = new EsTopicMeetingDao();
        this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
        this.esNeighborhoodDao = new EsNeighborhoodDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        String selectedSpaceRaw = trimToNull(request.getParameter("space"));
        Long selectedSpaceId = parseId(selectedSpaceRaw);

        if (selectedSpaceRaw != null && selectedSpaceId == null) {
            renderList(request, response, "Invalid Topic Space selection.", null);
            return;
        }

        if ("new".equalsIgnoreCase(mode)) {
            EsTopic blank = new EsTopic();
            blank.setPriorityIis(0);
            blank.setPriorityEhr(0);
            blank.setPriorityCdc(0);
            blank.setStatus(EsTopic.EsTopicStatus.ACTIVE);
            renderNewTopicForm(request, response, blank, null, null, null);
            return;
        }

        renderList(request, response, null, selectedSpaceId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String topicSpaceIdRaw = trimToNull(request.getParameter("esTopicSpaceId"));

        String topicName = trimToNull(request.getParameter("topicName"));
        String description = trimToNull(request.getParameter("description"));
        String topicSummary = trimToNull(request.getParameter("topicSummary"));
        String topicEmoji = trimToNull(request.getParameter("topicEmoji"));
        Set<Long> selectedNeighborhoodIds = parseNeighborhoodIds(request.getParameterValues("esNeighborhoodId"));
        String priorityIisRaw = trimToNull(request.getParameter("priorityIis"));
        String priorityEhrRaw = trimToNull(request.getParameter("priorityEhr"));
        String priorityCdcRaw = trimToNull(request.getParameter("priorityCdc"));
        String stage = trimToNull(request.getParameter("stage"));
        String policyStatus = trimToNull(request.getParameter("policyStatus"));
        String topicType = trimToNull(request.getParameter("topicType"));
        String confluenceUrl = trimToNull(request.getParameter("confluenceUrl"));
        String statusRaw = trimToNull(request.getParameter("status"));

        boolean meetingEnabled = request.getParameter("meetingEnabled") != null;
        String meetingName = trimToNull(request.getParameter("meetingName"));
        String meetingDescription = trimToNull(request.getParameter("meetingDescription"));
        String meetingOnlineUrl = trimToNull(request.getParameter("onlineMeetingUrl"));
        String meetingOnlineDetails = trimToNull(request.getParameter("onlineMeetingDetails"));
        boolean meetingRequiresApproval = request.getParameter("meetingRequiresApproval") != null;

        String topicCodeParam = trimToNull(request.getParameter("topicCode"));
        EsTopic newTopic = new EsTopic();
        EsTopicMeeting newMeeting = null;
        try {
            Long defaultSpaceId = findActiveDefaultTopicSpaceId();
            Long requestedSpaceId = parseId(topicSpaceIdRaw);
            if (requestedSpaceId == null) {
                requestedSpaceId = defaultSpaceId;
            }
            EsTopicSpace targetSpace = requireActiveTopicSpace(requestedSpaceId, "Topic Space");
            String topicCodeVal = required(topicCodeParam, "Topic code");
            if (esTopicDao.findByTopicCode(topicCodeVal).isPresent()) {
                throw new IllegalArgumentException("Topic code is already in use.");
            }
            newTopic.setTopicCode(topicCodeVal);
            newTopic.setEsTopicSpaceId(targetSpace.getEsTopicSpaceId());
            newTopic.setTopicName(required(topicName, "Topic name"));
            newTopic.setDescription(description);
            newTopic.setTopicSummary(topicSummary);
            newTopic.setTopicEmoji(topicEmoji);
            newTopic.setNeighborhood(null);
            newTopic.setPriorityIis(parseRequiredInt(priorityIisRaw, "Priority IIS"));
            newTopic.setPriorityEhr(parseRequiredInt(priorityEhrRaw, "Priority EHR"));
            newTopic.setPriorityCdc(parseRequiredInt(priorityCdcRaw, "Priority CDC"));
            newTopic.setStage(stage);
            newTopic.setPolicyStatus(policyStatus);
            newTopic.setTopicType(topicType);
            newTopic.setConfluenceUrl(validateOptionalUrl(confluenceUrl, "Confluence URL"));
            newTopic.setStatus(parseStatus(required(statusRaw, "Status")));
            newTopic.setCreatedByUserId(adminUser.get().getUserId());

            validateNeighborhoodSelectionForTopicSpace(newTopic.getEsTopicSpaceId(), selectedNeighborhoodIds);

            EsTopic saved = esTopicDao.saveOrUpdate(newTopic);
            topicNeighborhoodDao.replaceTopicNeighborhoods(saved.getEsTopicId(), selectedNeighborhoodIds);

            if (meetingEnabled) {
                if (!topicSpaceDao.isActiveSpaceId(saved.getEsTopicSpaceId())) {
                    throw new IllegalArgumentException(
                            "Cannot create a meeting for a topic in an inactive Topic Space.");
                }
                newMeeting = new EsTopicMeeting();
                newMeeting.setEsTopicId(saved.getEsTopicId());
                newMeeting.setMeetingName(required(meetingName, "Meeting name"));
                newMeeting.setMeetingDescription(meetingDescription);
                newMeeting.setOnlineMeetingUrl(meetingOnlineUrl);
                newMeeting.setOnlineMeetingDetails(meetingOnlineDetails);
                newMeeting.setJoinRequiresApproval(meetingRequiresApproval);
                newMeeting.setStatus(EsTopicMeeting.MeetingStatus.ACTIVE);
                esTopicMeetingDao.saveOrUpdate(newMeeting);
            }

            response.sendRedirect(contextPath + "/es/topic/" + saved.getEsTopicId());
        } catch (Exception ex) {
            newTopic.setTopicCode(topicCodeParam);
            newTopic.setTopicName(topicName);
            newTopic.setDescription(description);
            newTopic.setTopicSummary(topicSummary);
            newTopic.setTopicEmoji(topicEmoji);
            newTopic.setNeighborhood(null);
            newTopic.setEsTopicSpaceId(parseId(topicSpaceIdRaw));
            newTopic.setStage(stage);
            newTopic.setPolicyStatus(policyStatus);
            newTopic.setTopicType(topicType);
            newTopic.setConfluenceUrl(confluenceUrl);
            if (priorityIisRaw != null) {
                newTopic.setPriorityIis(parseIntOrNull(priorityIisRaw));
            }
            if (priorityEhrRaw != null) {
                newTopic.setPriorityEhr(parseIntOrNull(priorityEhrRaw));
            }
            if (priorityCdcRaw != null) {
                newTopic.setPriorityCdc(parseIntOrNull(priorityCdcRaw));
            }
            if (statusRaw != null) {
                try {
                    newTopic.setStatus(parseStatus(statusRaw));
                } catch (Exception ignored) {
                    // Keep existing status if parse fails to avoid masking original validation
                    // error.
                }
            }
            if (meetingEnabled) {
                if (newMeeting == null) {
                    newMeeting = new EsTopicMeeting();
                }
                newMeeting.setMeetingName(meetingName);
                newMeeting.setMeetingDescription(meetingDescription);
                newMeeting.setOnlineMeetingUrl(meetingOnlineUrl);
                newMeeting.setOnlineMeetingDetails(meetingOnlineDetails);
                newMeeting.setJoinRequiresApproval(meetingRequiresApproval);
                newMeeting.setStatus(EsTopicMeeting.MeetingStatus.ACTIVE);
            }
            renderNewTopicForm(request, response, newTopic, meetingEnabled ? newMeeting : null, ex.getMessage(),
                    selectedNeighborhoodIds);
        }
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message,
            Long selectedSpaceId) throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicSpace> allTopicSpaces = topicSpaceDao.findAllOrdered();
        EsTopicSpace selectedSpace = selectedSpaceId == null ? null
                : topicSpaceDao.findById(selectedSpaceId).orElse(null);
        List<EsTopic> topics = selectedSpaceId == null
                ? List.of()
                : esTopicDao.findAllOrderByTopicName().stream()
                        .filter(topic -> selectedSpaceId.equals(topic.getEsTopicSpaceId()))
                        .toList();

        AdminShellRenderer.render(request, response, "ES Topics Admin - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">ES Topics</h2>");
                    out.println("            <p class=\"aira-meta\">View and manage Emerging Standards topics.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" method=\"get\" action=\"" + contextPath
                            + "/admin/es/topics\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"space\">Workspace (required)</label>");
                    out.println(
                            "                <select class=\"aira-select\" id=\"space\" name=\"space\" required onchange=\"this.form.submit()\">");
                    out.println("                  <option value=\"\">— Select —</option>");
                    for (EsTopicSpace topicSpace : allTopicSpaces) {
                        if (topicSpace.getEsTopicSpaceId() == null || trimToNull(topicSpace.getSpaceCode()) == null) {
                            continue;
                        }
                        boolean isSelected = selectedSpaceId != null
                                && selectedSpaceId.equals(topicSpace.getEsTopicSpaceId());
                        boolean active = Boolean.TRUE.equals(topicSpace.getIsActive());
                        String flags = isSelected ? " selected" : "";
                        if (!active && !isSelected) {
                            flags += " disabled";
                        }
                        out.println("                  <option value=\"" + topicSpace.getEsTopicSpaceId() + "\""
                                + flags + ">" + escapeHtml(orEmpty(topicSpace.getSpaceName()))
                                + (active ? "" : " (inactive)") + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");
                    out.println(
                            "              <noscript><button class=\"aira-button aira-button--secondary\" type=\"submit\">Show Topics</button></noscript>");
                    out.println("            </form>");

                    if (selectedSpace == null) {
                        out.println(
                                "            <p class=\"aira-meta\">Select a workspace above to view and manage its topics.</p>");
                    } else {
                        out.println("            <p class=\"aira-meta\"><strong>Current workspace:</strong> "
                                + escapeHtml(orEmpty(selectedSpace.getSpaceName())) + "</p>");
                    }

                    if (selectedSpace != null) {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead>");
                        out.println("                <tr>");
                        out.println("                  <th>Topic Name</th>");
                        out.println("                  <th>Stage</th>");
                        out.println("                  <th>Status</th>");
                        out.println("                </tr>");
                        out.println("              </thead>");
                        out.println("              <tbody>");
                        for (EsTopic topic : topics) {
                            out.println("                <tr>");
                            out.println(
                                    "                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                            + "/es/topic/" + topic.getEsTopicId()
                                            + "\">" + escapeHtml(orEmpty(topic.getTopicName())) + "</a></td>");
                            out.println("                  <td>" + escapeHtml(orEmpty(topic.getStage())) + "</td>");
                            out.println(
                                    "                  <td>"
                                            + escapeHtml(topic.getStatus() == null ? "" : topic.getStatus().name())
                                            + "</td>");
                            out.println("                </tr>");
                        }
                        if (topics.isEmpty()) {
                            out.println("                <tr>");
                            out.println(
                                    "                  <td colspan=\"3\">No ES topics found for this workspace.</td>");
                            out.println("                </tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");

                        out.println("            <div class=\"aira-action-group\">");
                        out.println("              <a class=\"aira-button aira-button--primary\" href=\""
                                + contextPath + "/admin/es/topics?mode=new&space="
                                + selectedSpaceId + "\">Add New Topic</a>");
                        out.println("            </div>");
                    }
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderNewTopicForm(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
            EsTopicMeeting meeting, String errorMessage, Set<Long> selectedNeighborhoodIdsOverride)
            throws IOException {
        String contextPath = request.getContextPath();
        boolean meetingEnabled = meeting != null && meeting.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE;
        List<EsTopicSpace> allTopicSpaces = topicSpaceDao.findAllOrdered();
        Long selectedTopicSpaceId = topic.getEsTopicSpaceId();
        if (selectedTopicSpaceId == null) {
            selectedTopicSpaceId = findActiveDefaultTopicSpaceId();
            topic.setEsTopicSpaceId(selectedTopicSpaceId);
        }
        final Long selectedTopicSpaceIdFinal = selectedTopicSpaceId;
        List<EsNeighborhood> allNeighborhoods = esNeighborhoodDao.findAllActive();
        List<String> policyStatuses = esTopicDao.findDistinctPolicyStatuses();
        List<String> topicTypes = esTopicDao.findDistinctTopicTypes();
        Set<Long> selectedNeighborhoodIds = selectedNeighborhoodIdsOverride == null ? Set.of()
                : selectedNeighborhoodIdsOverride;
        final Set<Long> selectedNeighborhoodIdsFinal = selectedNeighborhoodIds;

        AdminShellRenderer.render(request, response, "Add New ES Topic - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Add New ES Topic</h2>");

                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println(
                            "            <form class=\"aira-form\" action=\"" + contextPath
                                    + "/admin/es/topics\" method=\"post\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"topicCode\">Topic Code (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"topicCode\" name=\"topicCode\" type=\"text\" required maxlength=\"80\" value=\""
                                    + escapeHtml(orEmpty(topic.getTopicCode())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"topicName\">Topic Name (required)</label>");
                    out.println("                <input class=\"aira-input\" id=\"topicName\" name=\"topicName\" type=\"text\" required value=\""
                            + escapeHtml(orEmpty(topic.getTopicName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"description\">Description</label>");
                    out.println("                <textarea class=\"aira-textarea\" id=\"description\" name=\"description\" rows=\"5\">"
                            + escapeHtml(orEmpty(topic.getDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"topicSummary\">Topic Summary (one sentence)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"topicSummary\" name=\"topicSummary\" type=\"text\" maxlength=\"300\" value=\""
                                    + escapeHtml(orEmpty(topic.getTopicSummary())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"topicEmoji\">Topic Emoji</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"topicEmoji\" name=\"topicEmoji\" type=\"text\" maxlength=\"64\" placeholder=\"e.g. 🧠\" value=\""
                                    + escapeHtml(orEmpty(topic.getTopicEmoji())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"esTopicSpaceId\">Topic Space (required)</label>");
                    out.println("                <select class=\"aira-select\" id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
                    out.println("                  <option value=\"\">— Select —</option>");
                    for (EsTopicSpace topicSpace : allTopicSpaces) {
                        if (topicSpace.getEsTopicSpaceId() == null
                                || trimToNull(topicSpace.getSpaceCode()) == null) {
                            continue;
                        }
                        boolean isCurrent = topicSpace.getEsTopicSpaceId().equals(selectedTopicSpaceIdFinal);
                        boolean active = Boolean.TRUE.equals(topicSpace.getIsActive());
                        String optionFlags = "";
                        if (isCurrent) {
                            optionFlags += " selected";
                        }
                        if (!active && !isCurrent) {
                            optionFlags += " disabled";
                        }
                        out.println("                  <option value=\"" + topicSpace.getEsTopicSpaceId() + "\"" + optionFlags
                                + ">"
                                + escapeHtml(orEmpty(topicSpace.getSpaceName()))
                                + (active ? "" : " (inactive)")
                                + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <fieldset style=\"margin-bottom: 1em;\">");
                    out.println("                <legend>Neighborhood(s)</legend>");
                    if (allNeighborhoods.isEmpty()) {
                        out.println("                <p class=\"aira-meta\"><em>No neighborhoods defined.</em></p>");
                        out.println("                <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/neighborhoods?mode=new\">Create a neighborhood</a> and then return to this topic.</p>");
                    } else {
                        for (EsNeighborhood nh : allNeighborhoods) {
                            Long nhId = nh.getEsNeighborhoodId();
                            boolean nhChecked = nhId != null && selectedNeighborhoodIdsFinal.contains(nhId);
                            out.println("                <label class=\"aira-radio js-neighborhood-option\" data-space-id=\""
                                    + escapeHtml(String.valueOf(nh.getEsTopicSpaceId()))
                                    + "\"><input type=\"checkbox\" name=\"esNeighborhoodId\" value=\""
                                    + escapeHtml(String.valueOf(nhId)) + "\"" + (nhChecked ? " checked" : "")
                                    + " /> "
                                    + escapeHtml(orEmpty(nh.getNeighborhoodName())) + "</label>");
                        }
                        out.println("                <script>");
                        out.println("                  (function(){");
                        out.println("                    var select = document.getElementById('esTopicSpaceId');");
                        out.println("                    if (!select) { return; }");
                        out.println(
                                "                    var options = Array.prototype.slice.call(document.querySelectorAll('.js-neighborhood-option')); ");
                        out.println("                    function applyNeighborhoodFilter(){");
                        out.println("                      var selected = (select.value || '').trim();");
                        out.println("                      options.forEach(function(label){");
                        out.println(
                                "                        var matches = selected && (label.getAttribute('data-space-id') === selected);");
                        out.println("                        label.style.display = matches ? '' : 'none';");
                        out.println("                        if (!matches) {");
                        out.println(
                                "                          var checkbox = label.querySelector('input[type=checkbox]');");
                        out.println("                          if (checkbox) { checkbox.checked = false; }");
                        out.println("                        }");
                        out.println("                      });");
                        out.println("                    }");
                        out.println("                    select.addEventListener('change', applyNeighborhoodFilter);");
                        out.println("                    applyNeighborhoodFilter();");
                        out.println("                  })();");
                        out.println("                </script>");
                    }
                    out.println("                <p style=\"margin-top: 0.75em;\"><a class=\"aira-inline-link\" href=\""
                            + contextPath
                            + "/admin/es/neighborhoods\">Manage neighborhoods</a></p>");
                    out.println("              </fieldset>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"priorityIis\">Priority IIS (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"priorityIis\" name=\"priorityIis\" type=\"number\" required value=\""
                                    + escapeHtml(String
                                            .valueOf(topic.getPriorityIis() == null ? 0 : topic.getPriorityIis()))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"priorityEhr\">Priority EHR (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"priorityEhr\" name=\"priorityEhr\" type=\"number\" required value=\""
                                    + escapeHtml(String
                                            .valueOf(topic.getPriorityEhr() == null ? 0 : topic.getPriorityEhr()))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"priorityCdc\">Priority CDC (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"priorityCdc\" name=\"priorityCdc\" type=\"number\" required value=\""
                                    + escapeHtml(String
                                            .valueOf(topic.getPriorityCdc() == null ? 0 : topic.getPriorityCdc()))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"stage\">Stage</label>");
                    out.println("                <select class=\"aira-select\" id=\"stage\" name=\"stage\">");
                    out.println("                  <option value=\"\">— Select —</option>");
                    for (String stageOpt : new String[] { "Start", "Gather", "Draft", "Pilot", "Rollout", "Monitor",
                            "Parked" }) {
                        String sel = stageOpt.equalsIgnoreCase(orEmpty(topic.getStage())) ? " selected" : "";
                        out.println(
                                "                  <option value=\"" + stageOpt + "\"" + sel + ">" + stageOpt + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"policyStatus\">Policy Status</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"policyStatus\" name=\"policyStatus\" type=\"text\" list=\"policyStatusList\" value=\""
                                    + escapeHtml(orEmpty(topic.getPolicyStatus())) + "\" />");
                    out.println("                <datalist id=\"policyStatusList\">");
                    for (String ps : policyStatuses) {
                        out.println("                  <option value=\"" + escapeHtml(ps) + "\" />");
                    }
                    out.println("                </datalist>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"topicType\">Topic Type</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"topicType\" name=\"topicType\" type=\"text\" list=\"topicTypeList\" value=\""
                                    + escapeHtml(orEmpty(topic.getTopicType())) + "\" />");
                    out.println("                <datalist id=\"topicTypeList\">");
                    for (String tt : topicTypes) {
                        out.println("                  <option value=\"" + escapeHtml(tt) + "\" />");
                    }
                    out.println("                </datalist>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"confluenceUrl\">Confluence URL</label>");
                    out.println("                <input class=\"aira-input\" id=\"confluenceUrl\" name=\"confluenceUrl\" type=\"url\" value=\""
                            + escapeHtml(orEmpty(topic.getConfluenceUrl())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"status\">Status (required)</label>");
                    out.println("                <select class=\"aira-select\" id=\"status\" name=\"status\" required>");
                    out.println(
                            "                  <option value=\"ACTIVE\"" + selectedStatus(topic, EsTopic.EsTopicStatus.ACTIVE)
                                    + ">ACTIVE</option>");
                    out.println("                  <option value=\"RETIRED\""
                            + selectedStatus(topic, EsTopic.EsTopicStatus.RETIRED)
                            + ">RETIRED</option>");
                    out.println("                  <option value=\"ARCHIVED\""
                            + selectedStatus(topic, EsTopic.EsTopicStatus.ARCHIVED)
                            + ">ARCHIVED</option>");
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <h3 class=\"aira-subsection-title\">Meeting Configuration</h3>");
                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"meetingEnabled\""
                            + (meetingEnabled ? " checked" : "")
                            + " /> Enable Meeting Support</label>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"meetingName\">Meeting Name (required when enabled)</label>");
                    out.println("                <input class=\"aira-input\" id=\"meetingName\" name=\"meetingName\" type=\"text\" value=\""
                            + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"meetingDescription\">Meeting Description</label>");
                    out.println("                <textarea class=\"aira-textarea\" id=\"meetingDescription\" name=\"meetingDescription\" rows=\"4\">"
                            + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingDescription()))
                            + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"onlineMeetingUrl\">Meeting URL (e.g. Zoom link)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"onlineMeetingUrl\" name=\"onlineMeetingUrl\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(meeting == null ? null : meeting.getOnlineMeetingUrl()))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println(
                            "                <label for=\"onlineMeetingDetails\">Connection Details (dial-in info, passcode, etc.)</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"onlineMeetingDetails\" name=\"onlineMeetingDetails\" rows=\"5\">"
                                    + escapeHtml(
                                            orEmpty(meeting == null ? null : meeting.getOnlineMeetingDetails()))
                                    + "</textarea>");
                    out.println("              </div>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"meetingRequiresApproval\""
                            + (meeting != null && Boolean.TRUE.equals(meeting.getJoinRequiresApproval())
                                    ? " checked"
                                    : "")
                            + " /> Join Requires Approval</label>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/es/topics?space="
                                    + topic.getEsTopicSpaceId()
                                    + "\">Back to Topics</a></p>");
                    out.println("          </section>");
                });
    }

    private String selectedStatus(EsTopic topic, EsTopic.EsTopicStatus status) {
        return topic.getStatus() == status ? " selected" : "";
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Set<Long> parseNeighborhoodIds(String[] values) {
        if (values == null || values.length == 0) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : values) {
            Long id = parseId(trimToNull(value));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Integer parseRequiredInt(String value, String label) {
        String normalized = required(value, label);
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private Integer parseIntOrNull(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private EsTopic.EsTopicStatus parseStatus(String value) {
        try {
            return EsTopic.EsTopicStatus.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Status must be ACTIVE, RETIRED, or ARCHIVED.");
        }
    }

    private String required(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private String validateOptionalUrl(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException(label + " must start with http:// or https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException(label + " must include a host.");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " is invalid.");
        }
    }

    private EsTopicSpace requireActiveTopicSpace(Long topicSpaceId, String label) {
        EsTopicSpace topicSpace = topicSpaceDao.findById(topicSpaceId)
                .orElseThrow(() -> new IllegalArgumentException(label + " is invalid."));
        if (!topicSpaceDao.isActiveSpaceId(topicSpaceId)) {
            throw new IllegalArgumentException("Only active Topic Spaces may receive new topics.");
        }
        return topicSpace;
    }

    private void validateNeighborhoodSelectionForTopicSpace(Long topicSpaceId, Set<Long> selectedNeighborhoodIds) {
        if (selectedNeighborhoodIds == null || selectedNeighborhoodIds.isEmpty()) {
            return;
        }
        Set<Long> allowedIds = esNeighborhoodDao.findAllOrderedBySpaceId(topicSpaceId).stream()
                .map(EsNeighborhood::getEsNeighborhoodId)
                .collect(Collectors.toSet());
        List<Long> invalidIds = selectedNeighborhoodIds.stream()
                .filter(id -> id != null && !allowedIds.contains(id))
                .collect(Collectors.toList());
        if (!invalidIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Neighborhood assignments must belong to the selected Topic Space. Invalid neighborhood IDs: "
                            + invalidIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
    }

    private Long findActiveDefaultTopicSpaceId() {
        Long defaultSpaceId = topicSpaceDao.findBySpaceCode("emerging-standards")
                .map(org.airahub.interophub.model.EsTopicSpace::getEsTopicSpaceId)
                .orElse(null);
        if (!topicSpaceDao.isActiveSpaceId(defaultSpaceId)) {
            throw new IllegalArgumentException(
                    "Cannot create topic because the default Emerging Standards Topic Space is missing or inactive.");
        }
        return defaultSpaceId;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
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
