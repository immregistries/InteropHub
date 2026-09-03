package org.airahub.interophub.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.service.digest.DailyDigestService;

/**
 * Polls every 15 minutes to check whether the InteropHub Daily Digest is due
 * (once per calendar day, after 6 AM server time); {@link DailyDigestService}
 * itself guards against double-firing.
 */
@WebListener
public class DailyDigestSchedulerListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(DailyDigestSchedulerListener.class.getName());

    private static final int POLL_INTERVAL_MINUTES = 15;

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "daily-digest-scheduler");
            t.setDaemon(true);
            return t;
        });

        DailyDigestService digestService = new DailyDigestService();

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                digestService.runIfDue();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Unexpected error in daily digest scheduler", ex);
            }
        }, POLL_INTERVAL_MINUTES, POLL_INTERVAL_MINUTES, TimeUnit.MINUTES);

        LOGGER.info("Daily digest scheduler started (interval=" + POLL_INTERVAL_MINUTES + "m).");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            LOGGER.info("Daily digest scheduler stopped.");
        }
    }
}
