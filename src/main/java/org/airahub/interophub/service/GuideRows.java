package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** In-memory shapes shared by the guide services and their stores. */
public final class GuideRows {
    private GuideRows() {
    }

    public static final class Person {
        final long userId;
        final String displayName;
        final String organization;

        Person(long userId, String displayName, String organization) {
            this.userId = userId;
            this.displayName = displayName;
            this.organization = organization;
        }
    }

    public static final class Disposition {
        final String code;
        final String label;

        Disposition(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    public static final class UrlBinding {
        public String url;
        public String label;
        int sortOrder;
        Long allowId;
        boolean ownsAllowlist;
        boolean insertNew;
        boolean insertEnabled = true;

        UrlBinding() {
        }

        UrlBinding(String url, String label) {
            this.url = url;
            this.label = label;
        }
    }

    public static final class TopicRow {
        public long id;
        public String code;
        public String name;
        public String visibility;
    }

    public static final class DocumentRow {
        Long id;
        public String documentKey;
        long topicId;
        public String title;
        public String repositoryUrl;
        public boolean active;
        long createdByUserId;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;
        public List<UrlBinding> urls = new ArrayList<>();
    }

    public static final class AllowRow {
        Long allowId;
        String baseUrl;
        boolean enabled;
    }

    public static final class CommentRow {
        Long id;
        long documentId;
        Long parentId;
        String anchor;
        Integer blockIndex;
        String snippet;
        String text;
        String state;
        String dispositionCode;
        String changeNote;
        long userId;
        String sourceNote;
        LocalDateTime createdAt;
        LocalDateTime editedAt;
        LocalDateTime updatedAt;
        LocalDateTime deletedAt;
        Long deletedByUserId;
        LocalDateTime withdrawnAt;
        LocalDateTime disposedAt;
        Long disposedByUserId;
        LocalDateTime closedAt;
        Long closedByUserId;

        boolean deleted() {
            return deletedAt != null;
        }

        boolean withdrawn() {
            return withdrawnAt != null || "WITHDRAWN".equals(state);
        }
    }

    public static final class AuditRow {
        long userId;
        Long tokenId;
        LocalDateTime at;
        String field;
        String oldValue;
        String newValue;
    }

    public static final class FlagRow {
        long id;
        String anchor;
        String note;
        long createdByUserId;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;
    }

    public static final class AllowInsert {
        final String url;
        final boolean enabled;

        AllowInsert(String url, boolean enabled) {
            this.url = url;
            this.enabled = enabled;
        }
    }

    public static final class AllowToggle {
        final long allowId;
        final boolean enabled;

        AllowToggle(long allowId, boolean enabled) {
            this.allowId = allowId;
            this.enabled = enabled;
        }
    }

    public static final class RegistrationPlan {
        boolean creating;
        DocumentRow document;
        List<UrlBinding> urls = new ArrayList<>();
        List<AllowInsert> inserts = new ArrayList<>();
        List<AllowToggle> toggles = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        String changesJson;
        long topicId;
        long userId;
        Long tokenId;
        String source;
    }
}
