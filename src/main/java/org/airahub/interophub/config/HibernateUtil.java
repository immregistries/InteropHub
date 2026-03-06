package org.airahub.interophub.config;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public final class HibernateUtil {
    private static final Logger LOGGER = Logger.getLogger(HibernateUtil.class.getName());
    private static final SessionFactory SESSION_FACTORY = buildSessionFactory();

    private HibernateUtil() {
    }

    private static SessionFactory buildSessionFactory() {
        try {
            SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
            LOGGER.info("Hibernate SessionFactory initialized successfully.");
            return sessionFactory;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Hibernate SessionFactory.", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return SESSION_FACTORY;
    }

    public static void shutdown() {
        LOGGER.info("Shutting down Hibernate SessionFactory.");
        getSessionFactory().close();
    }
}