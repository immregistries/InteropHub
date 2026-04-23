package org.airahub.interophub.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsInterestDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsCampaignTopic;
import org.airahub.interophub.model.EsTopic;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One-time admin import service for ES topics and campaign topic assignments.
 * Parses newline-separated JSON objects and upserts into es_topic /
 * es_campaign_topic.
 */
public class EsTopicImportService {

    private final EsTopicDao topicDao;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsInterestDao interestDao;
    private final EsCommentDao commentDao;
    private final EsSubscriptionDao subscriptionDao;

    public EsTopicImportService() {
        this.topicDao = new EsTopicDao();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.interestDao = new EsInterestDao();
        this.commentDao = new EsCommentDao();
        this.subscriptionDao = new EsSubscriptionDao();
    }

    /**
     * Imports topics from newline-separated JSON objects and assigns them to a
     * campaign.
     *
     * <p>
     * Campaign resolution order:
     * <ol>
     * <li>If {@code newCampaignCode} and {@code newCampaignName} are both
     * non-blank, find or
     * create the campaign by code (new campaign wins over selectedCampaignId).</li>
     * <li>Otherwise, use {@code selectedCampaignId}.</li>
     * <li>If neither is supplied, throws {@link IllegalArgumentException}.</li>
     * </ol>
     *
     * <p>
     * Expected JSON fields per line:
     * 
     * <pre>
     * {
     *   "topicCode": "code",       // required
     *   "topicName": "name",       // required
     *   "description": "...",      // nullable
     *   "neighborhood": "...",     // nullable
     *   "priorityIis": 2,          // integer, defaults 0
     *   "priorityEhr": 1,          // integer, defaults 0
     *   "priorityCdc": 3,          // integer, defaults 0
     *   "stage": "...",            // nullable
    *   "policyStatus": "...",     // nullable
    *   "topicType": "...",        // nullable
    *   "confluenceUrl": "...",    // nullable
     *   "displayOrder": 10,        // integer, defaults 0
     *   "set": 1                   // nullable integer for topic_set_no
     * }
     * </pre>
     */
    public ImportResult importLines(String rawLines, Long selectedCampaignId,
            String newCampaignCode, String newCampaignName, Long adminUserId, int tablesPerSet) {

        EsCampaign campaign = resolveCampaign(
                selectedCampaignId, newCampaignCode, newCampaignName, adminUserId);

        boolean allowCampaignReset = campaign.getStatus() == null
                || campaign.getStatus() == EsCampaign.CampaignStatus.DRAFT;

        if (allowCampaignReset) {
            Long campaignId = campaign.getEsCampaignId();
            // Draft campaigns are reset to a clean slate before rebuilding assignments.
            interestDao.deleteByCampaignId(campaignId);
            commentDao.deleteByCampaignId(campaignId);
            subscriptionDao.deleteBySourceCampaignId(campaignId);
            campaignTopicDao.deleteByCampaignId(campaignId);
        }

        String[] lines = rawLines.split("\r?\n");
        int linesProcessed = 0;
        int topicsInserted = 0;
        int topicsUpdated = 0;
        int campaignTopicsInserted = 0;
        int campaignTopicsUpdated = 0;
        int duplicateTopicCodes = 0;
        Set<String> seenTopicCodes = new HashSet<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            JSONObject json;
            try {
                json = new JSONObject(line);
            } catch (JSONException ex) {
                return ImportResult.failure(linesProcessed, i + 1,
                        "Malformed JSON on line " + (i + 1) + ": " + ex.getMessage(),
                        topicsInserted, topicsUpdated,
                        campaignTopicsInserted, campaignTopicsUpdated,
                        duplicateTopicCodes,
                        campaign.getCampaignCode(), campaign.getCampaignName());
            }

            try {
                Integer topicSetNo = !json.has("set") || json.isNull("set") ? null : json.getInt("set");

                // ── Upsert es_topic ──────────────────────────────────────────────────
                String topicCode = json.getString("topicCode");

                if (seenTopicCodes.contains(topicCode)) {
                    duplicateTopicCodes++;
                }
                seenTopicCodes.add(topicCode);

                Optional<EsTopic> existingTopic = topicDao.findByTopicCode(topicCode);
                EsTopic topic;
                boolean isNewTopic;
                if (existingTopic.isPresent()) {
                    topic = existingTopic.get();
                    isNewTopic = false;
                } else {
                    topic = new EsTopic();
                    topic.setTopicCode(topicCode);
                    topic.setCreatedByUserId(adminUserId);
                    isNewTopic = true;
                }

                // Import policy: topic metadata is always upserted regardless of campaign
                // status. Only campaign-assignment rebuild logic is gated to DRAFT campaigns.
                topic.setTopicName(json.getString("topicName"));
                topic.setDescription(
                        json.isNull("description") ? null : json.optString("description", null));
                topic.setNeighborhood(
                        json.isNull("neighborhood") ? null : json.optString("neighborhood", null));
                topic.setPriorityIis(json.optInt("priorityIis", 0));
                topic.setPriorityEhr(json.optInt("priorityEhr", 0));
                topic.setPriorityCdc(json.optInt("priorityCdc", 0));
                topic.setStage(json.isNull("stage") ? null : json.optString("stage", null));
                topic.setPolicyStatus(readNullableTrimmedString(json, "policyStatus"));
                topic.setTopicType(readNullableTrimmedString(json, "topicType"));
                topic.setConfluenceUrl(readNullableTrimmedString(json, "confluenceUrl"));

                topic = topicDao.saveOrUpdate(topic);

                if (isNewTopic) {
                    topicsInserted++;
                } else {
                    topicsUpdated++;
                }

                if (allowCampaignReset) {
                    // ── Rebuild es_campaign_topic rows only while campaign is DRAFT ───────────
                    // Topics without a valid set are intentionally not assigned to any campaign
                    // table.
                    if (topicSetNo == null || topicSetNo < 1) {
                        linesProcessed++;
                        continue;
                    }

                    Long campaignId = campaign.getEsCampaignId();
                    Long topicId = topic.getEsTopicId();
                    int displayOrder = json.optInt("displayOrder", 0);
                    // With tablesPerSet=N and set S: tables (S-1)*N+1 .. S*N.
                    int startTable = (topicSetNo - 1) * tablesPerSet + 1;
                    int endTable = topicSetNo * tablesPerSet;

                    List<Integer> expectedTableNos = IntStream.rangeClosed(startTable, endTable)
                            .boxed()
                            .collect(Collectors.toList());

                    // Defensive cleanup if duplicates/stale rows already exist during this import
                    // run.
                    campaignTopicDao.deleteByCampaignIdAndTopicIdAndTableNoNotIn(
                            campaignId, topicId, expectedTableNos);

                    for (int tableNo = startTable; tableNo <= endTable; tableNo++) {
                        Optional<EsCampaignTopic> existingCt = campaignTopicDao
                                .findByCampaignIdAndTopicIdAndTableNo(campaignId, topicId, tableNo);
                        EsCampaignTopic ct;
                        boolean isNewCt;
                        if (existingCt.isPresent()) {
                            ct = existingCt.get();
                            isNewCt = false;
                        } else {
                            ct = new EsCampaignTopic();
                            ct.setEsCampaignId(campaignId);
                            ct.setEsTopicId(topicId);
                            ct.setTableNo(tableNo);
                            isNewCt = true;
                        }

                        ct.setDisplayOrder(displayOrder);
                        ct.setTopicSetNo(topicSetNo);

                        campaignTopicDao.saveOrUpdate(ct);

                        if (isNewCt) {
                            campaignTopicsInserted++;
                        } else {
                            campaignTopicsUpdated++;
                        }
                    }
                }

            } catch (JSONException ex) {
                return ImportResult.failure(linesProcessed, i + 1,
                        "Invalid field on line " + (i + 1) + ": " + ex.getMessage(),
                        topicsInserted, topicsUpdated,
                        campaignTopicsInserted, campaignTopicsUpdated,
                        duplicateTopicCodes,
                        campaign.getCampaignCode(), campaign.getCampaignName());
            }

            linesProcessed++;
        }

