package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class InteropVersionResolver {
    private static final String APP_VERSION_PROPERTIES_PATH = "/interophub-version.properties";
    private static final String POM_PROPERTIES_PATH = "/META-INF/maven/org.airahub/interophub/pom.properties";
    private static final String VERSION = resolveVersion();

    private InteropVersionResolver() {
    }

    static String getApplicationVersion() {
        return VERSION;
    }

    private static String resolveVersion() {
        String appVersion = readVersionFromProperties(APP_VERSION_PROPERTIES_PATH, "software.version");
        if (appVersion != null && !appVersion.startsWith("${")) {
            return appVersion;
        }

        String pomVersion = readVersionFromProperties(POM_PROPERTIES_PATH, "version");
        if (pomVersion != null) {
            return pomVersion;
        }

        Package pkg = InteropVersionResolver.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
            return pkg.getImplementationVersion().trim();
        }

        return "development";
    }

    private static String readVersionFromProperties(String path, String key) {
        Properties properties = new Properties();
        try (InputStream in = InteropVersionResolver.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            properties.load(in);
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } catch (IOException ex) {
            return null;
        }
    }
}