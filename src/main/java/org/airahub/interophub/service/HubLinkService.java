package org.airahub.interophub.service;

import org.airahub.interophub.dao.HubSettingDao;

/**
 * Builds absolute InteropHub URLs from {@code hub_settings.external_base_url}
 * for use in outgoing emails.
 */
public class HubLinkService {

    private final HubSettingDao hubSettingDao;

    public HubLinkService() {
        this.hubSettingDao = new HubSettingDao();
    }

    public String buildTopicLink(Long topicId) {
        return buildLink("/es/topic/" + topicId);
    }

    /** Prepends the configured external base URL to an app-relative path (e.g. "/es/agenda?meetingId=1"). */
    public String buildLink(String path) {
        String base = baseUrl();
        return base == null ? path : base + path;
    }

    private String baseUrl() {
        return hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .map(settings -> trimToNull(settings.getExternalBaseUrl()))
                .map(base -> base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                .orElse(null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
