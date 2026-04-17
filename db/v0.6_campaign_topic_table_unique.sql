-- v0.6: Widen es_campaign_topic unique key to include table_no so that the
-- same topic can appear in multiple tables within the same campaign.
-- Required for the "tables per set" import feature.

ALTER TABLE es_campaign_topic
    DROP INDEX uq_es_campaign_topic,
    ADD UNIQUE KEY uq_es_campaign_topic (es_campaign_id, es_topic_id, table_no);
