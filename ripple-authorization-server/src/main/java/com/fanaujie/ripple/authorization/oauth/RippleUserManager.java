package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.database.model.User;
import com.fanaujie.ripple.database.service.IUserStorage;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class RippleUserManager implements UserDetailsManager {

    private final IUserStorage userStorage;

    public RippleUserManager(IUserStorage userStorage) {
        this.userStorage = userStorage;
    }

    @Override
    public void createUser(UserDetails userDetails) {
        if (userDetails instanceof User) {
            userStorage.insertUser((User) userDetails);
            return;
        }
        throw new IllegalArgumentException("UserDetails must be an instance of User");
    }

    @Override
    public void updateUser(UserDetails userDetails) {
        User user = new User();
        user.setAccount(userDetails.getUsername());
        user.setPassword(userDetails.getPassword());
        user.setRole(userDetails.getAuthorities().toString());
        userStorage.updateUser(user);
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
