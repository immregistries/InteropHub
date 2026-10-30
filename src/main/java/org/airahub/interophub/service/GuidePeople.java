package org.airahub.interophub.service;

import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.Person;

final class GuidePeople {
    private GuidePeople() {
    }

    static Person of(User user) {
        if (user == null || user.getUserId() == null) {
            return null;
        }
        String name = user.getFullName();
        return new Person(user.getUserId(), name == null ? "" : name, user.getOrganization());
    }
}
