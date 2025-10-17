package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.UserProfileData;
import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
import com.fanaujie.ripple.apigateway.sender.NotificationSender;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserProfileService {

    private final UserRepository userStorage;
    private final NotificationSender notificationSender;

    public UserProfileService(UserRepository userStorage, NotificationSender notificationSender) {
        this.userStorage = userStorage;
        this.notificationSender = notificationSender;
    }

    public ResponseEntity<UserProfileResponse> getUserProfile(long userId) {
        try {
            UserProfile userProfile = this.userStorage.getUserProfile(userId);
            UserProfileData data = new UserProfileData();
            data.setUserId(String.valueOf(userProfile.getUserId()));
            data.setNickName(userProfile.getNickName());
            data.setAvatar(userProfile.getAvatar());
            this.notifySelfInfoUpdate(userId);
            return ResponseEntity.ok(new UserProfileResponse(200, "success", data));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.status(404)
                    .body(new UserProfileResponse(404, "User profile not found", null));
        }
    }

    public ResponseEntity<CommonResponse> updateNickName(long userId, String nickName) {
        try {
            this.userStorage.updateNickNameByUserId(userId, nickName);
            this.notificationSender.sendSelfInfoUpdatedNotification(userId);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.status(404)
                    .body(new CommonResponse(404, "User profile not found"));
        }
    }

    public ResponseEntity<CommonResponse> deleteAvatar(long userId) {
        try {
            this.userStorage.updateAvatarByUserId(userId, null);
            this.notificationSender.sendSelfInfoUpdatedNotification(userId);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.status(404)
                    .body(new CommonResponse(404, "User profile not found"));
        }
    }

    private void notifySelfInfoUpdate(long userId) {}
}
