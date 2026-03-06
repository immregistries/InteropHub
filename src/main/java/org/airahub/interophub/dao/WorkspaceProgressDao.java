package org.airahub.interophub.dao;

import org.airahub.interophub.model.WorkspaceProgress;

public class WorkspaceProgressDao extends GenericDao<WorkspaceProgress, Long> {
    public WorkspaceProgressDao() {
        super(WorkspaceProgress.class);
    }
}
