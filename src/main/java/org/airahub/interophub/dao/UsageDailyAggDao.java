package org.airahub.interophub.dao;

import org.airahub.interophub.model.UsageDailyAgg;
import org.airahub.interophub.model.UsageDailyAggId;

public class UsageDailyAggDao extends GenericDao<UsageDailyAgg, UsageDailyAggId> {
    public UsageDailyAggDao() {
        super(UsageDailyAgg.class);
    }
}
