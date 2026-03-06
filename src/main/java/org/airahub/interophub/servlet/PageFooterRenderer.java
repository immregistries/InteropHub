package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;

final class PageFooterRenderer {
    private static final String VERSION = resolveVersion();
    private static final String APP_VERSION_PROPERTIES_PATH = "/interophub-version.properties";
    private static final String POM_PROPERTIES_PATH = "/META-INF/maven/org.airahub/interophub/pom.properties";

    private PageFooterRenderer() {
    }

    static void render(PrintWriter out) {
        out.println("  <footer class=\"legal-footer\">");
        out.println(
                "    <p><strong>American Immunization Registry Association (AIRA)</strong> supports collaboration, standards, and shared solutions that strengthen immunization information systems and improve the use of vaccination data to protect public health. <a href=\"https://www.immregistries.org/\">https://www.immregistries.org/</a></p>");
        out.println("    <p>InteropHub version " + VERSION
                + " &ndash; &copy; Copyright 2026, American Immunization Registry Association. All rights reserved. <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Privacy%20Policy%20-%20Final%202024_.pdf\">Privacy Policy</a> <a href=\"https://repository.immregistries.org/files/AIRA-Terms_of_Use_2024.pdf\">Terms of Use</a></p>");
        out.println("  </footer>");
    }

    private static String resolveVersion() {
        String appVersion = readVersionFromProperties(APP_VERSION_PROPERTIES_PATH, "software.version");
        if (appVersion != null) {
            return appVersion;
        }

        String pomVersion = readVersionFromProperties(POM_PROPERTIES_PATH, "version");
        if (pomVersion != null) {
            return pomVersion;
        }

        Package pkg = PageFooterRenderer.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
            return pkg.getImplementationVersion().trim();
        }

        return "unknown";
    }

    private static String readVersionFromProperties(String path, String key) {
        Properties properties = new Properties();
        try (InputStream in = PageFooterRenderer.class.getResourceAsStream(path)) {
            if (in != null) {
                properties.load(in);
                String version = properties.getProperty(key);
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (IOException ex) {
            return null;
        }
        return null;
    }
}