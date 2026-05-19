package org.airahub.interophub.model;

/**
 * Summary of how many recipients fall into each group for a single
 * communication. In-memory value object.
 */
public class CommunicationRecipientGroupSummary {

    private final RecipientGroup group;
    private final int count;

    public CommunicationRecipientGroupSummary(RecipientGroup group, int count) {
        this.group = group;
        this.count = count;
    }

    public RecipientGroup getGroup() {
        return group;
    }

    public int getCount() {
        return count;
    }
}
