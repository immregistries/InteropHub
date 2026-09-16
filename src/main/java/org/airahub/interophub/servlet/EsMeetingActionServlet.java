package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.airahub.interophub.dao.EsMeetingActionStateDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.MeetingActionType;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingAuthorizationService;

/**
 * Records personal read/snooze state on one derived meeting-cadence action
 * (see MeetingActionQueueService). The action itself - whether it applies,
 * its due date, and its completion - is never stored here; this servlet only
 * ever records what the signed-in user has already seen or chosen to hide.
 * Snoozing (or reading) never affects the shared obligation for anyone else.
 */
public class EsMeetingActionServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsMeetingDao meetingDao;
    private final EsMeetingActionStateDao actionStateDao;
    private final MeetingAuthorizationService authorizationService;

    public EsMeetingActionServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingDao = new EsMeetingDao();
        this.actionStateDao = new EsMeetingActionStateDao();
        this.authorizationService = new MeetingAuthorizationService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String contextPath = request.getContextPath();

        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        if (userOpt.isEmpty()) {
            response.sendRedirect(contextPath + "/home");
            return;
        }
        Long userId = userOpt.get().getUserId();

        Long meetingId = parseId(request.getParameter("meetingId"));
        MeetingActionType actionType = parseActionType(request.getParameter("actionType"));
        String op = trimToNull(request.getParameter("op"));
        if (meetingId == null || actionType == null || op == null) {
            response.sendRedirect(contextPath + "/welcome");
            return;
        }

        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null || !authorizationService.canControlMeeting(userId, meeting)) {
            // Not eligible to act on this meeting, so not eligible to hold personal
            // state about it either - silently redirect rather than leaking existence.
            response.sendRedirect(contextPath + "/welcome");
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        switch (op) {
            case "read" -> actionStateDao.markRead(userId, meetingId, actionType, now);
            case "snoozeTomorrow" -> actionStateDao.snoozeUntil(userId, meetingId, actionType, now.plusDays(1));
            case "snoozeNextWeek" -> actionStateDao.snoozeUntil(userId, meetingId, actionType, now.plusDays(7));
            case "unsnooze" -> actionStateDao.snoozeUntil(userId, meetingId, actionType, null);
            default -> { }
        }

        response.sendRedirect(contextPath + "/welcome#meeting-action-queue");
    }

    private Long parseId(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private MeetingActionType parseActionType(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return MeetingActionType.valueOf(trimmed);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
