package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;

import com.fanaujie.ripple.database.exception.NotFoundRelationException;
import com.fanaujie.ripple.database.service.IRelationStorage;
import com.fanaujie.ripple.database.service.IUserProfileStorage;
import com.fanaujie.ripple.database.model.UserRelation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RelationService {

    private final IRelationStorage relationStorage;
    private final IUserProfileStorage userProfileStorage;

    public RelationService(
            IRelationStorage relationStorage, IUserProfileStorage userProfileStorage) {
        this.relationStorage = relationStorage;
        this.userProfileStorage = userProfileStorage;
    }

    public ResponseEntity<CommonResponse> addFriend(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot add yourself as friend"));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            byte status = this.relationStorage.getRelationStatus(targetUserId, currentUserId);
            if ((status & UserRelation.FRIEND_FLAG) == UserRelation.FRIEND_FLAG) {
                return ResponseEntity.badRequest()
                        .body(new CommonResponse(400, "Target user is already your friend"));
            }
        } catch (NotFoundRelationException ignored) {
            // no existing relation, proceed to add friend
        }
        this.relationStorage.upsertRelationStatus(
                currentUserId, targetUserId, UserRelation.FRIEND_FLAG);
        return ResponseEntity.ok(new CommonResponse(200, "success"));
    }

    public ResponseEntity<CommonResponse> removeFriend(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot remove yourself as friend"));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            byte status = this.relationStorage.getRelationStatus(targetUserId, currentUserId);
            if ((status & UserRelation.FRIEND_FLAG) != UserRelation.FRIEND_FLAG) {
                return ResponseEntity.badRequest()
                        .body(new CommonResponse(400, "Target user is not your friend"));
            } else {
                status &= ~UserRelation.FRIEND_FLAG;
                this.relationStorage.upsertRelationStatus(currentUserId, targetUserId, status);
            }
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "No existing relation with target user"));
        }
    }

    public ResponseEntity<UsersResponse> getFriendsWithBlockedLists(long currentUserId) {
        List<User> friends = new ArrayList<>();
        List<User> blockedUsers = new ArrayList<>();

        this.relationStorage
                .getFriendsWithBlockedUsers(currentUserId)
                .forEach(
                        u -> {
                            byte flags = u.getRelationFlags();
                            boolean isFriend =
                                    (flags & UserRelation.FRIEND_FLAG) == UserRelation.FRIEND_FLAG;
                            boolean isBlocked =
                                    (flags & UserRelation.BLOCKED_FLAG)
                                            == UserRelation.BLOCKED_FLAG;
                            boolean isHidden =
                                    (flags & UserRelation.HIDDEN_FLAG) == UserRelation.HIDDEN_FLAG;

                            User user =
                                    new User(
                                            u.getTargetUserId(),
                                            u.getTargetNickName(),
                                            u.getTargetAvatar(),
                                            u.getTargetUserDisplayName());

                            if (isFriend && !isBlocked && !isHidden) {
                                friends.add(user);
                            } else if (isBlocked && !isHidden) {
                                blockedUsers.add(user);
                            }
                        });
        return ResponseEntity.ok(
                new UsersResponse(
                        HttpStatus.OK.value(), "success", new UserData(friends, blockedUsers)));
    }

    public ResponseEntity<CommonResponse> updateFriendDisplayName(
            long currentUserId, long targetUserId, String displayName) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot update display name for yourself"));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            byte status = this.relationStorage.getRelationStatus(targetUserId, currentUserId);
            // only friends can set display name
            if ((status & UserRelation.FRIEND_FLAG) == UserRelation.FRIEND_FLAG) {
                this.relationStorage.updateFriendDisplayName(
                        currentUserId, targetUserId, displayName);
                return ResponseEntity.ok(new CommonResponse(200, "success"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new CommonResponse(400, "You are not friends with the target user"));
            }
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "No existing relation with target user"));
        }
    }

    public ResponseEntity<CommonResponse> updateBlockedStatus(
            long currentUserId, long targetUserId, boolean block) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot block/unblock yourself"));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            byte status = this.relationStorage.getRelationStatus(targetUserId, currentUserId);
            if (block) {
                status |= UserRelation.BLOCKED_FLAG;
            } else {
                status &= ~UserRelation.BLOCKED_FLAG;
            }
            this.relationStorage.upsertRelationStatus(currentUserId, targetUserId, status);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "No existing relation with target user"));
        }
    }

    public ResponseEntity<CommonResponse> hideBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Cannot hide yourself"));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Target user does not exist"));
        }
        try {
            byte status = this.relationStorage.getRelationStatus(targetUserId, currentUserId);
            // only blocked users can be hidden
            if ((status & UserRelation.BLOCKED_FLAG) != UserRelation.BLOCKED_FLAG) {
                return ResponseEntity.badRequest()
                        .body(new CommonResponse(400, "Target user is not blocked"));
            }
            status |= UserRelation.HIDDEN_FLAG;
            this.relationStorage.upsertRelationStatus(currentUserId, targetUserId, status);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "No existing relation with target user"));
        }
    }
}