        return ImportResult.success(linesProcessed, topicsInserted, topicsUpdated,
                campaignTopicsInserted, campaignTopicsUpdated, duplicateTopicCodes,
                campaign.getCampaignCode(), campaign.getCampaignName());
    }

    private EsCampaign resolveCampaign(Long selectedCampaignId, String newCampaignCode,
            String newCampaignName, Long adminUserId) {
        if (newCampaignCode != null && newCampaignName != null) {
            Optional<EsCampaign> existing = campaignDao.findByCampaignCode(newCampaignCode);
            if (existing.isPresent()) {
                return existing.get();
            }
            EsCampaign campaign = new EsCampaign();
            campaign.setCampaignCode(newCampaignCode);
            campaign.setCampaignName(newCampaignName);
            campaign.setCreatedByUserId(adminUserId);
            return campaignDao.saveOrUpdate(campaign);
        }
        if (selectedCampaignId != null) {
            return campaignDao.findById(selectedCampaignId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected campaign not found."));
        }
        throw new IllegalArgumentException(
                "A campaign is required. Select an existing campaign or enter a new campaign code and name.");
    }

    private String readNullableTrimmedString(JSONObject json, String fieldName) {
        if (json.isNull(fieldName)) {
            return null;
        }
        String raw = json.optString(fieldName, null);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── Result DTO
    // ────────────────────────────────────────────────────────────────────────────────

    public static class ImportResult {

        private final int linesProcessed;
        private final int topicsInserted;
        private final int topicsUpdated;
        private final int campaignTopicsInserted;
        private final int campaignTopicsUpdated;
        private final int duplicateTopicCodes;
        private final String campaignCode;
        private final String campaignName;
        private final String errorMessage;
        private final int errorLine;

        private ImportResult(int linesProcessed, int topicsInserted, int topicsUpdated,
                int campaignTopicsInserted, int campaignTopicsUpdated, int duplicateTopicCodes,
                String campaignCode, String campaignName, String errorMessage, int errorLine) {
            this.linesProcessed = linesProcessed;
            this.topicsInserted = topicsInserted;
            this.topicsUpdated = topicsUpdated;
            this.campaignTopicsInserted = campaignTopicsInserted;
            this.campaignTopicsUpdated = campaignTopicsUpdated;
            this.duplicateTopicCodes = duplicateTopicCodes;
            this.campaignCode = campaignCode;
            this.campaignName = campaignName;
            this.errorMessage = errorMessage;
            this.errorLine = errorLine;
        }

        public static ImportResult success(int linesProcessed, int topicsInserted, int topicsUpdated,
                int campaignTopicsInserted, int campaignTopicsUpdated, int duplicateTopicCodes,
                String campaignCode, String campaignName) {
            return new ImportResult(linesProcessed, topicsInserted, topicsUpdated,
                    campaignTopicsInserted, campaignTopicsUpdated, duplicateTopicCodes,
                    campaignCode, campaignName, null, 0);
        }

        public static ImportResult failure(int linesProcessed, int errorLine, String errorMessage,
                int topicsInserted, int topicsUpdated, int campaignTopicsInserted,
                int campaignTopicsUpdated, int duplicateTopicCodes,
                String campaignCode, String campaignName) {
            return new ImportResult(linesProcessed, topicsInserted, topicsUpdated,
                    campaignTopicsInserted, campaignTopicsUpdated, duplicateTopicCodes,
                    campaignCode, campaignName, errorMessage, errorLine);
        }

        public int getLinesProcessed() {
            return linesProcessed;
        }

        public int getTopicsInserted() {
            return topicsInserted;
        }

        public int getTopicsUpdated() {
            return topicsUpdated;
        }

        public int getCampaignTopicsInserted() {
            return campaignTopicsInserted;
        }

        public int getCampaignTopicsUpdated() {
            return campaignTopicsUpdated;
        }

        public int getDuplicateTopicCodes() {
            return duplicateTopicCodes;
        }

        public String getCampaignCode() {
            return campaignCode;
        }

        public String getCampaignName() {
            return campaignName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getErrorLine() {
            return errorLine;
        }
    }
}
