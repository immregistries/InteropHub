package org.airahub.interophub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.airahub.interophub.service.ArtifactBlobStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactStorageHealthListenerTest {

    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getLogger(ArtifactStorageHealthListener.class.getName());
    private final Handler handler = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeEach
    void setup() {
        logger.addHandler(handler);
    }

    @AfterEach
    void teardown() {
        logger.removeHandler(handler);
        records.clear();
    }

    @Test
    void anAvailableManagedIdentityIsReportedAsEnabled() {
        ArtifactStorageHealthListener.report(ArtifactBlobStorageService.Environment.PRODUCTION, true);

        assertEquals(1, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("enabled (managed identity available)"),
                records.get(0).getMessage());
    }

    @Test
    void aMissingManagedIdentityIsReportedAsUnavailable() {
        ArtifactStorageHealthListener.report(ArtifactBlobStorageService.Environment.PRODUCTION, false);

        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("managed identity unavailable"),
                records.get(0).getMessage());
    }

    @Test
    void aDevelopmentHostIsReportedAsDevelopmentMode() {
        ArtifactStorageHealthListener.report(ArtifactBlobStorageService.Environment.DEVELOPMENT, false);

        assertEquals(1, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("disabled (development mode)"),
                records.get(0).getMessage());
    }

    @Test
    void aFailedEnvironmentCheckIsNotReportedAsDevelopmentMode() {
        ArtifactStorageHealthListener.report(ArtifactBlobStorageService.Environment.UNKNOWN, false);

        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        String message = records.get(0).getMessage();
        assertTrue(message.contains("environment check failed"), message);
        assertTrue(!message.contains("development mode"), message);
    }
}
