package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingPollDao;
import org.airahub.interophub.dao.EsTopicMeetingPollOptionDao;
import org.airahub.interophub.dao.EsTopicMeetingPollResponseDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingPoll;
import org.airahub.interophub.model.EsTopicMeetingPollOption;
import org.airahub.interophub.model.EsTopicMeetingPollResponse.PollResponseValue;
import org.airahub.interophub.model.User;

public class EsTopicMeetingPollService {

    public static final String DEFAULT_TIMEZONE = "America/New_York";

    public static final Set<String> ALLOWED_TIMEZONES = Set.of(
            "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
            "America/Phoenix", "America/Anchorage", "Pacific/Honolulu",
            "America/Sao_Paulo", "America/Santiago",
            "Europe/London", "Europe/Paris",
            "Africa/Johannesburg",
            "Asia/Kolkata", "Asia/Tokyo",
            "Australia/Sydney",
            "Pacific/Auckland");

    private final EsTopicMeetingPollDao pollDao;
    private final EsTopicMeetingPollOptionDao optionDao;
    private final EsTopicMeetingPollResponseDao responseDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsInterestService interestService;
    private final EsSubscriptionDao subscriptionDao;
    private final UserDao userDao;

    public EsTopicMeetingPollService() {
        this.pollDao = new EsTopicMeetingPollDao();
        this.optionDao = new EsTopicMeetingPollOptionDao();
        this.responseDao = new EsTopicMeetingPollResponseDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.interestService = new EsInterestService();
        this.subscriptionDao = new EsSubscriptionDao();
        this.userDao = new UserDao();
    }

    public EsTopicMeetingPoll getPollRequired(Long pollId) {
        return pollDao.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found."));
    }

    public List<EsTopicMeetingPoll> listPollsForMeeting(Long esTopicMeetingId) {
        return pollDao.findByTopicMeetingId(esTopicMeetingId);
    }

    public EsTopicMeetingPoll createPoll(Long esTopicMeetingId, String pollName, String pollDescription,
            String defaultTimezone) {
        EsTopicMeeting meeting = requireActiveMeeting(esTopicMeetingId);
        EsTopicMeetingPoll poll = new EsTopicMeetingPoll();
        poll.setEsTopicMeetingId(meeting.getEsTopicMeetingId());
        poll.setPollName(required(trimToNull(pollName), "Poll name"));
        poll.setPollDescription(trimToNull(pollDescription));
        poll.setDefaultTimezone(normalizeTimezone(defaultTimezone));
        return pollDao.saveOrUpdate(poll);
    }

    public EsTopicMeetingPoll updatePoll(Long pollId, String pollName, String pollDescription,
            String defaultTimezone) {
        EsTopicMeetingPoll poll = requirePollOnActiveMeeting(pollId);
        poll.setPollName(required(trimToNull(pollName), "Poll name"));
        poll.setPollDescription(trimToNull(pollDescription));
        poll.setDefaultTimezone(normalizeTimezone(defaultTimezone));
        return pollDao.saveOrUpdate(poll);
    }

    public List<EsTopicMeetingPollOption> listOptionsOrdered(Long pollId) {
        return optionDao.findByPollIdOrdered(pollId);
    }

    public EsTopicMeetingPollOption addOption(Long pollId, LocalDateTime localStart, LocalDateTime localEnd,
            String inputTimezone) {
        EsTopicMeetingPoll poll = requirePollOnActiveMeeting(pollId);
        String timezoneId = normalizeTimezone(inputTimezone != null ? inputTimezone : poll.getDefaultTimezone());
        EsTopicMeetingPollOption option = new EsTopicMeetingPollOption();
        option.setEsTopicMeetingPollId(poll.getEsTopicMeetingPollId());
        option.setStartsAtUtc(toUtcLocalDateTime(localStart, timezoneId));
        option.setEndsAtUtc(localEnd != null ? toUtcLocalDateTime(localEnd, timezoneId) : null);
        option.setDisplayOrder(optionDao.maxDisplayOrder(pollId) + 1);
        validateOptionTimes(option.getStartsAtUtc(), option.getEndsAtUtc());
        return optionDao.saveOrUpdate(option);
    }

