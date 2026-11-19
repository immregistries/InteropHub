package org.airahub.interophub.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stops an executor and waits briefly so its thread is gone before Tomcat checks for leaks. */
final class ExecutorShutdown {
    static final long WAIT_SECONDS = 10;

    private ExecutorShutdown() {
    }

    /** @return true if the executor terminated within the wait. */
    static boolean stop(ExecutorService executor, String name, Logger logger) {
        if (executor == null) {
            return true;
        }
        executor.shutdownNow();
        try {
            if (executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)) {
                return true;
            }
            logger.log(Level.WARNING, "{0} did not stop within {1} seconds.", new Object[] { name, WAIT_SECONDS });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}
