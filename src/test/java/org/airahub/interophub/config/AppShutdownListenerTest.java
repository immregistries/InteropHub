package org.airahub.interophub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class AppShutdownListenerTest {

    static final class FakeDriver implements Driver {
        public Connection connect(String url, Properties info) { return null; }
        public boolean acceptsURL(String url) { return url != null && url.startsWith("jdbc:fake-3976:"); }
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 1; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
    }

    static int calls;

    public static void countCall() {
        calls++;
    }

    private static boolean registered(Driver driver) {
        return Collections.list(DriverManager.getDrivers()).contains(driver);
    }

    @Test
    void deregistersOnlyDriversLoadedByTheWebappLoader() throws Exception {
        FakeDriver driver = new FakeDriver();
        DriverManager.registerDriver(driver);
        try (URLClassLoader other = new URLClassLoader(new URL[0], null)) {
            AppShutdownListener.deregisterDrivers(other);
            assertTrue(registered(driver), "a driver from another class loader must be left alone");
        }
        assertTrue(AppShutdownListener.deregisterDrivers(FakeDriver.class.getClassLoader()) >= 1);
        assertFalse(registered(driver));
    }

    @Test
    void shutdownDoesNotBuildHibernateWhenItWasNeverUsed() {
        // No INTEROPHUB_DB_* env vars are needed: an unused SessionFactory is not built just to be closed.
        boolean before = HibernateUtil.isInitialized();
        HibernateUtil.shutdownIfInitialized();
        assertEquals(before, HibernateUtil.isInitialized());
    }

    @Test
    void invokeStaticCallsPresentClassesAndSkipsMissingOnes() throws Exception {
        ClassLoader loader = AppShutdownListenerTest.class.getClassLoader();
        calls = 0;
        assertTrue(AppShutdownListener.invokeStatic(AppShutdownListenerTest.class.getName(), "countCall", loader));
        assertEquals(1, calls);
        assertFalse(AppShutdownListener.invokeStatic("com.example.NotOnTheClassPath3976", "shutdown", loader));
    }

    @Test
    void stopsTheLibraryThreadsTomcatReported() {
        List<String> names = AppShutdownListener.LIBRARY_SHUTDOWNS.stream().map(entry -> entry[0] + "." + entry[1]).toList();
        assertEquals(List.of(
                "com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown",
                "reactor.netty.http.HttpResources.disposeLoopsAndConnections",
                "reactor.core.scheduler.Schedulers.shutdownNow"), names);
        ClassLoader loader = AppShutdownListenerTest.class.getClassLoader();
        for (String[] entry : AppShutdownListener.LIBRARY_SHUTDOWNS) {
            assertTrue(hasPublicStaticNoArg(entry[0], entry[1], loader), entry[0] + "." + entry[1] + " must exist in the shipped jars");
        }
    }

    private static boolean hasPublicStaticNoArg(String className, String method, ClassLoader loader) {
        try {
            var m = Class.forName(className, false, loader).getMethod(method);
            return java.lang.reflect.Modifier.isStatic(m.getModifiers());
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    @Test
    void executorShutdownWaitsForTheThreadToEnd() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(ExecutorShutdown.stop(executor, "test", Logger.getGlobal()));
        assertTrue(executor.isTerminated());
    }
}
