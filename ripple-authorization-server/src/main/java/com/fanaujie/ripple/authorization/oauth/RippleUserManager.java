package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.database.mapper.UserMapper;
import com.fanaujie.ripple.database.model.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class RippleUserManager implements UserDetailsManager {

    private final UserMapper userMapper;

    public RippleUserManager(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void createUser(UserDetails userDetails) {
        if (userDetails instanceof User) {
            userMapper.insertUser((User) userDetails);
            return;
        }
        throw new IllegalArgumentException("UserDetails must be an instance of User");
    }

    @Override
    public void updateUser(UserDetails userDetails) {
        User user = new User();
        user.setAccount(userDetails.getUsername());
        user.setPassword(userDetails.getPassword());
        user.setEnabled(userDetails.isEnabled());

        userMapper.updateUser(user);
    }

    @Override
    public void deleteUser(String account) {
        Long userId = userMapper.findUserIdByAccount(account);
        if (userId != null) {
            userMapper.deleteUser(account);
        }
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        // not implemented in this context
    }

    @Override
    public boolean userExists(String account) {
        return userMapper.countByAccount(account) > 0;
    }

    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        User user = userMapper.findByAccount(account);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + account);
        }
        return user;
    }
}
