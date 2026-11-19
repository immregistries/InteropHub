package org.airahub.interophub.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.lang.reflect.Method;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Releases what InteropHub leaves behind in Tomcat when the web application is
 * stopped or hot-redeployed, so the old class loader can be collected
 * Tomcat logged, for /hub on sabbia: the MySQL
 * driver still registered, and the threads mysql-cj-abandoned-connection-cleanup,
 * Hibernate Connection Pool Validation Thread, reactor-http-epoll-*,
 * boundedElastic-evictor-* and interophub-note-sse-keepalive still running.
 *
 * <p>Declared first in web.xml (and not annotated) so it is destroyed last,
 * after the scheduler listeners have stopped using the database.
 */
public class AppShutdownListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AppShutdownListener.class.getName());

    /** Optional libraries: class name and static no-argument method that stops their threads. */
    static final List<String[]> LIBRARY_SHUTDOWNS = List.of(
            new String[] { "com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", "checkedShutdown" },
            new String[] { "reactor.netty.http.HttpResources", "disposeLoopsAndConnections" },
            new String[] { "reactor.core.scheduler.Schedulers", "shutdownNow" });

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Nothing to start.
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ClassLoader webapp = AppShutdownListener.class.getClassLoader();
        step("Hibernate SessionFactory", HibernateUtil::shutdownIfInitialized);
        step("JDBC drivers", () -> LOGGER.info("Deregistered " + deregisterDrivers(webapp) + " JDBC driver(s)."));
        for (String[] library : LIBRARY_SHUTDOWNS) {
            step(library[0] + "." + library[1], () -> invokeStatic(library[0], library[1], webapp));
        }
    }

    /** Deregisters every JDBC driver that was loaded by {@code owner}; drivers from other loaders are left alone. */
    static int deregisterDrivers(ClassLoader owner) {
        int count = 0;
        for (Driver driver : Collections.list(DriverManager.getDrivers())) {
            if (driver.getClass().getClassLoader() != owner) {
                continue;
            }
            try {
                DriverManager.deregisterDriver(driver);
                count++;
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not deregister JDBC driver " + driver.getClass().getName(), ex);
            }
        }
        return count;
    }

    /** Calls a static no-argument method if the class is on the class path. @return false if the class is absent. */
    static boolean invokeStatic(String className, String methodName, ClassLoader loader) throws Exception {
        Class<?> type;
        try {
            type = Class.forName(className, true, loader);
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
        Method method = type.getMethod(methodName);
        method.invoke(null);
        return true;
    }

    private interface Step {
        void run() throws Exception;
    }

    /** Runs one cleanup step; a failure is logged and never stops the remaining steps. */
    private static void step(String name, Step step) {
        try {
            step.run();
        } catch (Exception | LinkageError ex) {
            LOGGER.log(Level.WARNING, "Shutdown step failed: " + name, ex);
        }
    }
}
