package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.UserProfileData;
import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
import com.fanaujie.ripple.database.mapper.UserProfileMapper;
import com.fanaujie.ripple.database.model.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;

    public ResponseEntity<UserProfileResponse> getUserProfile(long userId) {
        UserProfile userProfile = userProfileMapper.findById(userId);
        if (userProfile == null) {
            return ResponseEntity.status(401)
                    .body(new UserProfileResponse(401, "User profile not found", null));
        }
        UserProfileData userProfileData =
                new UserProfileData(
                        userProfile.getUserId(),
                        userProfile.getNickName(),
                        userProfile.getAvatar());
        return ResponseEntity.ok(new UserProfileResponse(200, "success", userProfileData));
    }

    @Transactional
    public ResponseEntity<CommonResponse> updateNickName(long userId, String nickName) {
        if (!userProfileExists(userId)) {
            return ResponseEntity.status(401)
                    .body(new CommonResponse(401, "User profile not found"));
        }
        userProfileMapper.updateNickName(userId, nickName);
        return ResponseEntity.ok(new CommonResponse(200, "success"));
    }

    @Transactional
    public ResponseEntity<CommonResponse> deleteAvatar(long userId) {
        if (!userProfileExists(userId)) {
            return ResponseEntity.status(401)
                    .body(new CommonResponse(401, "User profile not found"));
        }
        userProfileMapper.updateAvatar(userId, null);
        return ResponseEntity.ok(new CommonResponse(200, "success"));
    }

    private boolean userProfileExists(long userId) {
        return userProfileMapper.countById(userId) > 0;
    }
}
