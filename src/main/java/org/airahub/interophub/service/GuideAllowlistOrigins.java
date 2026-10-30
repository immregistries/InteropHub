package org.airahub.interophub.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.airahub.interophub.dao.AppRedirectAllowlistDao;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.model.AppRedirectAllowlist;
import org.airahub.interophub.model.AppRegistry;

/** Enabled {@code topic-reference} allowlist rows, reduced to origins. */
public final class GuideAllowlistOrigins implements Supplier<Set<String>> {
    private final AppRegistryDao appRegistryDao;
    private final AppRedirectAllowlistDao allowlistDao;

    public GuideAllowlistOrigins() {
        this(new AppRegistryDao(), new AppRedirectAllowlistDao());
    }

    GuideAllowlistOrigins(AppRegistryDao appRegistryDao, AppRedirectAllowlistDao allowlistDao) {
        this.appRegistryDao = appRegistryDao;
        this.allowlistDao = allowlistDao;
    }

    @Override
    public Set<String> get() {
        AppRegistry app = appRegistryDao.findByAppCode(GuideApps.TOPIC_REFERENCE).orElse(null);
        if (app == null || app.getAppId() == null) {
            return Set.of();
        }
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        for (AppRedirectAllowlist row : allowlistDao.findEnabledByAppId(app.getAppId())) {
            String origin = GuideCors.originOf(row.getBaseUrl());
            if (origin != null) {
                origins.add(origin);
            }
        }
        return Set.copyOf(origins);
    }
}
