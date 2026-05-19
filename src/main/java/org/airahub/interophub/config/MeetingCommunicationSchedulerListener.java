package org.airahub.interophub.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.service.MeetingCommunicationSendService;

/**
 * Starts a background scheduler that polls for due meeting communications
 * every 60 seconds and dispatches them.
 *
 * The {@code @WebListener} annotation registers this listener automatically;
 * it is also declared in web.xml for compatibility.
 */
@WebListener
public class MeetingCommunicationSchedulerListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(MeetingCommunicationSchedulerListener.class.getName());

    private static final int POLL_INTERVAL_SECONDS = 60;

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "meeting-comm-scheduler");
            t.setDaemon(true);
            return t;
        });

        MeetingCommunicationSendService sendService = new MeetingCommunicationSendService();

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                sendService.processDue();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE,
                        "Unexpected error in meeting communication scheduler", ex);
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info("Meeting communication scheduler started (interval=" + POLL_INTERVAL_SECONDS + "s).");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            LOGGER.info("Meeting communication scheduler stopped.");
        }
    }
}