    public EsTopicMeetingPollOption updateOption(Long pollId, Long optionId, Integer displayOrder,
            LocalDateTime localStart, LocalDateTime localEnd, String inputTimezone) {
        EsTopicMeetingPoll poll = requirePollOnActiveMeeting(pollId);
        EsTopicMeetingPollOption option = optionDao.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Poll option not found."));
        if (!poll.getEsTopicMeetingPollId().equals(option.getEsTopicMeetingPollId())) {
            throw new IllegalArgumentException("Poll option does not belong to this poll.");
        }
        String timezoneId = normalizeTimezone(inputTimezone != null ? inputTimezone : poll.getDefaultTimezone());
        option.setStartsAtUtc(toUtcLocalDateTime(localStart, timezoneId));
        option.setEndsAtUtc(localEnd != null ? toUtcLocalDateTime(localEnd, timezoneId) : null);
        if (displayOrder != null) {
            option.setDisplayOrder(displayOrder);
        }
        validateOptionTimes(option.getStartsAtUtc(), option.getEndsAtUtc());
        return optionDao.saveOrUpdate(option);
    }

    public int deleteOption(Long pollId, Long optionId) {
        requirePollOnActiveMeeting(pollId);
        return optionDao.deleteByIdAndPollId(optionId, pollId);
    }

    public Map<Long, PollResponseValue> getCurrentUserResponses(Long pollId, Long userId) {
        return responseDao.findByPollIdAndUserId(pollId, userId);
    }

    public void saveUserResponses(Long pollId, User user, Map<Long, PollResponseValue> responsesByOptionId) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
        EsTopicMeetingPoll poll = requirePollOnActiveMeeting(pollId);
        if (responsesByOptionId == null || responsesByOptionId.isEmpty()) {
            throw new IllegalArgumentException("At least one response is required.");
        }

        Map<Long, EsTopicMeetingPollOption> optionById = optionDao.findByPollIdOrdered(pollId).stream()
                .collect(Collectors.toMap(EsTopicMeetingPollOption::getEsTopicMeetingPollOptionId, o -> o));

        Map<Long, PollResponseValue> validResponses = new LinkedHashMap<>();
        boolean hasYesOrMaybe = false;
        for (Map.Entry<Long, PollResponseValue> entry : responsesByOptionId.entrySet()) {
            Long optionId = entry.getKey();
            PollResponseValue responseValue = entry.getValue();
            if (optionId == null || responseValue == null || !optionById.containsKey(optionId)) {
                throw new IllegalArgumentException("A response references an option outside this poll.");
            }
            validResponses.put(optionId, responseValue);
            if (responseValue == PollResponseValue.YES || responseValue == PollResponseValue.MAYBE) {
                hasYesOrMaybe = true;
            }
        }

        responseDao.saveOrUpdateResponses(user.getUserId(), validResponses);

