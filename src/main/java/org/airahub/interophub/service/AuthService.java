package org.airahub.interophub.service;

import java.util.Optional;
import java.util.logging.Logger;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.User;

public class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    private final UserDao userDao;

    public AuthService() {
        this.userDao = new UserDao();
    }

    public Optional<User> findUserByEmail(String email) {
        LOGGER.info("Looking up user by email.");
        return userDao.findByEmail(email);
    }

    public User registerUser(String email, String firstName, String lastName, String organization, String roleTitle) {
        LOGGER.info("Registering a new placeholder user.");
        User user = new User();
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        user.setEmail(email == null ? null : email.trim());
        user.setEmailNormalized(normalizedEmail);
        user.setFirstName(firstName == null ? null : firstName.trim());
        user.setLastName(lastName == null ? null : lastName.trim());
        user.setOrganization(organization == null ? null : organization.trim());
        user.setRoleTitle(roleTitle == null ? null : roleTitle.trim());
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(Boolean.FALSE);
        return userDao.save(user);
    }
}
