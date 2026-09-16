package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsMeetingRsvpDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingAuthorizationService;
import org.airahub.interophub.service.MeetingExpectedPeopleService;
import org.airahub.interophub.service.MeetingExpectedPeopleService.ExpectedPerson;

/**
 * JSON people-search endpoint backing the attendance console's "too many to
 * list" search-to-reveal widget (docs/meeting-attendance-console-design.md,
 * Phase 5 redesign). Deliberately searches every registered InteropHub user,
 * not just this meeting's expected/interested pool - a walk-in who wasn't a
 * series member or topic follower still needs to be checked in - but flags
 * matches that are expected so the curated signal isn't lost.
 *
 * Mapped to /es/meeting-attendance/search-people.
 */
public class EsMeetingAttendanceSearchServlet extends HttpServlet {

    private static final int RESULT_LIMIT = 20;

    private final AuthFlowService authFlowService;
    private final MeetingAuthorizationService meetingAuthorizationService;
    private final EsMeetingDao meetingDao;
    private final EsMeetingAttendanceDao attendanceDao;
    private final EsMeetingRsvpDao rsvpDao;
    private final MeetingExpectedPeopleService expectedPeopleService;
    private final UserDao userDao;

    public EsMeetingAttendanceSearchServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingAuthorizationService = new MeetingAuthorizationService();
        this.meetingDao = new EsMeetingDao();
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.rsvpDao = new EsMeetingRsvpDao();
        this.expectedPeopleService = new MeetingExpectedPeopleService();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        Long meetingId = parseId(request.getParameter("meetingId"));
        Long topicId = parseId(request.getParameter("topicId"));
        String query = trimToNull(request.getParameter("q"));

        EsMeeting meeting = meetingId != null ? meetingDao.findById(meetingId).orElse(null) : null;
        Optional<User> viewerOpt = authFlowService.findAuthenticatedUser(request);
        if (meeting == null || viewerOpt.isEmpty()
                || !meetingAuthorizationService.canControlMeeting(viewerOpt.get().getUserId(), meeting)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"ok\":false}");
            }
            return;
        }

        if (query == null || query.isBlank()) {
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"ok\":true,\"results\":[]}");
            }
            return;
        }

        List<EsMeetingAttendance> active = attendanceDao.findByEsMeetingId(meetingId);
        Set<Long> checkedInUserIds = new HashSet<>();
        Set<String> checkedInEmails = new HashSet<>();
        for (EsMeetingAttendance a : active) {
            if (a.getUserId() != null) {
                checkedInUserIds.add(a.getUserId());
            }
            if (a.getEmailNormalized() != null) {
                checkedInEmails.add(a.getEmailNormalized());
            }
        }

        List<ExpectedPerson> expected = topicId != null
                ? expectedPeopleService.findExpectedForTopic(meetingId, topicId)
                : expectedPeopleService.findExpectedMeetingWide(meeting, rsvpDao.findByMeetingId(meetingId));
        Set<Long> expectedUserIds = new HashSet<>();
        Set<String> expectedEmails = new HashSet<>();
        for (ExpectedPerson p : expected) {
            if (p.userId() != null) {
                expectedUserIds.add(p.userId());
            }
            if (p.emailNormalized() != null) {
                expectedEmails.add(p.emailNormalized());
            }
        }

        List<User> matches = userDao.searchUsers(query);
        int limit = Math.min(matches.size(), RESULT_LIMIT);

        try (PrintWriter out = response.getWriter()) {
            out.print("{\"ok\":true,\"results\":[");
            for (int i = 0; i < limit; i++) {
                User u = matches.get(i);
                if (i > 0) {
                    out.print(',');
                }
                boolean checkedIn = checkedInUserIds.contains(u.getUserId())
                        || (u.getEmailNormalized() != null && checkedInEmails.contains(u.getEmailNormalized()));
                boolean isExpected = expectedUserIds.contains(u.getUserId())
                        || (u.getEmailNormalized() != null && expectedEmails.contains(u.getEmailNormalized()));
                writeUser(out, u, checkedIn, isExpected);
            }
            out.print("]}");
        }
    }

    private void writeUser(PrintWriter out, User u, boolean checkedIn, boolean expected) {
        String name = ((orEmpty(u.getFirstName())) + " " + orEmpty(u.getLastName())).trim();
        out.print("{\"userId\":");
        out.print(u.getUserId());
        out.print(",\"displayName\":\"");
        out.print(escapeJson(name.isEmpty() ? orEmpty(u.getEmail()) : name));
        out.print("\",\"firstName\":\"");
        out.print(escapeJson(orEmpty(u.getFirstName())));
        out.print("\",\"lastName\":\"");
        out.print(escapeJson(orEmpty(u.getLastName())));
        out.print("\",\"organization\":\"");
        out.print(escapeJson(orEmpty(u.getOrganization())));
        out.print("\",\"email\":\"");
        out.print(escapeJson(orEmpty(u.getEmail())));
        out.print("\",\"expected\":");
        out.print(expected);
        out.print(",\"checkedIn\":");
        out.print(checkedIn);
        out.print("}");
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
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

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
