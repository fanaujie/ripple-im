package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.authorization.mapper.UserMapper;
import com.fanaujie.ripple.authorization.model.mapper.User;
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
    public void deleteUser(String username) {
        Long userId = userMapper.findUserIdByAccount(username);
        if (userId != null) {
            userMapper.deleteUser(username);
        }
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        // not implemented in this context
    }


    @Override
    public boolean userExists(String username) {
        return userMapper.countByAccount(username) > 0;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByAccount(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return user;
    }
}
