package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.dao.EsMeetingCommunicationDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.CommunicationEligibilityResult;
import org.airahub.interophub.model.CommunicationPreview;
import org.airahub.interophub.model.CommunicationRecipientGroupSummary;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.RecipientGroup;

/**
 * Generates a preview for a meeting communication — resolving recipients,
 * computing per-group counts, and rendering one sample email per distinct
 * primary group (up to 5 samples).
 */
public class MeetingCommunicationPreviewService {

    private final EsMeetingCommunicationDao communicationDao;
    private final EsMeetingDao meetingDao;
    private final MeetingCommunicationRecipientResolver resolver;
    private final MeetingCommunicationRenderer renderer;
    private final MeetingCommunicationEligibilityService eligibilityService;

    public MeetingCommunicationPreviewService() {
        this.communicationDao = new EsMeetingCommunicationDao();
        this.meetingDao = new EsMeetingDao();
        this.resolver = new MeetingCommunicationRecipientResolver();
        this.renderer = new MeetingCommunicationRenderer();
        this.eligibilityService = new MeetingCommunicationEligibilityService(renderer);
    }

    /**
     * Loads the communication and meeting, resolves recipients, builds group
     * summaries, selects sample emails, and checks eligibility.
     *
     * @throws IllegalArgumentException if communication or meeting is not found
     */
    public CommunicationPreview preview(Long esMeetingCommunicationId) {
        EsMeetingCommunication communication = communicationDao.findById(esMeetingCommunicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Communication not found: " + esMeetingCommunicationId));
        EsMeeting meeting = meetingDao.findById(communication.getEsMeetingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Meeting not found for communication: " + esMeetingCommunicationId));

        List<CommunicationRecipientPreview> recipients = resolver.resolve(communication, meeting);
        List<CommunicationRecipientGroupSummary> summaries = buildGroupSummaries(recipients);
        List<CommunicationRenderedEmail> samples = buildSampleEmails(communication, meeting, recipients);
        CommunicationEligibilityResult eligibility = eligibilityService.check(communication, meeting);

        return new CommunicationPreview(communication, meeting, recipients, summaries, samples, eligibility);
    }

    private List<CommunicationRecipientGroupSummary> buildGroupSummaries(
            List<CommunicationRecipientPreview> recipients) {
        Map<RecipientGroup, Integer> counts = new LinkedHashMap<>();
        for (RecipientGroup g : RecipientGroup.values()) {
            counts.put(g, 0);
        }
        for (CommunicationRecipientPreview r : recipients) {
            counts.merge(r.getPrimaryGroup(), 1, Integer::sum);
        }
        List<CommunicationRecipientGroupSummary> result = new ArrayList<>();
        for (Map.Entry<RecipientGroup, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0) {
                result.add(new CommunicationRecipientGroupSummary(e.getKey(), e.getValue()));
            }
        }
        return result;
    }

    private List<CommunicationRenderedEmail> buildSampleEmails(
            EsMeetingCommunication communication,
            EsMeeting meeting,
            List<CommunicationRecipientPreview> recipients) {
        // One sample per distinct primaryGroup, up to 5 total
        Map<RecipientGroup, CommunicationRenderedEmail> byGroup = new LinkedHashMap<>();
        for (CommunicationRecipientPreview r : recipients) {
            if (!byGroup.containsKey(r.getPrimaryGroup())) {
                byGroup.put(r.getPrimaryGroup(), renderer.render(communication, meeting, r));
            }
            if (byGroup.size() >= 5) {
                break;
            }
        }
        return new ArrayList<>(byGroup.values());
    }
}
