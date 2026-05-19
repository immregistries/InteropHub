package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.EsMeetingCommunicationDao;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsMeetingCommunication.CommunicationStatus;

/**
 * CRUD service for EsMeetingCommunication.
 * Enforces business rules (e.g., minimum scheduling horizon).
 */
public class MeetingCommunicationService {

    /** Minimum lead time from now to scheduledSendAt when scheduling. */
    private static final long MIN_SCHEDULE_MINUTES = 60;

    private final EsMeetingCommunicationDao dao;

    public MeetingCommunicationService() {
        this.dao = new EsMeetingCommunicationDao();
    }

    /**
     * Creates a new DRAFT communication. Sets status to DRAFT.
     * If scheduledSendAt is provided and the caller also wants to schedule
     * immediately, they should call {@link #schedule(Long)} afterwards.
     *
     * @throws IllegalArgumentException if required fields are missing
     */
    public EsMeetingCommunication create(EsMeetingCommunication communication) {
        if (communication.getEsMeetingId() == null) {
            throw new IllegalArgumentException("esMeetingId is required.");
        }
        if (communication.getCommunicationType() == null) {
            throw new IllegalArgumentException("communicationType is required.");
        }
        if (communication.getCreatedByUserId() == null) {
            throw new IllegalArgumentException("createdByUserId is required.");
        }
        communication.setStatus(CommunicationStatus.DRAFT);
        return dao.saveOrUpdate(communication);
    }

    /**
     * Updates an existing DRAFT communication.
     *
     * @throws IllegalArgumentException if the communication is not in DRAFT status
     */
    public EsMeetingCommunication update(EsMeetingCommunication communication) {
        EsMeetingCommunication existing = dao.findById(communication.getEsMeetingCommunicationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Communication not found: " + communication.getEsMeetingCommunicationId()));
        if (existing.getStatus() != CommunicationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT communications can be edited (current status: " + existing.getStatus() + ").");
        }
        return dao.saveOrUpdate(communication);
    }

    /**
     * Transitions a communication from DRAFT to SCHEDULED.
     * Validates that scheduledSendAt is at least 60 minutes from now.
     *
     * @throws IllegalArgumentException if communication not found or already past
     *                                  minimum horizon
     * @throws IllegalStateException    if communication is not in DRAFT status
     */
    public EsMeetingCommunication schedule(Long esMeetingCommunicationId) {
        EsMeetingCommunication communication = dao.findById(esMeetingCommunicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Communication not found: " + esMeetingCommunicationId));
        if (communication.getStatus() != CommunicationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT communications can be scheduled (current status: "
                            + communication.getStatus() + ").");
        }
        if (communication.getScheduledSendAt() == null) {
            throw new IllegalArgumentException("scheduledSendAt is required to schedule a communication.");
        }
        LocalDateTime minimum = LocalDateTime.now().plusMinutes(MIN_SCHEDULE_MINUTES);
        if (communication.getScheduledSendAt().isBefore(minimum)) {
            throw new IllegalArgumentException(
                    "scheduledSendAt must be at least " + MIN_SCHEDULE_MINUTES
                            + " minutes from now.");
        }
        communication.setStatus(CommunicationStatus.SCHEDULED);
        return dao.saveOrUpdate(communication);
    }

    /**
     * Cancels a DRAFT or SCHEDULED communication.
     *
     * @throws IllegalStateException if the communication is in a terminal state
     */
    public EsMeetingCommunication cancel(
            Long esMeetingCommunicationId,
            Long cancelledByUserId,
            String reason) {
        EsMeetingCommunication communication = dao.findById(esMeetingCommunicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Communication not found: " + esMeetingCommunicationId));
        CommunicationStatus status = communication.getStatus();
        if (status == CommunicationStatus.SENT
                || status == CommunicationStatus.SENDING
                || status == CommunicationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel a communication in status: " + status);
        }
        communication.setStatus(CommunicationStatus.CANCELLED);
        communication.setCancelledAt(LocalDateTime.now());
        communication.setCancelledByUserId(cancelledByUserId);
        communication.setCancellationReason(reason);
        return dao.saveOrUpdate(communication);
    }

    public Optional<EsMeetingCommunication> findById(Long id) {
        return dao.findById(id);
    }

    public List<EsMeetingCommunication> findByMeetingId(Long esMeetingId) {
        return dao.findByMeetingId(esMeetingId);
    }

    public List<EsMeetingCommunication> findAllRecent(int limit) {
        return dao.findAllRecent(limit);
    }

    /**
     * Cancels all pending (DRAFT or SCHEDULED) communications for a meeting.
     * Called automatically when a meeting is cancelled.
     */
    public int cancelAllPendingForMeeting(Long esMeetingId, Long cancelledByUserId) {
        return dao.cancelPendingForMeeting(esMeetingId, cancelledByUserId, LocalDateTime.now());
    }
}
