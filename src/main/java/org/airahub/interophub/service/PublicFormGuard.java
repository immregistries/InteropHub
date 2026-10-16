package org.airahub.interophub.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Lightweight bot protection for public, sign-in-free forms that create
 * subscriptions (campaign topic follows, campaign registration, meeting
 * requests). Three checks, all invisible to real people:
 * <ul>
 * <li>Honeypot: an off-screen text field people never see or fill.</li>
 * <li>Minimum fill time: the page's GET stamps a render time in the session;
 * a submit with no stamp, or sooner than {@link #MIN_FILL_MILLIS} after it, is
 * rejected (and the stamp is reset so a real person can simply submit
 * again).</li>
 * <li>In-memory rate limits per client IP (generous, since conference Wi-Fi
 * puts many attendees behind one address) and per email (tighter, with a
 * minimum gap that stops concurrent bursts).</li>
 * </ul>
 * Rate-limit state is per JVM and resets on restart, which is acceptable for
 * this level of protection.
 */
public final class PublicFormGuard {

    public enum Verdict {
        OK, HONEYPOT, TOO_FAST, RATE_LIMITED
    }

    public static final String HONEYPOT_FIELD = "website";

    static final long MIN_FILL_MILLIS = 2_000L;
    static final long WINDOW_MILLIS = 10 * 60_000L;
    static final int MAX_PER_IP = 60;
    static final int MAX_PER_EMAIL = 6;
    static final long MIN_GAP_PER_EMAIL_MILLIS = 2_000L;
    private static final long SWEEP_INTERVAL_MILLIS = 60_000L;

    private static final String ATTR_RENDERED_AT_PREFIX = "interophub.formGuard.renderedAt.";
    private static final Logger LOGGER = Logger.getLogger(PublicFormGuard.class.getName());
    private static final PublicFormGuard SHARED = new PublicFormGuard(System::currentTimeMillis);

    private final LongSupplier clock;
    private final Map<String, Deque<Long>> attemptsByKey = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepAt = new AtomicLong();

    PublicFormGuard(LongSupplier clock) {
        this.clock = clock;
        this.lastSweepAt.set(clock.getAsLong());
    }

    public static PublicFormGuard shared() {
        return SHARED;
    }

    /** Off-screen honeypot input to place inside each guarded form. */
    public static String honeypotHtml() {
        return "<div style=\"position:absolute;left:-10000px;top:auto;width:1px;height:1px;overflow:hidden;\""
                + " aria-hidden=\"true\"><label>Leave this field empty <input type=\"text\" name=\""
                + HONEYPOT_FIELD + "\" value=\"\" tabindex=\"-1\" autocomplete=\"off\" /></label></div>";
    }

    public static String userMessage(Verdict verdict) {
        switch (verdict) {
            case TOO_FAST:
                return "Please take a moment to review, then submit again.";
            case RATE_LIMITED:
                return "Too many submissions from this connection or email address in the last few minutes."
                        + " Please wait a bit and try again.";
            default:
                return null;
        }
    }

    /** Call from the GET that renders the guarded form. */
    public void markRendered(HttpSession session, String formKey) {
        session.setAttribute(ATTR_RENDERED_AT_PREFIX + formKey, clock.getAsLong());
    }

    /**
     * Checks a guarded form submission. On {@link Verdict#HONEYPOT} the caller
     * should quietly pretend success; on {@link Verdict#TOO_FAST} or
     * {@link Verdict#RATE_LIMITED} it should re-render the form with
     * {@link #userMessage(Verdict)}.
     */
    public Verdict check(HttpServletRequest request, String formKey, String emailNormalized) {
        String ip = request.getRemoteAddr();
        String honeypot = request.getParameter(HONEYPOT_FIELD);
        if (honeypot != null && !honeypot.isBlank()) {
            log("honeypot filled", formKey, ip, emailNormalized);
            return Verdict.HONEYPOT;
        }

        long now = clock.getAsLong();
        HttpSession session = request.getSession(true);
        Object renderedAt = session.getAttribute(ATTR_RENDERED_AT_PREFIX + formKey);
        if (!(renderedAt instanceof Long) || now - (Long) renderedAt < MIN_FILL_MILLIS) {
            markRendered(session, formKey);
            log(renderedAt == null ? "submitted without rendering the form" : "submitted too fast",
                    formKey, ip, emailNormalized);
            return Verdict.TOO_FAST;
        }

        if (!tryAcquire("ip:" + ip, MAX_PER_IP, 0L, now)
                || (emailNormalized != null
                        && !tryAcquire("email:" + emailNormalized, MAX_PER_EMAIL, MIN_GAP_PER_EMAIL_MILLIS, now))) {
            log("rate limited", formKey, ip, emailNormalized);
            return Verdict.RATE_LIMITED;
        }
        return Verdict.OK;
    }

    private boolean tryAcquire(String key, int max, long minGapMillis, long now) {
        sweepIfDue(now);
        boolean[] allowed = new boolean[1];
        attemptsByKey.compute(key, (k, attempts) -> {
            Deque<Long> deque = attempts == null ? new ArrayDeque<>() : attempts;
            prune(deque, now);
            boolean tooSoon = minGapMillis > 0 && !deque.isEmpty() && now - deque.peekLast() < minGapMillis;
            if (deque.size() < max && !tooSoon) {
                deque.addLast(now);
                allowed[0] = true;
            }
            return deque.isEmpty() ? null : deque;
        });
        return allowed[0];
    }

    private void sweepIfDue(long now) {
        long last = lastSweepAt.get();
        if (now - last < SWEEP_INTERVAL_MILLIS || !lastSweepAt.compareAndSet(last, now)) {
            return;
        }
        for (String key : attemptsByKey.keySet()) {
            attemptsByKey.computeIfPresent(key, (k, deque) -> {
                prune(deque, now);
                return deque.isEmpty() ? null : deque;
            });
        }
    }

    private static void prune(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() >= WINDOW_MILLIS) {
            deque.removeFirst();
        }
    }

    private static void log(String reason, String formKey, String ip, String emailNormalized) {
        LOGGER.warning("Public form blocked (" + reason + "): form=" + formKey + " ip=" + ip
                + " email=" + emailNormalized);
    }
}
