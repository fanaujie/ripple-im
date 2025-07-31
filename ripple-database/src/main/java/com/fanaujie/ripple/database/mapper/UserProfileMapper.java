package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserProfileMapper {

    UserProfile findByAccount(String account);

    void insertUserProfile(UserProfile userProfile);

    void updateUserProfile(UserProfile userProfile);

    void deleteUserProfile(String account);

    int countByAccount(String account);

    void updateNickName(@Param("account") String account, @Param("nickName") String nickName);

    void updateUserPortrait(@Param("account") String account, @Param("userPortrait") String userPortrait);
}