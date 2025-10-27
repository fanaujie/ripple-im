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
        log.debug("getUserProfile: Querying user profile for userId: {}", userId);
        try {
            UserProfile userProfile = this.userStorage.getUserProfile(userId);
            log.debug(
                    "getUserProfile: Found user profile - userId: {}, nickName: {}",
                    userId,
                    userProfile.getNickName());

            UserProfileData data = new UserProfileData();
            data.setUserId(String.valueOf(userProfile.getUserId()));
            data.setNickName(userProfile.getNickName());
            data.setAvatar(userProfile.getAvatar());

            log.debug("getUserProfile: UserProfileData constructed successfully");
            this.notifySelfInfoUpdate(userId);
            log.debug("getUserProfile: Returning user profile response for userId: {}", userId);
            return ResponseEntity.ok(new UserProfileResponse(200, "success", data));
        } catch (NotFoundUserProfileException e) {
            log.error("getUserProfile: User profile not found for userId: {}", userId);
            return ResponseEntity.status(404)
                    .body(new UserProfileResponse(404, "User profile not found", null));
        }
    }

    public ResponseEntity<CommonResponse> updateNickName(long userId, String nickName) {
        log.debug(
                "updateNickName: Updating nickName for userId: {}, new nickName: {}",
                userId,
                nickName);
        try {
            this.userStorage.updateNickNameByUserId(userId, nickName);
            log.debug("updateNickName: NickName updated successfully for userId: {}", userId);

            log.debug(
                    "updateNickName: Sending self info update notification for userId: {}", userId);
            this.notificationSender.sendSelfInfoUpdatedNotification(userId);
            log.debug(
                    "updateNickName: Self info update notification sent successfully for userId: {}",
                    userId);

            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            log.error("updateNickName: User profile not found for userId: {}", userId);
            return ResponseEntity.status(404)
                    .body(new CommonResponse(404, "User profile not found"));
        }
    }

    public ResponseEntity<CommonResponse> deleteAvatar(long userId) {
        log.debug("deleteAvatar: Deleting avatar for userId: {}", userId);
        try {
            this.userStorage.updateAvatarByUserId(userId, null);
            log.debug("deleteAvatar: Avatar deleted successfully for userId: {}", userId);

            log.debug("deleteAvatar: Sending self info update notification for userId: {}", userId);
            this.notificationSender.sendSelfInfoUpdatedNotification(userId);
            log.debug(
                    "deleteAvatar: Self info update notification sent successfully for userId: {}",
                    userId);

            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            log.error("deleteAvatar: User profile not found for userId: {}", userId);
            return ResponseEntity.status(404)
                    .body(new CommonResponse(404, "User profile not found"));
        }
    }

    private void notifySelfInfoUpdate(long userId) {}
}
