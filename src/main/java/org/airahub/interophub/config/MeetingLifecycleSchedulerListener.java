package org.airahub.interophub.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.service.MeetingMaintenanceService;

@WebListener
public class MeetingLifecycleSchedulerListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(MeetingLifecycleSchedulerListener.class.getName());
    private static final int POLL_INTERVAL_SECONDS = 60;

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "meeting-lifecycle-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        MeetingMaintenanceService maintenanceService = new MeetingMaintenanceService();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                maintenanceService.runDueMaintenance();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Unexpected error in meeting lifecycle scheduler", ex);
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info("Meeting lifecycle scheduler started (interval=" + POLL_INTERVAL_SECONDS + "s).");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            LOGGER.info("Meeting lifecycle scheduler stopped.");
        }
    }
}