package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserProfileMapper {

    UserProfile findById(long userId);

    void insertUserProfile(UserProfile userProfile);

    void updateUserProfile(UserProfile userProfile);

    void deleteUserProfile(long userId);

    int countById(long userId);

    void updateNickName(@Param("userId") long userId, @Param("nickName") String nickName);

    void updateAvatar(@Param("userId") long userId, @Param("avatar") String avatar);
}
