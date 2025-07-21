package com.fanaujie.ripple.apiserver.spring.mapper;

import com.fanaujie.ripple.apiserver.spring.model.mapper.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface UserMapper {

    User findByUsername(String username);

    List<String> getUserAuthorities(long userId);

    void insertUser(User user);

    void updateUser(User user);

    void deleteUser(String username);

    int countByUsername(String username);

    Long findUserIdByUsername(String username);

    void insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

    void deleteUserRoles(Long userId);

    Long getDefaultRoleId();

    boolean hasUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

    void changePassword(@Param("username") String username, @Param("newPassword") String newPassword);
}