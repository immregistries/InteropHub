package org.airahub.interophub.model;

/**
 * Result of checking whether a communication is eligible to be scheduled or
 * sent. In-memory value object returned by
 * MeetingCommunicationEligibilityService.
 */
public class CommunicationEligibilityResult {

    private final boolean eligible;
    private final String reason;

    private CommunicationEligibilityResult(boolean eligible, String reason) {
        this.eligible = eligible;
        this.reason = reason;
    }

    public static CommunicationEligibilityResult ok() {
        return new CommunicationEligibilityResult(true, null);
    }

    public static CommunicationEligibilityResult blocked(String reason) {
        return new CommunicationEligibilityResult(false, reason);
    }

    public boolean isEligible() {
        return eligible;
    }

    /**
     * Returns a human-readable explanation when not eligible; null when eligible.
     */
    public String getReason() {
        return reason;
    }
}
