package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserProfileMapper {

    UserProfile findByUserId(long userId);

    void insertUserProfile(UserProfile userProfile);

    int updateNickName(@Param("userId") long userId, @Param("nickName") String nickName);

    int updateAvatar(@Param("userId") long userId, @Param("avatar") String avatar);

    int updateStatus(@Param("userId") long userId, @Param("status") byte status);

    int countByUserId(long userId);
}
