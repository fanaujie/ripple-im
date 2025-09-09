package com.fanaujie.ripple.database.service;

import com.fanaujie.ripple.database.mapper.UserMapper;
import com.fanaujie.ripple.database.model.User;
import org.springframework.stereotype.Service;

@Service
public class UserStorageImpl implements IUserStorage {

    private final UserMapper userMapper;

    public UserStorageImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User findByAccount(String account) {
        return userMapper.findByAccount(account);
    }

    @Override
    public void insertUser(User user) {
        userMapper.insertUser(user);
    }

    @Override
    public void updateUser(User user) {
        userMapper.updateUser(user);
    }

    @Override
    public boolean userExists(String account) {
        return userMapper.countByAccount(account) > 0;
    }
}
