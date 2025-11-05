package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;

import com.fanaujie.ripple.apigateway.dto.User;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RelationService {

    private static int MAX_PAGE_SIZE = 200;
    private static int MAX_SYNC_CHANGES = 200;
    private final RelationRepository relationRepository;
    private final UserRepository userRepository;
    private final MessageAPISender messageAPISender;

    public RelationService(
            RelationRepository relationRepository,
            UserRepository userRepository,
            MessageAPISender messageAPISender) {
        this.relationRepository = relationRepository;
        this.userRepository = userRepository;
        this.messageAPISender = messageAPISender;
    }

    public ResponseEntity<UserRelationsResponse> getRelations(
            long currentUserId, String nextPageToken, int pageSize) {
        // Validate pageSize
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(new UserRelationsResponse(400, "Invalid page size", null));
        }

        PagedRelationResult result =
                this.relationRepository.getRelations(currentUserId, nextPageToken, pageSize);
        List<User> users =
                result.getRelations().stream()
                        .map(
                                relation ->
                                        new User(
                                                String.valueOf(relation.getRelationUserId()),
                                                relation.getRelationNickName(),
                                                relation.getRelationAvatar(),
                                                relation.getRelationRemarkName(),
                                                relation.getRelationFlags() & 0xFF)) // byte to int
                        .collect(Collectors.toList());
        UserRelationsData data =
                new UserRelationsData(users, result.getNextPageToken(), result.isHasMore());

        return ResponseEntity.ok(new UserRelationsResponse(200, "success", data));
    }

    public ResponseEntity<CommonResponse> addFriend(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot add yourself as friend"));
        }
        Relation relation =
                this.relationRepository.getRelationBetweenUser(currentUserId, targetUserId);
        if (relation != null && RelationFlags.FRIEND.isSet(relation.getRelationFlags())) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is already your friend"));
        }
        try {
            UserProfile targetUserProfile = this.userRepository.getUserProfile(targetUserId);
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setRelationEvent(
                                    RelationEvent.newBuilder()
                                            .setEventType(RelationEvent.EventType.ADD_FRIEND)
                                            .setUserId(currentUserId)
                                            .setTargetUserId(targetUserId)
                                            .setTargetUserNickName(targetUserProfile.getNickName())
                                            .setTargetUserAvatar(targetUserProfile.getAvatar())
                                            .setTargetUserRemarkName(
                                                    targetUserProfile.getNickName())
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is already your friend"));
        } catch (Exception e) {
            log.error("addFriend: Error adding friend", e);
            return ResponseEntity.status(500).body(new CommonResponse(500, "Failed to add friend"));
        }
    }

    public ResponseEntity<CommonResponse> removeFriend(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot remove yourself as friend"));
        }
        Relation relation =
                this.relationRepository.getRelationBetweenUser(currentUserId, targetUserId);
        if (relation == null) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not your friend"));
        }
        try {
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setRelationEvent(
                                    RelationEvent.newBuilder()
                                            .setEventType(RelationEvent.EventType.REMOVE_FRIEND)
                                            .setUserId(currentUserId)
                                            .setTargetUserId(targetUserId)
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Failed to remove friend"));
        }
    }

    public ResponseEntity<CommonResponse> updateFriendRemarkName(
            long currentUserId, long targetUserId, String remarkName) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot update display name for yourself"));
        }
        Relation relation =
                this.relationRepository.getRelationBetweenUser(currentUserId, targetUserId);
        if (relation == null) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not your friend"));
        }
        try {
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setRelationEvent(
                                    RelationEvent.newBuilder()
                                            .setEventType(
                                                    RelationEvent.EventType
                                                            .UPDATE_FRIEND_REMARK_NAME)
                                            .setUserId(currentUserId)
                                            .setTargetUserId(targetUserId)
                                            .setTargetUserRemarkName(remarkName)
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Failed to update friend's display name"));
        }
    }

    public ResponseEntity<CommonResponse> addBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot block yourself"));
        }
        Relation betweenUserRelation =
                this.relationRepository.getRelationBetweenUser(currentUserId, targetUserId);
        try {
            RelationEvent.Builder eventBuilder = RelationEvent.newBuilder();
            eventBuilder.setEventType(RelationEvent.EventType.BLOCK_FRIEND);
            if (betweenUserRelation == null) {
                // block user is strange user, need to create relation record
                eventBuilder.setEventType(RelationEvent.EventType.BLOCK_STRANGER);
                UserProfile blockedUserProfile = this.userRepository.getUserProfile(targetUserId);
                betweenUserRelation = new Relation();
                betweenUserRelation.setSourceUserId(currentUserId);
                betweenUserRelation.setRelationUserId(targetUserId);
                betweenUserRelation.setRelationNickName(blockedUserProfile.getNickName());
                betweenUserRelation.setRelationAvatar(blockedUserProfile.getAvatar());
                betweenUserRelation.setRelationRemarkName(blockedUserProfile.getNickName());
            } else {
                if (RelationFlags.BLOCKED.isSet(betweenUserRelation.getRelationFlags())) {
                    return ResponseEntity.badRequest()
                            .body(new CommonResponse(400, "Target user is already blocked"));
                }
            }
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setRelationEvent(
                                    eventBuilder
                                            .setUserId(currentUserId)
                                            .setTargetUserId(targetUserId)
                                            .setTargetUserNickName(
                                                    betweenUserRelation.getRelationNickName())
                                            .setTargetUserAvatar(
                                                    betweenUserRelation.getRelationAvatar())
                                            .setTargetUserRemarkName(
                                                    betweenUserRelation.getRelationRemarkName())
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user profile not found"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Failed to block target user"));
        }
    }

    public ResponseEntity<CommonResponse> removeBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot unblock yourself"));
        }
        Relation betweenUserRelation =
                this.relationRepository.getRelationBetweenUser(currentUserId, targetUserId);
        if (betweenUserRelation == null) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not related to you"));
        }
        if (!RelationFlags.BLOCKED.isSet(betweenUserRelation.getRelationFlags())) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not blocked"));
        }
        try {
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setRelationEvent(
                                    RelationEvent.newBuilder()
                                            .setEventType(RelationEvent.EventType.UNBLOCK_USER)
                                            .setUserId(currentUserId)
                                            .setTargetUserId(targetUserId)
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Failed to unblock user"));
        }
    }

    public ResponseEntity<CommonResponse> hideBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot hide yourself"));
        }
        Relation betweenUserRelation =
                this.relationRepository.getRelationBetweenUser(currentUserId, targetUserId);
        if (betweenUserRelation == null) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not related to you"));
        }
        if (!RelationFlags.BLOCKED.isSet(betweenUserRelation.getRelationFlags())) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not blocked"));
        }
        try {
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setRelationEvent(
                                    RelationEvent.newBuilder()
                                            .setEventType(RelationEvent.EventType.HIDE_BLOCKED_USER)
                                            .setUserId(currentUserId)
                                            .setTargetUserId(targetUserId)
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Failed to hide blocked user"));
        }
    }

    public ResponseEntity<RelationSyncResponse> syncRelations(long currentUserId, String version) {
        // If version is null, or empty require full sync
        if (version == null || version.isEmpty()) {
            RelationSyncData data = new RelationSyncData(true, null, new ArrayList<>());
            return ResponseEntity.ok(new RelationSyncResponse(200, "success", data));
        }

        try {
            // Query changes with max batch size
            List<RelationVersionChange> records =
                    this.relationRepository.getRelationChanges(
                            currentUserId, version, MAX_SYNC_CHANGES);

            // Convert records to RelationChange DTOs
            List<RelationChange> changes =
                    records.stream()
                            .map(
                                    record -> {
                                        return new RelationChange(
                                                record.getVersion(),
                                                record.getOperation(),
                                                String.valueOf(record.getRelationUserId()),
                                                record.getNickName(),
                                                record.getAvatar(),
                                                record.getRemarkName(),
                                                record.getRelationFlags() != null
                                                        ? record.getRelationFlags() & 0xFF
                                                        : 0);
                                    })
                            .collect(Collectors.toList());

            // Get latest version (from last record) for next batch sync
            String latestVersion =
                    records.isEmpty() ? version : records.get(records.size() - 1).getVersion();

            RelationSyncData data = new RelationSyncData(false, latestVersion, changes);
            return ResponseEntity.ok(new RelationSyncResponse(200, "success", data));
        } catch (InvalidVersionException e) {
            return ResponseEntity.badRequest()
                    .body(new RelationSyncResponse(400, e.getMessage(), null));
        }
    }

    public ResponseEntity<RelationVersionResponse> getLatestVersion(long currentUserId) {
        String latestVersion = this.relationRepository.getLatestRelationVersion(currentUserId);

        if (latestVersion == null) {
            return ResponseEntity.ok(
                    new RelationVersionResponse(200, "success", new RelationVersionData(null)));
        }

        RelationVersionData data = new RelationVersionData(latestVersion);
        return ResponseEntity.ok(new RelationVersionResponse(200, "success", data));
    }
}
