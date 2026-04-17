package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignRegistrationDao;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsInterestDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsInterest;

public class TableVoteService {

    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsInterestDao interestDao;
    private final EsCampaignRegistrationDao campaignRegistrationDao;

    public TableVoteService() {
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.interestDao = new EsInterestDao();
        this.campaignRegistrationDao = new EsCampaignRegistrationDao();
    }

    public Optional<EsCampaign> findCampaignExact(String campaignCode) {
        return campaignDao.findByCampaignCode(campaignCode)
                .filter(campaign -> campaignCode != null && campaignCode.equals(campaign.getCampaignCode()));
    }

    public List<EsCampaignTopicBrowseRow> findTableTopics(Long campaignId, Integer tableNo) {
        return campaignTopicDao.findBrowseRowsByCampaignIdAndTableNoOrdered(campaignId, tableNo);
    }

    public int resolveCurrentRound(EsCampaign campaign) {
        if (campaign == null || campaign.getCurrentRoundNo() == null || campaign.getCurrentRoundNo() < 1) {
            return 1;
        }
        return campaign.getCurrentRoundNo();
    }

    public Set<Long> findSessionSelections(Long campaignId, Integer tableNo, Integer roundNo, String sessionKey) {
        return interestDao.findTopicIdsByCampaignAndTableAndRoundAndSession(campaignId, tableNo, roundNo, sessionKey);
    }

    public Map<Long, Long> findVoteTotals(Long campaignId, Integer tableNo, Integer roundNo) {
        return interestDao.findVoteTotalsByCampaignAndTableAndRound(campaignId, tableNo, roundNo).stream()
                .collect(Collectors.toMap(
                        EsInterestDao.VoteTotalRow::getEsTopicId,
                        EsInterestDao.VoteTotalRow::getVoteCount));
    }

    public SubmitResult submitVote(EsCampaign campaign, Integer tableNo, String sessionKey,
            List<Long> requestedTopicIds,
            String sessionCampaignCode, Long sessionCampaignRegistrationId) {
        if (campaign == null || campaign.getEsCampaignId() == null) {
            throw new IllegalArgumentException("Campaign is required.");
        }
        if (tableNo == null || tableNo < 1) {
            throw new IllegalArgumentException("A valid table number is required.");
        }
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("A session key is required.");
        }

        List<EsCampaignTopicBrowseRow> tableTopics = findTableTopics(campaign.getEsCampaignId(), tableNo);
        if (tableTopics.isEmpty()) {
            throw new IllegalArgumentException("No topics are assigned to this table.");
        }

        List<Long> normalizedRequested = sanitizeRequestedTopicIds(requestedTopicIds);
        if (normalizedRequested.isEmpty()) {
            throw new IllegalArgumentException("Select at least one topic.");
        }
        if (normalizedRequested.size() > 3) {
            throw new IllegalArgumentException("Select no more than three topics.");
        }

        Set<Long> allowedTopicIds = tableTopics.stream()
                .map(EsCampaignTopicBrowseRow::getEsTopicId)
                .collect(Collectors.toSet());
        if (!allowedTopicIds.containsAll(normalizedRequested)) {
            throw new IllegalArgumentException("One or more selected topics are not assigned to this table.");
        }

        int currentRoundNo = resolveCurrentRound(campaign);
        Long campaignRegistrationId = resolveCampaignRegistrationId(campaign, sessionKey, sessionCampaignCode,
                sessionCampaignRegistrationId);

        interestDao.deleteByCampaignAndTableAndRoundAndSession(campaign.getEsCampaignId(), tableNo, currentRoundNo,
                sessionKey);

        for (Long topicId : normalizedRequested) {
            EsInterest vote = new EsInterest();
            vote.setEsCampaignId(campaign.getEsCampaignId());
            vote.setEsTopicId(topicId);
            vote.setEsCampaignRegistrationId(campaignRegistrationId);
            vote.setSessionKey(sessionKey);
            vote.setTableNo(tableNo);
            vote.setRoundNo(currentRoundNo);
            interestDao.saveOrUpdate(vote);
        }

        return new SubmitResult(currentRoundNo, Set.copyOf(normalizedRequested));
    }

    private List<Long> sanitizeRequestedTopicIds(List<Long> requestedTopicIds) {
        if (requestedTopicIds == null || requestedTopicIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : requestedTopicIds) {
            if (id != null && id > 0L) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    private Long resolveCampaignRegistrationId(EsCampaign campaign, String sessionKey, String sessionCampaignCode,
            Long sessionCampaignRegistrationId) {
        if (sessionCampaignRegistrationId != null
                && sessionCampaignCode != null
                && sessionCampaignCode.equals(campaign.getCampaignCode())) {
            return sessionCampaignRegistrationId;
        }
        return campaignRegistrationDao.findLatestIdByCampaignAndSessionKey(campaign.getEsCampaignId(), sessionKey)
                .orElse(null);
    }

    public static final class SubmitResult {
        private final int roundNo;
        private final Set<Long> selectedTopicIds;

        private SubmitResult(int roundNo, Set<Long> selectedTopicIds) {
            this.roundNo = roundNo;
            this.selectedTopicIds = selectedTopicIds;
        }

        public int getRoundNo() {
            return roundNo;
        }

        public Set<Long> getSelectedTopicIds() {
            return selectedTopicIds;
        }
    }
}
