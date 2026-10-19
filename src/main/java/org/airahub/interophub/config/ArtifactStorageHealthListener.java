package org.airahub.interophub.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.service.ArtifactBlobStorageService;

/**
 * Logs the artifact Blob write path state once at startup so a missing managed
 * identity or role assignment is visible in the Tomcat log before the first
 * upload is attempted. A failed probe must never prevent startup.
 *
 * <p>The {@code @WebListener} annotation registers this listener automatically;
 * it is also declared in web.xml for compatibility.
 */
@WebListener
public class ArtifactStorageHealthListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(ArtifactStorageHealthListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ArtifactBlobStorageService blob = new ArtifactBlobStorageService();
            report(blob.environment(), blob.isWriteEnabled());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "artifact write path: probe failed; uploads disabled.", ex);
        }
    }

    /**
     * Logs the write path state. A failed environment check is reported as
     * such, so an operator is not told the host is a development machine when
     * the check itself is what failed.
     */
    static void report(ArtifactBlobStorageService.Environment environment, boolean writeEnabled) {
        if (environment == ArtifactBlobStorageService.Environment.UNKNOWN) {
            LOGGER.warning("artifact write path: disabled (environment check failed). "
                    + "The deployment environment could not be determined; "
                    + "uploads stay disabled until it can be.");
        } else if (environment == ArtifactBlobStorageService.Environment.DEVELOPMENT) {
            LOGGER.info("artifact write path: disabled (development mode).");
        } else if (writeEnabled) {
            LOGGER.info("artifact write path: enabled (managed identity available).");
        } else {
            LOGGER.warning("artifact write path: disabled (managed identity unavailable). "
                    + "Check the VM identity and its Storage Blob Data Contributor assignment.");
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
