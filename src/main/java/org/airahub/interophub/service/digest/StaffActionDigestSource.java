package org.airahub.interophub.service.digest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.dao.EsMeetingActionStateDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingActionState;
import org.airahub.interophub.model.MeetingAction;
import org.airahub.interophub.model.MeetingActionType;
import org.airahub.interophub.service.HubLinkService;
import org.airahub.interophub.service.MeetingActionQueueService;
import org.airahub.interophub.service.MeetingResponsibilityResolver;

/**
 * Digest source: each meeting's shared, currently due-or-overdue actions
 * (docs/interophub-meeting-cadence-design.md's "daily staff reminder
 * digest"), sent to the tiered set of people specifically responsible for
 * that meeting - see {@link MeetingResponsibilityResolver}.
 *
 * <p>Deliberately NOT the dashboard's eligibility rule
 * (canControlMeeting, which always includes every site admin unconditionally):
 * a daily broadcast of the entire org's backlog to every admin account reads
 * as spam, not a targeted reminder. Site admins only hear about a meeting
 * here when nobody more specific (chair, scribe, cochair, presenter, topic
 * champion, or Topic-Space admin) is already watching it. The in-app queue
 * keeps using the broader rule unchanged - an admin should still be able to
 * open and act on anything from the dashboard.
 *
 * <p>Also ignores the {@code since} delta window that other {@link DigestItemSource}
 * implementations rely on: an action's due-ness is a currently-true condition,
 * not a point-in-time event, so this always snapshots what's due-or-overdue
 * as of {@code until} via {@link MeetingActionQueueService#getAllDueOrOverdueActions}.
 */
public class StaffActionDigestSource implements DigestItemSource {

    public static final String SECTION_TITLE = "Action Needed";

    private final MeetingActionQueueService meetingActionQueueService;
    private final MeetingResponsibilityResolver responsibilityResolver;
    private final EsMeetingActionStateDao actionStateDao;
    private final HubLinkService hubLinkService;

    public StaffActionDigestSource() {
        this.meetingActionQueueService = new MeetingActionQueueService();
        this.responsibilityResolver = new MeetingResponsibilityResolver();
        this.actionStateDao = new EsMeetingActionStateDao();
        this.hubLinkService = new HubLinkService();
    }

    @Override
    public String key() {
        return "STAFF_ACTIONS";
    }

    @Override
    public boolean respectsCommunityUnsubscribe() {
        return false;
    }

    @Override
    public List<DigestNotice> collect(LocalDateTime since, LocalDateTime until) {
        Instant asOf = until.toInstant(ZoneOffset.UTC);

        Map<String, List<MeetingAction>> actionsByRecipientEmailNormalized = new LinkedHashMap<>();
        Map<String, String> recipientEmailByNormalized = new LinkedHashMap<>();
        Map<String, Long> recipientUserIdByNormalized = new LinkedHashMap<>();

        for (MeetingActionQueueService.MeetingWithAction pair : meetingActionQueueService
                .getAllDueOrOverdueActions(asOf)) {
            EsMeeting meeting = pair.meeting();
            MeetingAction action = pair.action();
            for (MeetingResponsibilityResolver.Responsible responsible : resolveResponsible(meeting, action)) {
                if (isSnoozed(responsible.userId(), meeting.getEsMeetingId(), action, until)) {
                    continue;
                }
                actionsByRecipientEmailNormalized
                        .computeIfAbsent(responsible.emailNormalized(), k -> new ArrayList<>())
                        .add(action);
                recipientEmailByNormalized.put(responsible.emailNormalized(), responsible.email());
                recipientUserIdByNormalized.put(responsible.emailNormalized(), responsible.userId());
            }
        }

        List<DigestNotice> notices = new ArrayList<>();
        for (Map.Entry<String, List<MeetingAction>> entry : actionsByRecipientEmailNormalized.entrySet()) {
            String emailNormalized = entry.getKey();
            notices.add(new DigestNotice(recipientEmailByNormalized.get(emailNormalized), emailNormalized,
                    recipientUserIdByNormalized.get(emailNormalized), SECTION_TITLE, buildBody(entry.getValue())));
        }
        return notices;
    }

    private List<MeetingResponsibilityResolver.Responsible> resolveResponsible(EsMeeting meeting, MeetingAction action) {
        if (action.getActionType() == MeetingActionType.PUBLISH_NOTES) {
            return responsibilityResolver.resolveForNotesPublishing(meeting);
        }
        if (action.getActionType() == MeetingActionType.PUBLISH_PROPOSED_AGENDA
                || action.getActionType() == MeetingActionType.FINALIZE_AGENDA) {
            return responsibilityResolver.resolveForAgendaOwnership(meeting);
        }
        return responsibilityResolver.resolveForMeeting(meeting);
    }

    private boolean isSnoozed(Long userId, Long esMeetingId, MeetingAction action, LocalDateTime now) {
        EsMeetingActionState state = actionStateDao.findOne(userId, esMeetingId, action.getActionType()).orElse(null);
        return state != null && state.getSnoozedUntil() != null && state.getSnoozedUntil().isAfter(now);
    }

    private String buildBody(List<MeetingAction> actions) {
        StringBuilder body = new StringBuilder();
        for (MeetingAction action : actions) {
            body.append("  - ").append(action.getInstruction())
                    .append(" (").append(action.getMeetingName()).append(")\n")
                    .append("    ").append(hubLinkService.buildLink(action.getHref())).append("\n");
        }
        return body.toString();
    }
}
