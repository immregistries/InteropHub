package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.HubSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicUrlServiceTest {

    @BeforeEach
    void setup() {
        PublicUrlService.clearLocalhostModeCache();
    }

    @AfterEach
    void teardown() {
        PublicUrlService.clearLocalhostModeCache();
    }

    @Test
    void localhostAndLoopbackAreDevelopmentHosts() {
        assertTrue(serviceWithBaseUrl("http://localhost:8080/hub").isLocalhostMode());
        PublicUrlService.clearLocalhostModeCache();
        assertTrue(serviceWithBaseUrl("http://127.0.0.1:8080/hub").isLocalhostMode());
        PublicUrlService.clearLocalhostModeCache();
        assertTrue(serviceWithBaseUrl("http://LOCALHOST:8080/hub").isLocalhostMode());
    }

    @Test
    void configuredProductionUrlIsNotADevelopmentHost() {
        assertFalse(serviceWithBaseUrl("https://informatics.immregistries.org/hub").isLocalhostMode());
    }

    @Test
    void theEnvironmentIsReadOnceAndThenReused() {
        AtomicInteger reads = new AtomicInteger();
        PublicUrlService service = serviceWithBaseUrl("https://informatics.immregistries.org/hub", reads);

        assertFalse(service.isLocalhostMode());
        assertFalse(service.isLocalhostMode());
        assertFalse(service.isLocalhostMode());

        assertEquals(1, reads.get(), "the environment must not be re-read per call");
    }

    @Test
    void aFailedReadIsNotCachedSoALaterAttemptCanSucceed() {
        AtomicInteger reads = new AtomicInteger();
        PublicUrlService service = new PublicUrlService(new HubSettingDao() {
            @Override
            public Optional<HubSetting> findActive() {
                reads.incrementAndGet();
                throw new IllegalStateException("database unavailable");
            }

            @Override
            public Optional<HubSetting> findFirst() {
                return Optional.empty();
            }
        });

        assertTrue(catchFailure(service), "an unreadable environment must fail closed");
        assertEquals(1, reads.get());

        // The failure must not have been cached: a later call retries the read.
        assertTrue(catchFailure(service), "a failed read must not be cached as the resolved value");
        assertEquals(2, reads.get());
    }

    @Test
    void clearingTheCacheForcesTheEnvironmentToBeReadAgain() {
        AtomicInteger reads = new AtomicInteger();
        PublicUrlService service = serviceWithBaseUrl("https://informatics.immregistries.org/hub", reads);

        assertFalse(service.isLocalhostMode());
        assertEquals(1, reads.get());

        PublicUrlService.clearLocalhostModeCache();

        assertFalse(service.isLocalhostMode());
        assertEquals(2, reads.get());
    }

    private static boolean catchFailure(PublicUrlService service) {
        try {
            service.isLocalhostMode();
            return false;
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    private static PublicUrlService serviceWithBaseUrl(String baseUrl) {
        return serviceWithBaseUrl(baseUrl, new AtomicInteger());
    }

    private static PublicUrlService serviceWithBaseUrl(String baseUrl, AtomicInteger reads) {
        return new PublicUrlService(new HubSettingDao() {
            @Override
            public Optional<HubSetting> findActive() {
                reads.incrementAndGet();
                HubSetting setting = new HubSetting();
                setting.setExternalBaseUrl(baseUrl);
                return Optional.of(setting);
            }

            @Override
            public Optional<HubSetting> findFirst() {
                return Optional.empty();
            }
        });
    }
}
