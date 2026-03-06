package org.airahub.interophub.dao;

import org.airahub.interophub.model.WorkspaceEndpoint;

public class WorkspaceEndpointDao extends GenericDao<WorkspaceEndpoint, Long> {
    public WorkspaceEndpointDao() {
        super(WorkspaceEndpoint.class);
    }
}
