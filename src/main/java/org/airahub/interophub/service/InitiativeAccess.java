package org.airahub.interophub.service;

import org.airahub.interophub.model.EsInitiativeManager;

/**
 * Who can see and change an initiative. Global admins always have full access.
 * Everyone else needs a per-initiative manager role. Manager roles are separate
 * from member status.
 */
public final class InitiativeAccess {

    public enum Level {
        NONE,
        VIEW,
        EDIT,
        ADMIN
    }

    private InitiativeAccess() {
    }

    public static Level level(boolean globalAdmin, EsInitiativeManager.ManagerRole role) {
        if (globalAdmin) {
            return Level.ADMIN;
        }
        if (role == null) {
            return Level.NONE;
        }
        return switch (role) {
            case ADMIN -> Level.ADMIN;
            case EDITOR -> Level.EDIT;
            case VIEWER -> Level.VIEW;
        };
    }

    public static boolean canView(Level level) {
        return level != null && level != Level.NONE;
    }

    /** Members, organizations, import, comments, and the checklist. */
    public static boolean canEditRoster(Level level) {
        return level == Level.EDIT || level == Level.ADMIN;
    }

    public static boolean canEditChecklist(Level level) {
        return canEditRoster(level);
    }

    /** Small Groups use the same edit right as the roster and the checklist. */
    public static boolean canEditSmallGroup(Level level) {
        return canEditRoster(level);
    }

    public static boolean canManageTemplates(boolean globalAdmin) {
        return globalAdmin;
    }

    /** Initiative settings, managers, and links. */
    public static boolean canManageSettings(Level level) {
        return level == Level.ADMIN;
    }

    public static boolean canComment(Level level) {
        return canEditRoster(level);
    }
}
