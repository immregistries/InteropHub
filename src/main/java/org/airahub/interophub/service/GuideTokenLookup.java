package org.airahub.interophub.service;

public interface GuideTokenLookup {
    BearerDecision authenticate(String rawToken);
}