        if (hasYesOrMaybe) {
            ensureTopicSubscription(poll, user);
        }
    }

    public PollResultsData getResults(Long pollId) {
        EsTopicMeetingPoll poll = requirePollOnActiveMeeting(pollId);
        List<EsTopicMeetingPollOption> options = optionDao.findByPollIdOrdered(pollId);

        Map<Long, Map<PollResponseValue, Integer>> countsByOption = new LinkedHashMap<>();
        for (EsTopicMeetingPollOption option : options) {
            Map<PollResponseValue, Integer> counts = new HashMap<>();
            counts.put(PollResponseValue.YES, 0);
            counts.put(PollResponseValue.MAYBE, 0);
            counts.put(PollResponseValue.NO, 0);
            countsByOption.put(option.getEsTopicMeetingPollOptionId(), counts);
        }
        for (EsTopicMeetingPollResponseDao.OptionAggregateRow row : responseDao.aggregateByPollId(pollId)) {
            countsByOption.computeIfAbsent(row.getOptionId(), ignored -> new HashMap<>())
                    .put(row.getResponse(), row.getCount());
        }

        Map<Long, Map<PollResponseValue, List<String>>> namesByOption = new LinkedHashMap<>();
        for (EsTopicMeetingPollOption option : options) {
            Map<PollResponseValue, List<String>> names = new HashMap<>();
            names.put(PollResponseValue.YES, new ArrayList<>());
            names.put(PollResponseValue.MAYBE, new ArrayList<>());
            names.put(PollResponseValue.NO, new ArrayList<>());
            namesByOption.put(option.getEsTopicMeetingPollOptionId(), names);
        }
        for (EsTopicMeetingPollResponseDao.OptionResponderNameRow row : responseDao
                .findResponderNamesByPollId(pollId)) {
            namesByOption
                    .computeIfAbsent(row.getOptionId(), ignored -> new HashMap<>())
                    .computeIfAbsent(row.getResponse(), ignored -> new ArrayList<>())
                    .add(row.getFullName());
        }

        return new PollResultsData(poll, options, countsByOption, namesByOption);
    }

    public String resolveEffectiveTimezone(User user, EsTopicMeetingPoll poll) {
        if (user != null) {
            String userTz = trimToNull(user.getTimezoneId());
            if (userTz != null && ALLOWED_TIMEZONES.contains(userTz)) {
                return userTz;
            }
        }
        return normalizeTimezone(poll.getDefaultTimezone());
    }

    public void updateUserTimezone(User user, String timezoneId) {
        if (user == null || user.getUserId() == null) {
            return;
        }
        String normalized = normalizeTimezone(timezoneId);
        if (!normalized.equals(user.getTimezoneId())) {
            user.setTimezoneId(normalized);
            userDao.saveOrUpdate(user);
        }
    }

    public LocalDateTime toUtcLocalDateTime(LocalDateTime localDateTime, String timezoneId) {
        if (localDateTime == null) {
            throw new IllegalArgumentException("Date/time is required.");
        }
        ZoneId source = safeZoneId(timezoneId);
        return localDateTime.atZone(source)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    public LocalDateTime fromUtcToLocal(LocalDateTime utcDateTime, String timezoneId) {
        if (utcDateTime == null) {
            return null;
        }
        ZoneId target = safeZoneId(timezoneId);
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(target)
                .toLocalDateTime();
    }

    private void ensureTopicSubscription(EsTopicMeetingPoll poll, User user) {
        EsTopicMeeting meeting = requireActiveMeeting(poll.getEsTopicMeetingId());
        if (meeting.getEsTopicId() == null) {
            return;
        }
        String emailNormalized = EsNormalizer.normalizeEmail(user.getEmail());
        if (emailNormalized == null) {
            return;
        }

        Optional<EsSubscription> existing = subscriptionDao.findByUserOrEmailAndTopic(
                user.getUserId(), emailNormalized, meeting.getEsTopicId());
        if (existing.isPresent()) {
            EsSubscription current = existing.get();
            if (current.getStatus() == EsSubscription.SubscriptionStatus.UNSUBSCRIBED) {
                current.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
                current.setUnsubscribedAt(null);
            }
            if (current.getUserId() == null) {
                current.setUserId(user.getUserId());
            }
            if (trimToNull(current.getEmail()) == null) {
                current.setEmail(user.getEmail());
            }
            subscriptionDao.saveOrUpdate(current);
            return;
        }

        EsSubscription sub = new EsSubscription();
        sub.setEmail(user.getEmail());
        sub.setEmailNormalized(emailNormalized);
        sub.setUserId(user.getUserId());
        sub.setEsTopicId(meeting.getEsTopicId());
        sub.setSubscriptionType(EsSubscription.SubscriptionType.TOPIC);
        sub.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
        interestService.subscribeOrUpdate(sub);
    }

    private EsTopicMeetingPoll requirePollOnActiveMeeting(Long pollId) {
        EsTopicMeetingPoll poll = getPollRequired(pollId);
        requireActiveMeeting(poll.getEsTopicMeetingId());
        return poll;
    }

    private EsTopicMeeting requireActiveMeeting(Long esTopicMeetingId) {
        EsTopicMeeting meeting = topicMeetingDao.findById(esTopicMeetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting was not found."));
        if (meeting.getStatus() != EsTopicMeeting.MeetingStatus.ACTIVE) {
            throw new IllegalArgumentException("Poll must belong to an active meeting.");
        }
        return meeting;
    }

    private void validateOptionTimes(LocalDateTime startsAtUtc, LocalDateTime endsAtUtc) {
        if (startsAtUtc == null) {
            throw new IllegalArgumentException("Option start time is required.");
        }
        if (endsAtUtc != null && endsAtUtc.isBefore(startsAtUtc)) {
            throw new IllegalArgumentException("Option end time must be on or after start time.");
        }
    }

    private String normalizeTimezone(String timezoneId) {
        String tz = trimToNull(timezoneId);
        if (tz == null || !ALLOWED_TIMEZONES.contains(tz)) {
            return DEFAULT_TIMEZONE;
        }
        return tz;
    }

    private ZoneId safeZoneId(String timezoneId) {
        String normalized = normalizeTimezone(timezoneId);
        try {
            return ZoneId.of(normalized);
        } catch (Exception ignored) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    private String required(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record PollResultsData(
            EsTopicMeetingPoll poll,
            List<EsTopicMeetingPollOption> options,
            Map<Long, Map<PollResponseValue, Integer>> countsByOption,
            Map<Long, Map<PollResponseValue, List<String>>> namesByOption) {
    }
}
