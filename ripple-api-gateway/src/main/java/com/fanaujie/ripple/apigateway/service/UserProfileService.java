package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.UserProfileData;
import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
import com.fanaujie.ripple.database.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.service.IUserProfileStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final IUserProfileStorage userProfileStorage;

    public UserProfileService(IUserProfileStorage userProfileStorage) {
        this.userProfileStorage = userProfileStorage;
    }

    public ResponseEntity<UserProfileResponse> getUserProfile(long userId) {
        try {
            UserProfile userProfile = this.userProfileStorage.getUserProfile(userId);
            UserProfileData data = new UserProfileData();
            data.setUserId(String.valueOf(userProfile.getUserId()));
            data.setNickName(userProfile.getNickName());
            data.setAvatar(userProfile.getAvatar());
            return ResponseEntity.ok(new UserProfileResponse(200, "success", data));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.status(404)
                    .body(new UserProfileResponse(404, "User profile not found", null));
        }
    }

    public ResponseEntity<CommonResponse> updateNickName(long userId, String nickName) {
        try {
            this.userProfileStorage.updateNickNameByUserId(userId, nickName);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.status(404)
                    .body(new CommonResponse(404, "User profile not found"));
        }
    }

    public ResponseEntity<CommonResponse> deleteAvatar(long userId) {
        try {
            this.userProfileStorage.updateAvatarByUserId(userId, null);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.status(404)
                    .body(new CommonResponse(404, "User profile not found"));
        }
    }
}
