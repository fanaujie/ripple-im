package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class RippleUserManager implements UserDetailsManager {

    private final RippleStorageFacade storageFacade;

    public RippleUserManager(RippleStorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @Override
    public void createUser(UserDetails userDetails) {
        if (userDetails instanceof User user) {
            storageFacade.insertUser(user, user.getAccount(), null);
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
        return storageFacade.userExists(account);
    }

    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        User user = storageFacade.findByAccount(account);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + account);
        }
        return user;
    }
}
