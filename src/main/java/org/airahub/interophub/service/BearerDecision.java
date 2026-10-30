package org.airahub.interophub.service;

import java.util.Set;
import org.airahub.interophub.model.User;

public final class BearerDecision {
    private final User user;
    private final Long tokenId;
    private final String kind;
    private final Set<String> scopes;

    private BearerDecision(User user, Long tokenId, String kind, Set<String> scopes) {
        this.user = user;
        this.tokenId = tokenId;
        this.kind = kind;
        this.scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public static BearerDecision reject() {
        return new BearerDecision(null, null, null, Set.of());
    }

    public static BearerDecision accept(User user, Long tokenId, String kind, Set<String> scopes) {
        return new BearerDecision(user, tokenId, kind, scopes);
    }

    public boolean accepted() {
        return user != null && tokenId != null && kind != null;
    }

    public User getUser() {
        return user;
    }

    public Long getTokenId() {
        return tokenId;
    }

    public String getKind() {
        return kind;
    }

    public Set<String> getScopes() {
        return scopes;
    }
}
