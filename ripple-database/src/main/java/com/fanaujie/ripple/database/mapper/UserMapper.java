package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserMapper {

    User findByAccount(String account);


    void insertUser(User user);

    void updateUser(User user);

    void deleteUser(String account);

    int countByAccount(String account);

    Long findUserIdByAccount(String account);

    void changePassword(@Param("account") String account, @Param("newPassword") String newPassword);
}