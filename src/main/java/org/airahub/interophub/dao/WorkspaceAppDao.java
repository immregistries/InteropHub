package org.airahub.interophub.dao;

import org.airahub.interophub.model.WorkspaceApp;

public class WorkspaceAppDao extends GenericDao<WorkspaceApp, Long> {
    public WorkspaceAppDao() {
        super(WorkspaceApp.class);
    }
}
