package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.UserProfileData;
import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserProfileService {

    private final RippleStorageFacade storageFacade;
    private final MessageAPISender messageAPISender;

    public UserProfileService(
            RippleStorageFacade storageFacade, MessageAPISender messageAPISender) {
        this.storageFacade = storageFacade;
        this.messageAPISender = messageAPISender;
    }

    public ResponseEntity<UserProfileResponse> getUserProfile(long userId) {
        try {
            UserProfile userProfile = this.storageFacade.getUserProfile(userId);
            UserProfileData data = new UserProfileData();
            data.setUserId(String.valueOf(userProfile.getUserId()));
            data.setNickName(userProfile.getNickName());
            data.setAvatar(userProfile.getAvatar());
            return ResponseEntity.ok(new UserProfileResponse(200, "success", data));
        } catch (NotFoundUserProfileException e) {
            log.error("getUserProfile: User profile not found for userId: {}", userId);
            return ResponseEntity.status(404)
                    .body(new UserProfileResponse(404, "User profile not found", null));
        }
    }

    public ResponseEntity<CommonResponse> updateNickName(long userId, String nickName) {
        try {
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setSelfInfoUpdateEvent(
                                    SelfInfoUpdateEvent.newBuilder()
                                            .setEventType(
                                                    SelfInfoUpdateEvent.EventType.UPDATE_NICK_NAME)
                                            .setUserId(userId)
                                            .setNickName(nickName)
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            log.error("updateNickName: Error updating nickName for userId: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Failed to update nickName"));
        }
    }

    public ResponseEntity<CommonResponse> deleteAvatar(long userId) {
        try {
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setSelfInfoUpdateEvent(
                                    SelfInfoUpdateEvent.newBuilder()
                                            .setEventType(
                                                    SelfInfoUpdateEvent.EventType.DELETE_AVATAR)
                                            .setUserId(userId)
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            log.error("deleteAvatar: Error deleting avatar for userId: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Failed to delete avatar"));
        }
    }
}
