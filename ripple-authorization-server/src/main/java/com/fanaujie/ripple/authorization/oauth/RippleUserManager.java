package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class RippleUserManager implements UserDetailsManager {

    private final UserRepository userStorage;

    public RippleUserManager(UserRepository userStorage) {
        this.userStorage = userStorage;
    }

    @Override
    public void createUser(UserDetails userDetails) {
        if (userDetails instanceof User user) {
            userStorage.insertUser(user, user.getAccount(), "");
            return;
        }
        throw new IllegalArgumentException("UserDetails must be an instance of User");
    }

    @Override
    public void updateUser(UserDetails userDetails) {
        // not implemented in this context
    }

    @Override
    public void deleteUser(String account) {
        // not implemented in this context
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        // not implemented in this context
    }

    @Override
    public boolean userExists(String account) {
        return userStorage.userExists(account);
    }

    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        User user = userStorage.findByAccount(account);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + account);
        }
        return user;
    }
}
