package org.airahub.interophub.service;

/**
 * Checks for {@code POST /api/guide/token}, in contract order. Every {@link Status#INVALID}
 * result uses {@link #INVALID_MESSAGE} so the caller cannot tell the checks apart.
 * A refused {@code Origin} is {@link Status#ORIGIN_FORBIDDEN} (contract section 4, test 1.23),
 * and only after the code checks have passed.
 */
public final class GuideTokenExchange {
    public static final String INVALID_MESSAGE = "Sign-in code is invalid or expired.";

    public enum Status {
        OK,
        INVALID,
        ORIGIN_FORBIDDEN
    }

    public static final class Verdict {
        public final Status status;

        private Verdict(Status status) {
            this.status = status;
        }
    }

    public static final class Facts {
        public boolean found;
        public String appCode;
        public boolean expired;
        public boolean consumed;
        public String storedReturnTo;
        public String storedChallenge;
        public String returnTo;
        public String codeVerifier;
        public String origin;
        public boolean originAllowed;
    }

    private GuideTokenExchange() {
    }

    public static Verdict evaluate(Facts facts) {
        if (facts == null || !facts.found || facts.expired || facts.consumed
                || !GuideApps.TOPIC_REFERENCE.equals(facts.appCode)) {
            return new Verdict(Status.INVALID);
        }
        if (facts.storedReturnTo == null || !facts.storedReturnTo.equals(facts.returnTo)) {
            return new Verdict(Status.INVALID);
        }
        if (!Pkce.matches(facts.codeVerifier, facts.storedChallenge)) {
            return new Verdict(Status.INVALID);
        }
        if (facts.origin != null && !facts.origin.isBlank() && !facts.originAllowed) {
            return new Verdict(Status.ORIGIN_FORBIDDEN);
        }
        return new Verdict(Status.OK);
    }

    public static String clientMessage(Verdict verdict) {
        if (verdict != null && verdict.status == Status.INVALID) {
            return INVALID_MESSAGE;
        }
        return null;
    }
}
