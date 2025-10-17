package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;

import com.fanaujie.ripple.apigateway.sender.NotificationSender;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.PagedRelationResult;
import com.fanaujie.ripple.storage.model.RelationOperation;
import com.fanaujie.ripple.storage.model.RelationVersionRecord;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RelationService {

    private static int MAX_PAGE_SIZE = 200;
    private static int MAX_SYNC_CHANGES = 200;
    private final RelationRepository relationRepository;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;

    public RelationService(
            RelationRepository relationRepository,
            UserRepository userRepository,
            NotificationSender notificationSender) {
        this.relationRepository = relationRepository;
        this.userRepository = userRepository;
        this.notificationSender = notificationSender;
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

        try {
            UserProfile targetUserProfile = this.userRepository.getUserProfile(targetUserId);
            this.relationRepository.addFriend(currentUserId, targetUserProfile);
            this.notificationSender.sendFriendNotification(
                    currentUserId, targetUserId, RelationEvent.EventType.ADD_FRIEND);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user profile not found"));
        } catch (RelationAlreadyExistsException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is already your friend"));
        }
    }

    public ResponseEntity<CommonResponse> removeFriend(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot remove yourself as friend"));
        }
        if (!this.userRepository.userIdExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            this.relationRepository.removeFriend(currentUserId, targetUserId);
            this.notificationSender.sendFriendNotification(
                    currentUserId, targetUserId, RelationEvent.EventType.REMOVE_FRIEND);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not friend"));
        }
    }

    public ResponseEntity<CommonResponse> updateFriendRemarkName(
            long currentUserId, long targetUserId, String remarkName) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot update display name for yourself"));
        }
        if (!this.userRepository.userIdExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            this.relationRepository.updateRelationRemarkName(
                    currentUserId, targetUserId, remarkName);
            this.notificationSender.sendFriendNotification(
                    currentUserId, targetUserId, RelationEvent.EventType.UPDATE_FRIEND);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not friend"));
        }
    }

    public ResponseEntity<CommonResponse> addBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot block yourself"));
        }
        boolean isFriend = this.relationRepository.isFriends(currentUserId, targetUserId);
        try {
            UserProfile targetUserProfile = this.userRepository.getUserProfile(targetUserId);
            this.relationRepository.addBlock(
                    currentUserId, targetUserId, isFriend, targetUserProfile);
            this.notificationSender.sendBlockNotification(
                    currentUserId, targetUserId, RelationEvent.EventType.BLOCK_USER);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user profile not found"));
        } catch (BlockAlreadyExistsException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is already blocked"));
        }
    }

    public ResponseEntity<CommonResponse> removeBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot unblock yourself"));
        }
        if (!this.userRepository.userIdExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            this.relationRepository.removeBlock(currentUserId, targetUserId);
            this.notificationSender.sendBlockNotification(
                    currentUserId, targetUserId, RelationEvent.EventType.UNBLOCK_USER);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundBlockException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not blocked"));
        }
    }

    public ResponseEntity<CommonResponse> hideBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot hide yourself"));
        }
        if (!this.userRepository.userIdExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            this.relationRepository.hideBlock(currentUserId, targetUserId);
            this.notificationSender.sendBlockNotification(
                    currentUserId, targetUserId, RelationEvent.EventType.HIDE_BLOCKED_USER);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundBlockException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user is not blocked"));
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
            List<RelationVersionRecord> records =
                    this.relationRepository.getRelationChanges(
                            currentUserId, version, MAX_SYNC_CHANGES);

            // Convert records to RelationChange DTOs
            List<RelationChange> changes =
                    records.stream()
                            .map(
                                    record -> {
                                        String operation =
                                                RelationOperation.fromValue(record.getOperation())
                                                        .name();
                                        return new RelationChange(
                                                operation,
                                                String.valueOf(record.getRelationUserId()),
                                                record.getNickName(),
                                                record.getAvatar(),
                                                record.getRemarkName(),
                                                record.getRelationFlags() != null
                                                        ? record.getRelationFlags() & 0xFF
                                                        : null);
                                    })
                            .collect(Collectors.toList());

            // Get latest version (from last record) for next batch sync
            String latestVersion =
                    records.isEmpty()
                            ? version
                            : records.get(records.size() - 1).getVersion().toString();

            RelationSyncData data = new RelationSyncData(false, latestVersion, changes);
            return ResponseEntity.ok(new RelationSyncResponse(200, "success", data));
        } catch (InvalidVersionException e) {
            return ResponseEntity.badRequest()
                    .body(new RelationSyncResponse(400, e.getMessage(), null));
        }
    }
}
