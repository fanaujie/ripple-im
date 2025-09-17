package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;

import com.fanaujie.ripple.database.exception.NotFoundRelationException;
import com.fanaujie.ripple.database.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.database.model.UserProfile;
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

    public ResponseEntity<RelationResponse> addFriend(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Cannot add yourself as friend", null));
        }
        UserProfile userProfile = null;
        boolean insertRelation = false;
        try {
            userProfile = this.userProfileStorage.getUserProfile(targetUserId);
            int status = this.relationStorage.getRelationStatus(currentUserId, targetUserId);
            if ((status & UserRelation.FRIEND_FLAG) == UserRelation.FRIEND_FLAG) {
                return ResponseEntity.badRequest()
                        .body(
                                new RelationResponse(
                                        400, "Target user is already your friend", null));
            }
        } catch (NotFoundRelationException ignored) {
            // no existing relation, proceed to add friend
            insertRelation = true;
        } catch (NotFoundUserProfileException e) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Target user does not exist", null));
        }
        if (insertRelation) {
            this.relationStorage.insertRelationStatus(
                    currentUserId,
                    targetUserId,
                    userProfile.getNickName(),
                    (byte) UserRelation.FRIEND_FLAG);
        } else {
            // reset hidden and blocked flags if any
            this.relationStorage.updateRelationStatus(
                    currentUserId, targetUserId, (byte) UserRelation.FRIEND_FLAG);
        }

        // The updated relation flags is always FRIEND_FLAG (reset hidden and blocked)
        return ResponseEntity.ok(
                new RelationResponse(
                        200,
                        "success",
                        new RelationData(
                                String.valueOf(currentUserId),
                                String.valueOf(targetUserId),
                                UserRelation.FRIEND_FLAG)));
    }

    public ResponseEntity<RelationResponse> removeFriend(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Cannot remove yourself as friend", null));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Target user does not exist", null));
        }
        try {
            int status = this.relationStorage.getRelationStatus(currentUserId, targetUserId);
            if ((status & UserRelation.FRIEND_FLAG) != UserRelation.FRIEND_FLAG) {
                return ResponseEntity.badRequest()
                        .body(new RelationResponse(400, "Target user is not your friend", null));
            } else {
                status &= ~UserRelation.FRIEND_FLAG;
                this.relationStorage.updateRelationStatus(currentUserId, targetUserId, status);
                return ResponseEntity.ok(
                        new RelationResponse(
                                200,
                                "success",
                                new RelationData(
                                        String.valueOf(currentUserId),
                                        String.valueOf(targetUserId),
                                        status)));
            }
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "No existing relation with target user", null));
        }
    }

    public ResponseEntity<UserRelationsResponse> getFriendsWithBlockedLists(long currentUserId) {
        List<User> friends = new ArrayList<>();
        List<User> blockedUsers = new ArrayList<>();

        this.relationStorage
                .getFriendsWithBlockedUsers(currentUserId)
                .forEach(
                        u -> {
                            int flags = u.getRelationFlags();
                            boolean isFriend =
                                    (flags & UserRelation.FRIEND_FLAG) == UserRelation.FRIEND_FLAG;
                            boolean isBlocked =
                                    (flags & UserRelation.BLOCKED_FLAG)
                                            == UserRelation.BLOCKED_FLAG;
                            boolean isHidden =
                                    (flags & UserRelation.HIDDEN_FLAG) == UserRelation.HIDDEN_FLAG;

                            User user =
                                    new User(
                                            String.valueOf(u.getTargetUserId()),
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
                new UserRelationsResponse(
                        HttpStatus.OK.value(),
                        "success",
                        new UserRelationsData(friends, blockedUsers)));
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
            int status = this.relationStorage.getRelationStatus(currentUserId, targetUserId);
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

    public ResponseEntity<RelationResponse> updateBlockedStatus(
            long currentUserId, long targetUserId, boolean block) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Cannot block/unblock yourself", null));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Target user does not exist", null));
        }
        try {
            int status = this.relationStorage.getRelationStatus(currentUserId, targetUserId);
            if (block) {
                status |= UserRelation.BLOCKED_FLAG;
            } else {
                status &= ~UserRelation.BLOCKED_FLAG;
            }
            this.relationStorage.updateRelationStatus(currentUserId, targetUserId, status);
            return ResponseEntity.ok(
                    new RelationResponse(
                            200,
                            "success",
                            new RelationData(
                                    String.valueOf(currentUserId),
                                    String.valueOf(targetUserId),
                                    status)));
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "No existing relation with target user", null));
        }
    }

    public ResponseEntity<RelationResponse> hideBlockedUser(long currentUserId, long targetUserId) {
        if (currentUserId == targetUserId) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Cannot hide yourself", null));
        }
        if (!this.userProfileStorage.userProfileExists(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "Target user does not exist", null));
        }
        try {
            int status = this.relationStorage.getRelationStatus(currentUserId, targetUserId);
            // only blocked users can be hidden
            if ((status & UserRelation.BLOCKED_FLAG) != UserRelation.BLOCKED_FLAG) {
                return ResponseEntity.badRequest()
                        .body(new RelationResponse(400, "Target user is not blocked", null));
            }
            status |= UserRelation.HIDDEN_FLAG;
            this.relationStorage.updateRelationStatus(currentUserId, targetUserId, status);
            return ResponseEntity.ok(
                    new RelationResponse(
                            200,
                            "success",
                            new RelationData(
                                    String.valueOf(currentUserId),
                                    String.valueOf(targetUserId),
                                    status)));
        } catch (NotFoundRelationException e) {
            return ResponseEntity.badRequest()
                    .body(new RelationResponse(400, "No existing relation with target user", null));
        }
    }
}
