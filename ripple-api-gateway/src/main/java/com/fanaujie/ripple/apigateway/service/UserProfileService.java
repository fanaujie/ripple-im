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

    public ResponseEntity<UserProfileResponse> getUserProfile(String account) {
        UserProfile userProfile = userProfileMapper.findByAccount(account);
        if (userProfile == null) {
            return ResponseEntity.status(401)
                    .body(new UserProfileResponse(401, "User profile not found", null));
        }
        UserProfileData userProfileData =
                new UserProfileData(
                        userProfile.getAccount(),
                        userProfile.getNickName(),
                        userProfile.getUserPortrait());
        return ResponseEntity.ok(new UserProfileResponse(200, "success", userProfileData));
    }

    @Transactional
    public ResponseEntity<CommonResponse> updateNickName(String account, String nickName) {
        if (!userProfileExists(account)) {
            return ResponseEntity.status(401)
                    .body(new CommonResponse(401, "User profile not found"));
        }
        userProfileMapper.updateNickName(account, nickName);
        return ResponseEntity.ok(new CommonResponse(200, "success"));
    }

    @Transactional
    public ResponseEntity<CommonResponse> deleteUserPortrait(String account) {
        if (!userProfileExists(account)) {
            return ResponseEntity.status(401)
                    .body(new CommonResponse(401, "User profile not found"));
        }
        userProfileMapper.updateUserPortrait(account, null);
        return ResponseEntity.ok(new CommonResponse(200, "success"));
    }

    private boolean userProfileExists(String account) {
        return userProfileMapper.countByAccount(account) > 0;
    }
}
