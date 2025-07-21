package com.fanaujie.ripple.apiserver.spring.oauth;

import com.fanaujie.ripple.apiserver.spring.mapper.UserMapper;
import com.fanaujie.ripple.apiserver.spring.model.mapper.User;
import org.springframework.security.core.context.SecurityContextHolder;
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
        User user = new User();
        user.setUsername(userDetails.getUsername());
        user.setPassword(userDetails.getPassword());
        user.setEnabled(userDetails.isEnabled());

        userMapper.insertUser(user);

        Long defaultRoleId = userMapper.getDefaultRoleId();
        if (defaultRoleId != null) {
            userMapper.insertUserRole(user.getId(), defaultRoleId);
        }
    }

    @Override
    public void updateUser(UserDetails userDetails) {
        User user = new User();
        user.setUsername(userDetails.getUsername());
        user.setPassword(userDetails.getPassword());
        user.setEnabled(userDetails.isEnabled());

        userMapper.updateUser(user);
    }

    @Override
    public void deleteUser(String username) {
        Long userId = userMapper.findUserIdByUsername(username);
        if (userId != null) {
            userMapper.deleteUserRoles(userId);
            userMapper.deleteUser(username);
        }
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        UserDetails currentUser = loadUserByUsername(getCurrentUsername());
        if (!currentUser.getPassword().equals(oldPassword)) {
            throw new IllegalArgumentException("Old password is incorrect");
        }
        userMapper.changePassword(currentUser.getUsername(), newPassword);
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext()
                .getAuthentication().getName();
    }

    @Override
    public boolean userExists(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return user;
    }
}
