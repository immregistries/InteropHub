package org.airahub.interophub.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares a Small Group's participants with the people registered for its
 * meeting series. The series is not the participant list.
 */
public final class SmallGroupSeriesComparison {

    public record Person(Long memberId, String emailNormalized, Long userId) {
    }

    public record Registrant(String emailNormalized, Long userId, String status, String email, String label) {
    }

    public record Result(List<Person> participantsNotRegistered, List<Registrant> registrantsNotParticipants) {
    }

    private SmallGroupSeriesComparison() {
    }

    /** Only an approved registration counts as registered. A request is still listed, separately. */
    public static boolean registeredStatus(String status) {
        return "APPROVED".equals(status);
    }

    public static boolean samePerson(String emailA, Long userA, String emailB, Long userB) {
        if (emailA != null && !emailA.isBlank() && emailB != null && emailA.equalsIgnoreCase(emailB)) {
            return true;
        }
        return userA != null && userA.equals(userB);
    }

    public static Result compare(List<Person> participants, List<Registrant> registrants) {
        List<Registrant> registered = new ArrayList<>();
        if (registrants != null) {
            for (Registrant registrant : registrants) {
                if (registrant != null && registeredStatus(registrant.status())) {
                    registered.add(registrant);
                }
            }
        }
        List<Person> notRegistered = new ArrayList<>();
        if (participants != null) {
            for (Person participant : participants) {
                if (participant == null) {
                    continue;
                }
                boolean found = false;
                for (Registrant registrant : registered) {
                    if (samePerson(participant.emailNormalized(), participant.userId(),
                            registrant.emailNormalized(), registrant.userId())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    notRegistered.add(participant);
                }
            }
        }
        List<Registrant> notParticipants = new ArrayList<>();
        for (Registrant registrant : registered) {
            boolean found = false;
            if (participants != null) {
                for (Person participant : participants) {
                    if (participant != null && samePerson(participant.emailNormalized(), participant.userId(),
                            registrant.emailNormalized(), registrant.userId())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                notParticipants.add(registrant);
            }
        }
        return new Result(notRegistered, notParticipants);
    }

    public static String registrationPath(String campaignCode, String topicCode) {
        if (campaignCode == null || campaignCode.isBlank() || topicCode == null || topicCode.isBlank()) {
            return null;
        }
        return "/registerForMeeting/" + encode(campaignCode.trim()) + "/" + encode(topicCode.trim());
    }

    /** Text placed on the clipboard. A person link names who it is for; the URL is the series link. */
    public static String copyText(String personLabel, String absoluteUrl) {
        if (absoluteUrl == null || absoluteUrl.isBlank()) {
            return null;
        }
        if (personLabel == null || personLabel.isBlank()) {
            return absoluteUrl;
        }
        return personLabel.trim() + "\n" + absoluteUrl;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
