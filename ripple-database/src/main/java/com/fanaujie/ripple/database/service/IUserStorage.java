package com.fanaujie.ripple.database.service;

import com.fanaujie.ripple.database.model.User;

public interface IUserStorage {

    User findByAccount(String account);

    void insertUser(User user);

    void updateUser(User user);

    boolean userExists(String account);
}
