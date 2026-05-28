package org.airahub.interophub.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.service.DandelionSyncService;

@WebListener
public class DandelionSyncSchedulerListener implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(DandelionSyncSchedulerListener.class.getName());

    private static final int POLL_INTERVAL_SECONDS = 60;

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dandelion-sync-scheduler");
            t.setDaemon(true);
            return t;
        });

        DandelionSyncService syncService = new DandelionSyncService();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                syncService.processPendingQueue();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Unexpected error in Dandelion sync scheduler", ex);
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info("Dandelion sync scheduler started (interval=" + POLL_INTERVAL_SECONDS + "s).");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            LOGGER.info("Dandelion sync scheduler stopped.");
        }
    }
}