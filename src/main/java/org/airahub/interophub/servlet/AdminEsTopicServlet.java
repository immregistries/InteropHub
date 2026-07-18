package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsTopicServlet extends HttpServlet {

    private static final DateTimeFormatter COMMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsTopicMeetingDao esTopicMeetingDao;
    private final EsCommentDao esCommentDao;
    private final EsSubscriptionDao esSubscriptionDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsNeighborhoodDao esNeighborhoodDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsMeetingDao esMeetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final UserDao userDao;

    public AdminEsTopicServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.esTopicMeetingDao = new EsTopicMeetingDao();
        this.esCommentDao = new EsCommentDao();
        this.esSubscriptionDao = new EsSubscriptionDao();
        this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
        this.esNeighborhoodDao = new EsNeighborhoodDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.esMeetingDao = new EsMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String mode = trimToNull(request.getParameter("mode"));
        String topicIdRaw = trimToNull(request.getParameter("esTopicId"));

        if (topicIdRaw != null) {
            Long topicId = parseId(topicIdRaw);
            if (topicId == null) {
                renderList(response, contextPath, "Invalid topic identifier.");
                return;
            }

            EsTopic topic = esTopicDao.findById(topicId).orElse(null);
            if (topic == null) {
                renderList(response, contextPath, "Topic entry was not found.");
                return;
            }

            EsTopicMeeting meeting = esTopicMeetingDao.findByTopicId(topicId).orElse(null);
            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(response, contextPath, topic, meeting, null, false, null);
                return;
            }

            List<EsSubscription> subscriptions = esSubscriptionDao.findActiveByTopicId(topicId);
            renderDetails(response, contextPath, topic, meeting, esCommentDao.findByTopicId(topicId), subscriptions);
            return;
        }

        if ("new".equalsIgnoreCase(mode)) {
            EsTopic blank = new EsTopic();
            blank.setPriorityIis(0);
            blank.setPriorityEhr(0);
            blank.setPriorityCdc(0);
            blank.setStatus(EsTopic.EsTopicStatus.ACTIVE);
            renderEditForm(response, contextPath, blank, null, null, true, null);
            return;
        }

        String message = request.getParameter("saved") != null ? "Topic settings saved." : null;
        renderList(response, contextPath, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String topicIdRaw = trimToNull(request.getParameter("esTopicId"));
        String topicSpaceIdRaw = trimToNull(request.getParameter("esTopicSpaceId"));

        String topicName = trimToNull(request.getParameter("topicName"));
        String description = trimToNull(request.getParameter("description"));
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

        if (topicIdRaw == null) {
            // CREATE path
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

                response.sendRedirect(contextPath + "/admin/es/topics?esTopicId=" + saved.getEsTopicId());
            } catch (Exception ex) {
                newTopic.setTopicCode(topicCodeParam);
                newTopic.setTopicName(topicName);
                newTopic.setDescription(description);
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
                renderEditForm(response, contextPath, newTopic, meetingEnabled ? newMeeting : null, ex.getMessage(),
                        true, selectedNeighborhoodIds);
            }
            return;
        }

        // EDIT path
        Long topicId = parseId(topicIdRaw);
        if (topicId == null) {
            renderList(response, contextPath, "Invalid topic identifier.");
            return;
        }

        EsTopic topic = esTopicDao.findById(topicId).orElse(null);
        if (topic == null) {
            renderList(response, contextPath, "Topic entry was not found.");
            return;
        }

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
                esTopicMeetingDao.disableMeeting(meeting, adminUser.get().getUserId());
            }

            response.sendRedirect(contextPath + "/admin/es/topics?saved=1");
        } catch (Exception ex) {
            topic.setTopicName(topicName);
            topic.setDescription(description);
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

            renderEditForm(response, contextPath, topic, meetingEnabled ? meeting : null, ex.getMessage(), false,
                    selectedNeighborhoodIds);
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }

        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }

        return authenticatedUser;
    }

    private void renderList(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopic> topics = esTopicDao.findAllOrderByTopicName();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "ES Topics Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>ES Topics</h2>");
                panelOut.println("        <p>View and manage Emerging Standards topics.</p>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Topic Name</th>");
                panelOut.println("              <th>Stage</th>");
                panelOut.println("              <th>Status</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");
                for (EsTopic topic : topics) {
                    panelOut.println("            <tr>");
                    panelOut.println(
                            "              <td><a href=\"" + contextPath + "/admin/es/topics?esTopicId="
                                    + topic.getEsTopicId()
                                    + "\">" + escapeHtml(orEmpty(topic.getTopicName())) + "</a></td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(topic.getStage())) + "</td>");
                    panelOut.println(
                            "              <td>" + escapeHtml(topic.getStatus() == null ? "" : topic.getStatus().name())
                                    + "</td>");
                    panelOut.println("            </tr>");
                }
                if (topics.isEmpty()) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td colspan=\"3\">No ES topics found.</td>");
                    panelOut.println("            </tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es/topics?mode=new\">Add New Topic</a></p>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderDetails(HttpServletResponse response, String contextPath, EsTopic topic, EsTopicMeeting meeting,
            List<EsComment> comments, List<EsSubscription> subscriptions)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        boolean meetingEnabled = meeting != null && meeting.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE;
        Map<Long, User> usersById = loadUsersById(comments);
        Map<Long, User> subUsersById = loadUsersByIdFromSubscriptions(subscriptions);
        String neighborhoodsDisplay = String.join(", ",
                topicNeighborhoodDao.findNeighborhoodNamesByTopicId(topic.getEsTopicId()));

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "ES Topic Details - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>ES Topic Details</h2>");
                panelOut.println("        <p>This view is read-only. Use Edit to change this topic.</p>");

                panelOut.println("        <section>");
                panelOut.println("          <p><strong>Topic Code:</strong> "
                        + escapeHtml(orEmpty(topic.getTopicCode())) + "</p>");
                panelOut.println("          <p><strong>Topic Name:</strong> "
                        + escapeHtml(orEmpty(topic.getTopicName())) + "</p>");
                panelOut.println(
                        "          <p><strong>Description:</strong> " + escapeHtml(orEmpty(topic.getDescription()))
                                + "</p>");
                panelOut.println(
                        "          <p><strong>Neighborhood:</strong> " + escapeHtml(orEmpty(neighborhoodsDisplay))
                                + "</p>");
                panelOut.println("          <p><strong>Priority IIS:</strong> "
                        + escapeHtml(String.valueOf(topic.getPriorityIis()))
                        + "</p>");
                panelOut.println("          <p><strong>Priority EHR:</strong> "
                        + escapeHtml(String.valueOf(topic.getPriorityEhr()))
                        + "</p>");
                panelOut.println("          <p><strong>Priority CDC:</strong> "
                        + escapeHtml(String.valueOf(topic.getPriorityCdc()))
                        + "</p>");
                panelOut.println(
                        "          <p><strong>Stage:</strong> " + escapeHtml(orEmpty(topic.getStage())) + "</p>");
                panelOut.println(
                        "          <p><strong>Policy Status:</strong> " + escapeHtml(orEmpty(topic.getPolicyStatus()))
                                + "</p>");
                panelOut.println("          <p><strong>Topic Type:</strong> "
                        + escapeHtml(orEmpty(topic.getTopicType())) + "</p>");
                if (trimToNull(topic.getConfluenceUrl()) == null) {
                    panelOut.println("          <p><strong>Confluence URL:</strong> </p>");
                } else {
                    panelOut.println("          <p><strong>Confluence URL:</strong> <a href=\""
                            + escapeHtml(topic.getConfluenceUrl()) + "\" target=\"_blank\" rel=\"noopener\">"
                            + escapeHtml(topic.getConfluenceUrl()) + "</a></p>");
                }
                panelOut.println("          <p><strong>Status:</strong> "
                        + escapeHtml(topic.getStatus() == null ? "" : topic.getStatus().name()) + "</p>");
                panelOut.println("          <p><strong>Created By User ID:</strong> "
                        + escapeHtml(
                                topic.getCreatedByUserId() == null ? "" : String.valueOf(topic.getCreatedByUserId()))
                        + "</p>");
                panelOut.println(
                        "          <p><strong>Meeting Enabled:</strong> " + (meetingEnabled ? "Yes" : "No") + "</p>");
                if (meeting != null) {
                    panelOut.println("          <p><strong>Meeting Name:</strong> "
                            + escapeHtml(orEmpty(meeting.getMeetingName()))
                            + "</p>");
                    panelOut.println("          <p><strong>Meeting Description:</strong> "
                            + escapeHtml(orEmpty(meeting.getMeetingDescription())) + "</p>");
                    panelOut.println("          <p><strong>Join Requires Approval:</strong> "
                            + (Boolean.TRUE.equals(meeting.getJoinRequiresApproval()) ? "Yes" : "No") + "</p>");
                    panelOut.println("          <p><strong>Meeting Status:</strong> "
                            + escapeHtml(meeting.getStatus() == null ? "" : meeting.getStatus().name()) + "</p>");
                }
                panelOut.println("        </section>");

                panelOut.println("        <section>");
                panelOut.println("          <h3>Following (" + subscriptions.size() + ")</h3>");
                if (subscriptions.isEmpty()) {
                    panelOut.println("          <p>No subscriptions.</p>");
                } else {
                    panelOut.println("          <table class=\"data-table\">");
                    panelOut.println("            <thead>");
                    panelOut.println("              <tr>");
                    panelOut.println("                <th>Email</th>");
                    panelOut.println("                <th>Display Name</th>");
                    panelOut.println("                <th>Organization</th>");
                    panelOut.println("                <th>Role</th>");
                    panelOut.println("              </tr>");
                    panelOut.println("            </thead>");
                    panelOut.println("            <tbody>");
                    for (EsSubscription sub : subscriptions) {
                        User subUser = sub.getUserId() != null ? subUsersById.get(sub.getUserId()) : null;
                        panelOut.println("              <tr>");
                        panelOut.println("                <td><a href=\"" + contextPath
                                + "/admin/es/subscription?subscriptionId=" + sub.getEsSubscriptionId()
                                + "&amp;topicId=" + topic.getEsTopicId() + "\">"
                                + escapeHtml(orEmpty(sub.getEmail())) + "</a></td>");
                        panelOut.println("                <td>"
                                + escapeHtml(subUser == null ? "" : orEmpty(subUser.getFullName())) + "</td>");
                        panelOut.println("                <td>"
                                + escapeHtml(subUser == null ? "" : orEmpty(subUser.getOrganization())) + "</td>");
                        String roleLabel = switch (sub.getStatus()) {
                            case CHAMPION -> "Champion";
                            case SUPPORT -> "Support";
                            default -> "Subscribed";
                        };
                        panelOut.println("                <td>" + escapeHtml(roleLabel) + "</td>");
                        panelOut.println("              </tr>");
                    }
                    panelOut.println("            </tbody>");
                    panelOut.println("          </table>");
                }
                panelOut.println("        </section>");

                panelOut.println("        <section>");
                panelOut.println("          <h3>Comments</h3>");
                if (comments.isEmpty()) {
                    panelOut.println("          <p>No comments.</p>");
                } else {
                    panelOut.println("          <table class=\"data-table\">");
                    panelOut.println("            <thead>");
                    panelOut.println("              <tr>");
                    panelOut.println("                <th>Date</th>");
                    panelOut.println("                <th>Commented By</th>");
                    panelOut.println("                <th>Comment</th>");
                    panelOut.println("              </tr>");
                    panelOut.println("            </thead>");
                    panelOut.println("            <tbody>");
                    for (EsComment comment : comments) {
                        panelOut.println("              <tr>");
                        panelOut.println("                <td>" + escapeHtml(formatCommentDate(comment.getCreatedAt()))
                                + "</td>");
                        panelOut.println("                <td>" + escapeHtml(resolveCommentAuthor(comment, usersById))
                                + "</td>");
                        panelOut.println("                <td>" + escapeHtml(orEmpty(comment.getCommentText()))
                                + "</td>");
                        panelOut.println("              </tr>");
                    }
                    panelOut.println("            </tbody>");
                    panelOut.println("          </table>");
                }
                panelOut.println("        </section>");

                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es/topics?esTopicId=" + topic.getEsTopicId()
                                + "&mode=edit\">Edit Topic</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/es/topic/" + topic.getEsTopicId()
                        + "\">View Public Page</a></p>");
                if (meeting != null && meeting.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE) {
                    panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/meetings?meetingId="
                            + meeting.getEsTopicMeetingId() + "\">Manage Meeting Members</a></p>");
                }
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/topics\">Back to Topics</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath, EsTopic topic, EsTopicMeeting meeting,
            String errorMessage, boolean isNew, Set<Long> selectedNeighborhoodIdsOverride) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
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
        Set<Long> selectedNeighborhoodIds = selectedNeighborhoodIdsOverride;
        if (selectedNeighborhoodIds == null) {
            selectedNeighborhoodIds = topic.getEsTopicId() == null
                    ? Set.of()
                    : topicNeighborhoodDao.findNeighborhoodIdsByTopicId(topic.getEsTopicId());
        }
        final Set<Long> selectedNeighborhoodIdsFinal = selectedNeighborhoodIds;

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out,
                    isNew ? "Add New ES Topic - InteropHub" : "Edit ES Topic - InteropHub",
                    contextPath, panelOut -> {
                        panelOut.println("      <section class=\"panel\">");
                        panelOut.println("        <h2>" + (isNew ? "Add New ES Topic" : "Edit ES Topic") + "</h2>");

                        if (errorMessage != null && !errorMessage.isBlank()) {
                            panelOut.println(
                                    "        <p><strong>Could not save:</strong> " + escapeHtml(errorMessage) + "</p>");
                        }

                        panelOut.println(
                                "        <form class=\"login-form\" action=\"" + contextPath
                                        + "/admin/es/topics\" method=\"post\">");
                        if (isNew) {
                            out.println("      <label for=\"topicCode\">Topic Code (required)</label>");
                            out.println(
                                    "      <input id=\"topicCode\" name=\"topicCode\" type=\"text\" required maxlength=\"80\" value=\""
                                            + escapeHtml(orEmpty(topic.getTopicCode())) + "\" />");
                        } else {
                            out.println(
                                    "      <input type=\"hidden\" name=\"esTopicId\" value=\"" + topic.getEsTopicId()
                                            + "\" />");
                            out.println(
                                    "      <p><strong>Topic Code:</strong> " + escapeHtml(orEmpty(topic.getTopicCode()))
                                            + "</p>");
                        }

                        out.println("      <label for=\"topicName\">Topic Name (required)</label>");
                        out.println("      <input id=\"topicName\" name=\"topicName\" type=\"text\" required value=\""
                                + escapeHtml(orEmpty(topic.getTopicName())) + "\" />");

                        out.println("      <label for=\"description\">Description</label>");
                        out.println("      <textarea id=\"description\" name=\"description\" rows=\"5\">"
                                + escapeHtml(orEmpty(topic.getDescription())) + "</textarea>");

                        out.println("      <label for=\"esTopicSpaceId\">Topic Space (required)</label>");
                        out.println("      <select id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
                        out.println("        <option value=\"\">\u2014 Select \u2014</option>");
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
                            out.println("        <option value=\"" + topicSpace.getEsTopicSpaceId() + "\"" + optionFlags
                                    + ">"
                                    + escapeHtml(orEmpty(topicSpace.getSpaceName()))
                                    + (active ? "" : " (inactive)")
                                    + "</option>");
                        }
                        out.println("      </select>");

                        out.println("      <fieldset style=\"margin-bottom: 1em;\">");
                        out.println("        <legend>Neighborhood(s)</legend>");
                        if (allNeighborhoods.isEmpty()) {
                            out.println("        <p><em>No neighborhoods defined.</em></p>");
                            out.println("        <p><a href=\"" + contextPath
                                    + "/admin/es/neighborhoods?mode=new\">Create a neighborhood</a> and then return to this topic.</p>");
                        } else {
                            for (EsNeighborhood nh : allNeighborhoods) {
                                Long nhId = nh.getEsNeighborhoodId();
                                boolean nhChecked = nhId != null && selectedNeighborhoodIdsFinal.contains(nhId);
                                out.println("        <label class=\"js-neighborhood-option\" data-space-id=\""
                                        + escapeHtml(String.valueOf(nh.getEsTopicSpaceId()))
                                        + "\"><input type=\"checkbox\" name=\"esNeighborhoodId\" value=\""
                                        + escapeHtml(String.valueOf(nhId)) + "\"" + (nhChecked ? " checked" : "")
                                        + " /> "
                                        + escapeHtml(orEmpty(nh.getNeighborhoodName())) + "</label>");
                            }
                            out.println("        <script>");
                            out.println("          (function(){");
                            out.println("            var select = document.getElementById('esTopicSpaceId');");
                            out.println("            if (!select) { return; }");
                            out.println(
                                    "            var options = Array.prototype.slice.call(document.querySelectorAll('.js-neighborhood-option')); ");
                            out.println("            function applyNeighborhoodFilter(){");
                            out.println("              var selected = (select.value || '').trim();");
                            out.println("              options.forEach(function(label){");
                            out.println(
                                    "                var matches = selected && (label.getAttribute('data-space-id') === selected);");
                            out.println("                label.style.display = matches ? '' : 'none';");
                            out.println("                if (!matches) {");
                            out.println(
                                    "                  var checkbox = label.querySelector('input[type=checkbox]');");
                            out.println("                  if (checkbox) { checkbox.checked = false; }");
                            out.println("                }");
                            out.println("              });");
                            out.println("            }");
                            out.println("            select.addEventListener('change', applyNeighborhoodFilter);");
                            out.println("            applyNeighborhoodFilter();");
                            out.println("          })();");
                            out.println("        </script>");
                        }
                        out.println("        <p style=\"margin-top: 0.75em;\"><a href=\"" + contextPath
                                + "/admin/es/neighborhoods\">Manage neighborhoods</a></p>");
                        out.println("      </fieldset>");

                        out.println("      <label for=\"priorityIis\">Priority IIS (required)</label>");
                        out.println(
                                "      <input id=\"priorityIis\" name=\"priorityIis\" type=\"number\" required value=\""
                                        + escapeHtml(String
                                                .valueOf(topic.getPriorityIis() == null ? 0 : topic.getPriorityIis()))
                                        + "\" />");

                        out.println("      <label for=\"priorityEhr\">Priority EHR (required)</label>");
                        out.println(
                                "      <input id=\"priorityEhr\" name=\"priorityEhr\" type=\"number\" required value=\""
                                        + escapeHtml(String
                                                .valueOf(topic.getPriorityEhr() == null ? 0 : topic.getPriorityEhr()))
                                        + "\" />");

                        out.println("      <label for=\"priorityCdc\">Priority CDC (required)</label>");
                        out.println(
                                "      <input id=\"priorityCdc\" name=\"priorityCdc\" type=\"number\" required value=\""
                                        + escapeHtml(String
                                                .valueOf(topic.getPriorityCdc() == null ? 0 : topic.getPriorityCdc()))
                                        + "\" />");

                        out.println("      <label for=\"stage\">Stage</label>");
                        out.println("      <select id=\"stage\" name=\"stage\">");
                        out.println("        <option value=\"\">\u2014 Select \u2014</option>");
                        for (String stageOpt : new String[] { "Start", "Gather", "Draft", "Pilot", "Rollout", "Monitor",
                                "Parked" }) {
                            String sel = stageOpt.equalsIgnoreCase(orEmpty(topic.getStage())) ? " selected" : "";
                            out.println(
                                    "        <option value=\"" + stageOpt + "\"" + sel + ">" + stageOpt + "</option>");
                        }
                        out.println("      </select>");

                        out.println("      <label for=\"policyStatus\">Policy Status</label>");
                        out.println(
                                "      <input id=\"policyStatus\" name=\"policyStatus\" type=\"text\" list=\"policyStatusList\" value=\""
                                        + escapeHtml(orEmpty(topic.getPolicyStatus())) + "\" />");
                        out.println("      <datalist id=\"policyStatusList\">");
                        for (String ps : policyStatuses) {
                            out.println("        <option value=\"" + escapeHtml(ps) + "\" />");
                        }
                        out.println("      </datalist>");

                        out.println("      <label for=\"topicType\">Topic Type</label>");
                        out.println(
                                "      <input id=\"topicType\" name=\"topicType\" type=\"text\" list=\"topicTypeList\" value=\""
                                        + escapeHtml(orEmpty(topic.getTopicType())) + "\" />");
                        out.println("      <datalist id=\"topicTypeList\">");
                        for (String tt : topicTypes) {
                            out.println("        <option value=\"" + escapeHtml(tt) + "\" />");
                        }
                        out.println("      </datalist>");

                        out.println("      <label for=\"confluenceUrl\">Confluence URL</label>");
                        out.println("      <input id=\"confluenceUrl\" name=\"confluenceUrl\" type=\"url\" value=\""
                                + escapeHtml(orEmpty(topic.getConfluenceUrl())) + "\" />");

                        out.println("      <label for=\"status\">Status (required)</label>");
                        out.println("      <select id=\"status\" name=\"status\" required>");
                        out.println(
                                "        <option value=\"ACTIVE\"" + selectedStatus(topic, EsTopic.EsTopicStatus.ACTIVE)
                                        + ">ACTIVE</option>");
                        out.println("        <option value=\"RETIRED\""
                                + selectedStatus(topic, EsTopic.EsTopicStatus.RETIRED)
                                + ">RETIRED</option>");
                        out.println("        <option value=\"ARCHIVED\""
                                + selectedStatus(topic, EsTopic.EsTopicStatus.ARCHIVED)
                                + ">ARCHIVED</option>");
                        out.println("      </select>");

                        out.println("      <h2>Meeting Configuration</h2>");
                        out.println("      <label><input type=\"checkbox\" name=\"meetingEnabled\""
                                + (meetingEnabled ? " checked" : "")
                                + " /> Enable Meeting Support</label>");

                        out.println("      <label for=\"meetingName\">Meeting Name (required when enabled)</label>");
                        out.println("      <input id=\"meetingName\" name=\"meetingName\" type=\"text\" value=\""
                                + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingName())) + "\" />");

                        out.println("      <label for=\"meetingDescription\">Meeting Description</label>");
                        out.println("      <textarea id=\"meetingDescription\" name=\"meetingDescription\" rows=\"4\">"
                                + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingDescription()))
                                + "</textarea>");

                        out.println("      <label for=\"onlineMeetingUrl\">Meeting URL (e.g. Zoom link)</label>");
                        out.println(
                                "      <input id=\"onlineMeetingUrl\" name=\"onlineMeetingUrl\" type=\"text\" value=\""
                                        + escapeHtml(orEmpty(meeting == null ? null : meeting.getOnlineMeetingUrl()))
                                        + "\" />");

                        out.println(
                                "      <label for=\"onlineMeetingDetails\">Connection Details (dial-in info, passcode, etc.)</label>");
                        out.println(
                                "      <textarea id=\"onlineMeetingDetails\" name=\"onlineMeetingDetails\" rows=\"5\">"
                                        + escapeHtml(
                                                orEmpty(meeting == null ? null : meeting.getOnlineMeetingDetails()))
                                        + "</textarea>");

                        out.println("      <label><input type=\"checkbox\" name=\"meetingRequiresApproval\""
                                + (meeting != null && Boolean.TRUE.equals(meeting.getJoinRequiresApproval())
                                        ? " checked"
                                        : "")
                                + " /> Join Requires Approval</label>");

                        panelOut.println("      <button type=\"submit\">Save</button>");
                        panelOut.println("    </form>");
                        panelOut.println(
                                "    <p><a href=\"" + contextPath + "/admin/es/topics\">Back to Topics</a></p>");
                        panelOut.println("      </section>");
                    });
        }
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin to access ES topic settings.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
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

    private Map<Long, User> loadUsersById(List<EsComment> comments) {
        List<Long> userIds = comments.stream()
                .map(EsComment::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userDao.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, User> loadUsersByIdFromSubscriptions(List<EsSubscription> subscriptions) {
        List<Long> userIds = subscriptions.stream()
                .map(EsSubscription::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userDao.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private String resolveCommentAuthor(EsComment comment, Map<Long, User> usersById) {
        if (comment.getUserId() != null) {
            User user = usersById.get(comment.getUserId());
            String displayName = user == null ? null : trimToNull(user.getFullName());
            if (displayName != null) {
                return displayName;
            }

            String commentName = joinNameParts(comment.getFirstName(), comment.getLastName());
            if (commentName != null) {
                return commentName;
            }

            if (user != null && trimToNull(user.getEmail()) != null) {
                return user.getEmail();
            }
        }

        String email = trimToNull(comment.getEmail());
        if (email != null) {
            return email;
        }

        String commentName = joinNameParts(comment.getFirstName(), comment.getLastName());
        return commentName == null ? "" : commentName;
    }

    private String joinNameParts(String firstName, String lastName) {
        String first = trimToNull(firstName);
        String last = trimToNull(lastName);
        if (first == null && last == null) {
            return null;
        }
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private String formatCommentDate(LocalDateTime createdAt) {
        if (createdAt == null) {
            return "";
        }
        return COMMENT_DATE_FORMAT.format(createdAt);
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
