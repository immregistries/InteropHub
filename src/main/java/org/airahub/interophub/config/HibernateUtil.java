package org.airahub.interophub.config;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public final class HibernateUtil {
    private static final String ENV_DB_DRIVER = "INTEROPHUB_DB_DRIVER";
    private static final String ENV_DRIVER = "INTEROPHUB_DRIVER";
    private static final String ENV_DB_URL = "INTEROPHUB_DB_URL";
    private static final String ENV_DB_USER = "INTEROPHUB_DB_USER";
    private static final String ENV_USER = "INTEROPHUB_USER";
    private static final String ENV_DB_PASSWORD = "INTEROPHUB_DB_PASSWORD";
    private static final String ENV_PASSWORD = "INTEROPHUB_PASSWORD";

    private static final Logger LOGGER = Logger.getLogger(HibernateUtil.class.getName());
    private static final SessionFactory SESSION_FACTORY = buildSessionFactory();

    private HibernateUtil() {
    }

    private static SessionFactory buildSessionFactory() {
        try {
            Configuration configuration = new Configuration().configure();
            applyDatabaseOverrides(configuration);
            SessionFactory sessionFactory = configuration.buildSessionFactory();
            LOGGER.info("Hibernate SessionFactory initialized successfully.");
            return sessionFactory;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Hibernate SessionFactory.", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void applyDatabaseOverrides(Configuration configuration) {
        configuration.setProperty(
                "hibernate.connection.driver_class",
                getEnvironmentVariableOrDefault(
                        ENV_DB_DRIVER,
                        ENV_DRIVER,
                        configuration.getProperty("hibernate.connection.driver_class")));
        configuration.setProperty(
                "hibernate.connection.url",
                getEnvironmentVariableOrDefault(
                        ENV_DB_URL,
                        null,
                        configuration.getProperty("hibernate.connection.url")));
        configuration.setProperty(
                "hibernate.connection.username",
                requireEnvironmentVariable(ENV_DB_USER, ENV_USER));
        configuration.setProperty(
                "hibernate.connection.password",
                requireEnvironmentVariable(ENV_DB_PASSWORD, ENV_PASSWORD));
    }

    private static String getEnvironmentVariableOrDefault(String envVarName, String aliasEnvVarName,
            String defaultValue) {
        String value = getEnvironmentVariable(envVarName);
        if (value != null) {
            return value;
        }

        if (aliasEnvVarName != null) {
            value = getEnvironmentVariable(aliasEnvVarName);
            if (value != null) {
                LOGGER.log(Level.INFO, "Using environment variable {0} for Hibernate configuration.", aliasEnvVarName);
                return value;
            }
        }

        LOGGER.log(Level.WARNING, "Environment variable {0}{1} is not set. Falling back to configured default.",
                new Object[] {
                        envVarName,
                        aliasEnvVarName == null ? "" : " (or " + aliasEnvVarName + ")"
                });
        return defaultValue;
    }

    private static String requireEnvironmentVariable(String envVarName, String aliasEnvVarName) {
        String value = getEnvironmentVariable(envVarName);
        if (value != null) {
            return value;
        }

        if (aliasEnvVarName != null) {
            value = getEnvironmentVariable(aliasEnvVarName);
            if (value != null) {
                LOGGER.log(Level.INFO, "Using environment variable {0} for Hibernate configuration.", aliasEnvVarName);
                return value;
            }
        }

        String message = String.format("Required environment variable %s%s is not set.",
                envVarName,
                aliasEnvVarName == null ? "" : " (or " + aliasEnvVarName + ")");
        LOGGER.severe(message);
        throw new IllegalStateException(message);
    }

    private static String getEnvironmentVariable(String envVarName) {
        String value = System.getenv(envVarName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public static SessionFactory getSessionFactory() {
        return SESSION_FACTORY;
    }

    public static void shutdown() {
        LOGGER.info("Shutting down Hibernate SessionFactory.");
        getSessionFactory().close();
    }
}