package org.airahub.interophub.dao;

import org.airahub.interophub.model.UserToken;

public class UserTokenDao extends GenericDao<UserToken, Long> {
    public UserTokenDao() {
        super(UserToken.class);
    }
}
