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
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.immregistries.aira.web.AiraPage;

/**
 * Lets an admin, or a Topic Champion / Support person for this specific
 * topic, edit an existing ES topic. Mapped to {@code /es/topic-edit/{id}}.
 * Replaces the admin-only edit form that used to live at
 * {@code /admin/es/topics?esTopicId=...&mode=edit}.
 */
public class EsTopicEditServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsTopicMeetingDao esTopicMeetingDao;
    private final EsSubscriptionDao esSubscriptionDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsNeighborhoodDao esNeighborhoodDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsMeetingDao esMeetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;

    public EsTopicEditServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.esTopicMeetingDao = new EsTopicMeetingDao();
        this.esSubscriptionDao = new EsSubscriptionDao();
        this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
        this.esNeighborhoodDao = new EsNeighborhoodDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.esMeetingDao = new EsMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        Long topicId = parseTopicId(request.getPathInfo());
        if (topicId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }

        EsTopic topic = esTopicDao.findById(topicId).orElse(null);
        if (topic == null) {
            renderNotFound(request, response, contextPath);
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return;
        }
        User viewer = authenticatedUser.get();
        boolean isAdmin = authFlowService.isAdminUser(viewer);
        if (!canManageTopic(viewer, isAdmin, topicId)) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return;
        }

        EsTopicMeeting meeting = esTopicMeetingDao.findByTopicId(topicId).orElse(null);
        renderEditForm(request, response, topic, meeting, null, isAdmin, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        String topicIdRaw = trimToNull(request.getParameter("esTopicId"));
        Long topicId = parseId(topicIdRaw);
        if (topicId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }

        EsTopic topic = esTopicDao.findById(topicId).orElse(null);
        if (topic == null) {
            renderNotFound(request, response, contextPath);
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return;
        }
        User viewer = authenticatedUser.get();
        boolean isAdmin = authFlowService.isAdminUser(viewer);
        if (!canManageTopic(viewer, isAdmin, topicId)) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return;
        }

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

        EsTopicMeeting meeting = esTopicMeetingDao.findByTopicId(topicId).orElse(null);

        try {
            Long requestedSpaceId = parseId(required(topicSpaceIdRaw, "Topic Space"));
            EsTopicSpace targetSpace = topicSpaceDao.findById(requestedSpaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Topic Space is invalid."));
            boolean movingTopicSpace = !requestedSpaceId.equals(topic.getEsTopicSpaceId());
            if (movingTopicSpace && !topicSpaceDao.isActiveSpaceId(requestedSpaceId)) {
                throw new IllegalArgumentException("Only active Topic Spaces may receive moved topics.");
            }
            if (movingTopicSpace) {
                validateTopicMoveMeetingAssignments(topic.getEsTopicId(), targetSpace);
            }

            topic.setEsTopicSpaceId(requestedSpaceId);
            topic.setTopicName(required(topicName, "Topic name"));
            topic.setDescription(description);
            topic.setTopicSummary(topicSummary);
            topic.setTopicEmoji(topicEmoji);
            topic.setNeighborhood(null);
            topic.setPriorityIis(parseRequiredInt(priorityIisRaw, "Priority IIS"));
            topic.setPriorityEhr(parseRequiredInt(priorityEhrRaw, "Priority EHR"));
            topic.setPriorityCdc(parseRequiredInt(priorityCdcRaw, "Priority CDC"));
            topic.setStage(stage);
            topic.setPolicyStatus(policyStatus);
            topic.setTopicType(topicType);
            topic.setConfluenceUrl(validateOptionalUrl(confluenceUrl, "Confluence URL"));
            topic.setStatus(parseStatus(required(statusRaw, "Status")));

            validateNeighborhoodSelectionForTopicSpace(topic.getEsTopicSpaceId(), selectedNeighborhoodIds);

            esTopicDao.saveOrUpdate(topic);
            topicNeighborhoodDao.replaceTopicNeighborhoods(topic.getEsTopicId(), selectedNeighborhoodIds);

            if (meetingEnabled) {
                if (!topicSpaceDao.isActiveSpaceId(topic.getEsTopicSpaceId())) {
                    throw new IllegalArgumentException(
                            "Cannot create a meeting for a topic in an inactive Topic Space.");
                }
                if (meeting == null) {
                    meeting = new EsTopicMeeting();
                    meeting.setEsTopicId(topic.getEsTopicId());
                }
                meeting.setMeetingName(required(meetingName, "Meeting name"));
                meeting.setMeetingDescription(meetingDescription);
                meeting.setOnlineMeetingUrl(meetingOnlineUrl);
                meeting.setOnlineMeetingDetails(meetingOnlineDetails);
                meeting.setJoinRequiresApproval(meetingRequiresApproval);
                meeting.setStatus(EsTopicMeeting.MeetingStatus.ACTIVE);
                meeting.setDisabledAt(null);
                meeting.setDisabledByUserId(null);
                esTopicMeetingDao.saveOrUpdate(meeting);
            } else if (meeting != null) {
                esTopicMeetingDao.disableMeeting(meeting, viewer.getUserId());
            }

            response.sendRedirect(contextPath + "/es/topic/" + topic.getEsTopicId());
        } catch (Exception ex) {
            topic.setTopicName(topicName);
            topic.setDescription(description);
            topic.setTopicSummary(topicSummary);
            topic.setTopicEmoji(topicEmoji);
            topic.setNeighborhood(null);
            topic.setEsTopicSpaceId(parseId(topicSpaceIdRaw));
            topic.setStage(stage);
            topic.setPolicyStatus(policyStatus);
            topic.setTopicType(topicType);
            topic.setConfluenceUrl(confluenceUrl);
            if (priorityIisRaw != null) {
                topic.setPriorityIis(parseIntOrNull(priorityIisRaw));
            }
            if (priorityEhrRaw != null) {
                topic.setPriorityEhr(parseIntOrNull(priorityEhrRaw));
            }
            if (priorityCdcRaw != null) {
                topic.setPriorityCdc(parseIntOrNull(priorityCdcRaw));
            }
            if (statusRaw != null) {
                try {
                    topic.setStatus(parseStatus(statusRaw));
                } catch (Exception ignored) {
                    // Keep existing status if parse fails to avoid masking original validation
                    // error.
                }
            }

            if (meetingEnabled) {
                if (meeting == null) {
                    meeting = new EsTopicMeeting();
                    meeting.setEsTopicId(topic.getEsTopicId());
                }
                meeting.setMeetingName(meetingName);
                meeting.setMeetingDescription(meetingDescription);
                meeting.setOnlineMeetingUrl(meetingOnlineUrl);
                meeting.setOnlineMeetingDetails(meetingOnlineDetails);
                meeting.setJoinRequiresApproval(meetingRequiresApproval);
                meeting.setStatus(EsTopicMeeting.MeetingStatus.ACTIVE);
            }

            renderEditForm(request, response, topic, meetingEnabled ? meeting : null, ex.getMessage(), isAdmin,
                    selectedNeighborhoodIds);
        }
    }

    private boolean canManageTopic(User viewer, boolean isAdmin, Long topicId) {
        if (isAdmin) {
            return true;
        }
        String viewerEmail = trimToNull(viewer.getEmailNormalized());
        return esSubscriptionDao.findActiveByTopicId(topicId).stream()
                .anyMatch(s -> isChampionEquivalentStatus(s.getStatus())
                        && ((s.getUserId() != null && s.getUserId().equals(viewer.getUserId()))
                                || (viewerEmail != null && viewerEmail.equals(s.getEmailNormalized()))));
    }

    private boolean isChampionEquivalentStatus(EsSubscription.SubscriptionStatus status) {
        return status == EsSubscription.SubscriptionStatus.CHAMPION
                || status == EsSubscription.SubscriptionStatus.SUPPORT;
    }

    private void renderNotFound(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        AiraPage page = InteropAiraPageFactory.base(request, "Topic Not Found - InteropHub").build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Topic Not Found</h1>");
            out.println("        </div>");
            out.println("      </div>");
            out.println(
                    "      <div class=\"aira-alert aira-alert--danger\"><p>This topic could not be found.</p></div>");
            out.println("      <p><a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/es/topics\">Back to Topics</a></p>");
            out.println("    </div>");
            page.writeEnd(out);
        }
    }

    private void renderEditForm(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
            EsTopicMeeting meeting, String errorMessage, boolean isAdminViewer,
            Set<Long> selectedNeighborhoodIdsOverride) throws IOException {
        String contextPath = request.getContextPath();
        boolean meetingEnabled = meeting != null && meeting.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE;
        List<EsTopicSpace> allTopicSpaces = topicSpaceDao.findAllOrdered();
        Long selectedTopicSpaceId = topic.getEsTopicSpaceId();
        List<EsNeighborhood> allNeighborhoods = esNeighborhoodDao.findAllActive();
        List<String> policyStatuses = esTopicDao.findDistinctPolicyStatuses();
        List<String> topicTypes = esTopicDao.findDistinctTopicTypes();
        Set<Long> selectedNeighborhoodIds = selectedNeighborhoodIdsOverride;
        if (selectedNeighborhoodIds == null) {
            selectedNeighborhoodIds = topicNeighborhoodDao.findNeighborhoodIdsByTopicId(topic.getEsTopicId());
        }
        final Set<Long> selectedNeighborhoodIdsFinal = selectedNeighborhoodIds;
        final Long selectedTopicSpaceIdFinal = selectedTopicSpaceId;
        EsTopicSpace currentTopicSpace = selectedTopicSpaceId == null ? null
                : topicSpaceDao.findById(selectedTopicSpaceId).orElse(null);

        AiraPage page = InteropAiraPageFactory.base(request, "Edit " + orEmpty(topic.getTopicName()) + " - InteropHub")
                .applicationSubtitle("Topic management")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        currentTopicSpace != null ? currentTopicSpace.getSpaceName() : "InteropHub",
                        currentTopicSpace != null ? currentTopicSpace.getSpaceCode() : null,
                        true,
                        false))
                .build();

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Edit Topic</h1>");
            out.println("        </div>");
            out.println("      </div>");

            out.println("      <section class=\"aira-panel\">");

            if (errorMessage != null && !errorMessage.isBlank()) {
                out.println(
                        "        <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> "
                                + escapeHtml(errorMessage) + "</p></div>");
            }

            out.println("        <form class=\"aira-form\" action=\"" + contextPath + "/es/topic-edit/"
                    + topic.getEsTopicId() + "\" method=\"post\">");
            out.println("          <input type=\"hidden\" name=\"esTopicId\" value=\"" + topic.getEsTopicId()
                    + "\" />");
            out.println("          <p><strong>Topic Code:</strong> " + escapeHtml(orEmpty(topic.getTopicCode()))
                    + "</p>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"topicName\">Topic Name (required)</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"topicName\" name=\"topicName\" type=\"text\" required value=\""
                            + escapeHtml(orEmpty(topic.getTopicName())) + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"description\">Description</label>");
            out.println(
                    "            <textarea class=\"aira-textarea\" id=\"description\" name=\"description\" rows=\"5\">"
                            + escapeHtml(orEmpty(topic.getDescription())) + "</textarea>");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"topicSummary\">Topic Summary (one sentence)</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"topicSummary\" name=\"topicSummary\" type=\"text\" maxlength=\"300\" value=\""
                            + escapeHtml(orEmpty(topic.getTopicSummary())) + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"topicEmoji\">Topic Emoji</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"topicEmoji\" name=\"topicEmoji\" type=\"text\" maxlength=\"64\" placeholder=\"e.g. 🧠\" value=\""
                            + escapeHtml(orEmpty(topic.getTopicEmoji())) + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"esTopicSpaceId\">Topic Space (required)</label>");
            out.println(
                    "            <select class=\"aira-select\" id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
            out.println("              <option value=\"\">— Select —</option>");
            for (EsTopicSpace topicSpace : allTopicSpaces) {
                if (topicSpace.getEsTopicSpaceId() == null || trimToNull(topicSpace.getSpaceCode()) == null) {
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
                out.println("              <option value=\"" + topicSpace.getEsTopicSpaceId() + "\"" + optionFlags
                        + ">" + escapeHtml(orEmpty(topicSpace.getSpaceName()))
                        + (active ? "" : " (inactive)") + "</option>");
            }
            out.println("            </select>");
            out.println("          </div>");

            out.println("          <fieldset style=\"margin-bottom: 1em;\">");
            out.println("            <legend>Neighborhood(s)</legend>");
            if (allNeighborhoods.isEmpty()) {
                out.println("            <p class=\"aira-meta\"><em>No neighborhoods defined.</em></p>");
            } else {
                for (EsNeighborhood nh : allNeighborhoods) {
                    Long nhId = nh.getEsNeighborhoodId();
                    boolean nhChecked = nhId != null && selectedNeighborhoodIdsFinal.contains(nhId);
                    out.println("            <label class=\"aira-radio js-neighborhood-option\" data-space-id=\""
                            + escapeHtml(String.valueOf(nh.getEsTopicSpaceId()))
                            + "\"><input type=\"checkbox\" name=\"esNeighborhoodId\" value=\""
                            + escapeHtml(String.valueOf(nhId)) + "\"" + (nhChecked ? " checked" : "") + " /> "
                            + escapeHtml(orEmpty(nh.getNeighborhoodName())) + "</label>");
                }
                out.println("            <script>");
                out.println("              (function(){");
                out.println("                var select = document.getElementById('esTopicSpaceId');");
                out.println("                if (!select) { return; }");
                out.println(
                        "                var options = Array.prototype.slice.call(document.querySelectorAll('.js-neighborhood-option')); ");
                out.println("                function applyNeighborhoodFilter(){");
                out.println("                  var selected = (select.value || '').trim();");
                out.println("                  options.forEach(function(label){");
                out.println(
                        "                    var matches = selected && (label.getAttribute('data-space-id') === selected);");
                out.println("                    label.style.display = matches ? '' : 'none';");
                out.println("                    if (!matches) {");
                out.println("                      var checkbox = label.querySelector('input[type=checkbox]');");
                out.println("                      if (checkbox) { checkbox.checked = false; }");
                out.println("                    }");
                out.println("                  });");
                out.println("                }");
                out.println("                select.addEventListener('change', applyNeighborhoodFilter);");
                out.println("                applyNeighborhoodFilter();");
                out.println("              })();");
                out.println("            </script>");
            }
            if (isAdminViewer) {
                out.println("            <p style=\"margin-top: 0.75em;\"><a class=\"aira-inline-link\" href=\""
                        + contextPath + "/admin/es/neighborhoods\">Manage neighborhoods</a></p>");
            }
            out.println("          </fieldset>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"priorityIis\">Priority IIS (required)</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"priorityIis\" name=\"priorityIis\" type=\"number\" required value=\""
                            + escapeHtml(String.valueOf(topic.getPriorityIis() == null ? 0 : topic.getPriorityIis()))
                            + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"priorityEhr\">Priority EHR (required)</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"priorityEhr\" name=\"priorityEhr\" type=\"number\" required value=\""
                            + escapeHtml(String.valueOf(topic.getPriorityEhr() == null ? 0 : topic.getPriorityEhr()))
                            + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"priorityCdc\">Priority CDC (required)</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"priorityCdc\" name=\"priorityCdc\" type=\"number\" required value=\""
                            + escapeHtml(String.valueOf(topic.getPriorityCdc() == null ? 0 : topic.getPriorityCdc()))
                            + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"stage\">Stage</label>");
            out.println("            <select class=\"aira-select\" id=\"stage\" name=\"stage\">");
            out.println("              <option value=\"\">— Select —</option>");
            for (String stageOpt : new String[] { "Start", "Gather", "Draft", "Pilot", "Rollout", "Monitor",
                    "Parked" }) {
                String sel = stageOpt.equalsIgnoreCase(orEmpty(topic.getStage())) ? " selected" : "";
                out.println("              <option value=\"" + stageOpt + "\"" + sel + ">" + stageOpt
                        + "</option>");
            }
            out.println("            </select>");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"policyStatus\">Policy Status</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"policyStatus\" name=\"policyStatus\" type=\"text\" list=\"policyStatusList\" value=\""
                            + escapeHtml(orEmpty(topic.getPolicyStatus())) + "\" />");
            out.println("            <datalist id=\"policyStatusList\">");
            for (String ps : policyStatuses) {
                out.println("              <option value=\"" + escapeHtml(ps) + "\" />");
            }
            out.println("            </datalist>");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"topicType\">Topic Type</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"topicType\" name=\"topicType\" type=\"text\" list=\"topicTypeList\" value=\""
                            + escapeHtml(orEmpty(topic.getTopicType())) + "\" />");
            out.println("            <datalist id=\"topicTypeList\">");
            for (String tt : topicTypes) {
                out.println("              <option value=\"" + escapeHtml(tt) + "\" />");
            }
            out.println("            </datalist>");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"confluenceUrl\">Confluence URL</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"confluenceUrl\" name=\"confluenceUrl\" type=\"url\" value=\""
                            + escapeHtml(orEmpty(topic.getConfluenceUrl())) + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"status\">Status (required)</label>");
            out.println("            <select class=\"aira-select\" id=\"status\" name=\"status\" required>");
            out.println("              <option value=\"ACTIVE\"" + selectedStatus(topic, EsTopic.EsTopicStatus.ACTIVE)
                    + ">ACTIVE</option>");
            out.println("              <option value=\"RETIRED\"" + selectedStatus(topic, EsTopic.EsTopicStatus.RETIRED)
                    + ">RETIRED</option>");
            out.println(
                    "              <option value=\"ARCHIVED\"" + selectedStatus(topic, EsTopic.EsTopicStatus.ARCHIVED)
                            + ">ARCHIVED</option>");
            out.println("            </select>");
            out.println("          </div>");

            out.println("          <h3 class=\"aira-subsection-title\">Meeting Configuration</h3>");
            out.println(
                    "          <label class=\"aira-radio\"><input type=\"checkbox\" name=\"meetingEnabled\""
                            + (meetingEnabled ? " checked" : "") + " /> Enable Meeting Support</label>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"meetingName\">Meeting Name (required when enabled)</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"meetingName\" name=\"meetingName\" type=\"text\" value=\""
                            + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingName())) + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"meetingDescription\">Meeting Description</label>");
            out.println(
                    "            <textarea class=\"aira-textarea\" id=\"meetingDescription\" name=\"meetingDescription\" rows=\"4\">"
                            + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingDescription()))
                            + "</textarea>");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"onlineMeetingUrl\">Meeting URL (e.g. Zoom link)</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"onlineMeetingUrl\" name=\"onlineMeetingUrl\" type=\"text\" value=\""
                            + escapeHtml(orEmpty(meeting == null ? null : meeting.getOnlineMeetingUrl())) + "\" />");
            out.println("          </div>");

            out.println("          <div class=\"aira-field\">");
            out.println(
                    "            <label for=\"onlineMeetingDetails\">Connection Details (dial-in info, passcode, etc.)</label>");
            out.println(
                    "            <textarea class=\"aira-textarea\" id=\"onlineMeetingDetails\" name=\"onlineMeetingDetails\" rows=\"5\">"
                            + escapeHtml(orEmpty(meeting == null ? null : meeting.getOnlineMeetingDetails()))
                            + "</textarea>");
            out.println("          </div>");

            out.println(
                    "          <label class=\"aira-radio\"><input type=\"checkbox\" name=\"meetingRequiresApproval\""
                            + (meeting != null && Boolean.TRUE.equals(meeting.getJoinRequiresApproval()) ? " checked"
                                    : "")
                            + " /> Join Requires Approval</label>");

            out.println("          <div class=\"aira-action-group\">");
            out.println("            <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
            out.println("          </div>");
            out.println("        </form>");
            out.println("        <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/es/topic/"
                    + topic.getEsTopicId() + "\">Cancel and return to View Topic Page</a></p>");
            out.println("      </section>");
            out.println("    </div>");
            page.writeEnd(out);
        }
    }

    private String selectedStatus(EsTopic topic, EsTopic.EsTopicStatus status) {
        return topic.getStatus() == status ? " selected" : "";
    }

    private Long parseTopicId(String pathInfo) {
        if (pathInfo == null || pathInfo.length() <= 1) {
            return null;
        }
        String segment = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        return parseId(segment);
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

    private void validateTopicMoveMeetingAssignments(Long topicId, EsTopicSpace targetSpace) {
        List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByTopicId(topicId);
        if (agendaItems.isEmpty()) {
            return;
        }
        Set<String> invalidMeetingRefs = new LinkedHashSet<>();
        for (EsMeetingAgendaItem agendaItem : agendaItems) {
            EsMeeting meeting = esMeetingDao.findById(agendaItem.getEsMeetingId()).orElse(null);
            if (meeting == null) {
                continue;
            }
            EsTopicSpace hostSpace = topicSpaceDao.findById(meeting.getEsTopicSpaceId()).orElse(null);
            if (!isTopicSpaceAllowedForMeetingHost(targetSpace, hostSpace)) {
                invalidMeetingRefs.add("#" + meeting.getEsMeetingId() + " (" + orEmpty(meeting.getMeetingName())
                        + ")");
            }
        }
        if (!invalidMeetingRefs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot move topic to the selected Topic Space because existing meeting agenda assignments would "
                            + "be invalid. Remove the topic from these meetings first: "
                            + String.join(", ", invalidMeetingRefs));
        }
    }

    private boolean isTopicSpaceAllowedForMeetingHost(EsTopicSpace topicSpace, EsTopicSpace meetingHostSpace) {
        if (topicSpace == null || meetingHostSpace == null) {
            return false;
        }
        EsTopicSpace.Visibility hostVisibility = meetingHostSpace.getVisibility();
        EsTopicSpace.Visibility topicVisibility = topicSpace.getVisibility();
        if (hostVisibility == EsTopicSpace.Visibility.PRIVATE) {
            return meetingHostSpace.getEsTopicSpaceId() != null
                    && meetingHostSpace.getEsTopicSpaceId().equals(topicSpace.getEsTopicSpaceId());
        }
        return topicVisibility == EsTopicSpace.Visibility.PUBLIC;
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
