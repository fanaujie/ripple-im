package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundGroupException;
import com.fanaujie.ripple.storage.model.GroupMemberInfo;
import com.fanaujie.ripple.storage.model.GroupVersionChange;
import com.fanaujie.ripple.storage.model.PagedGroupMemberResult;
import com.fanaujie.ripple.storage.model.PagedUserGroupResult;
import com.fanaujie.ripple.storage.model.UserGroup;
import com.fanaujie.ripple.storage.model.UserGroupVersionChange;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GroupService {

    private static final int MAX_SYNC_CHANGES = 200;
    private static final int MAX_PAGE_SIZE = 100;

    private final MessageAPISender messageAPISender;
    private final SnowflakeIdClient snowflakeIdClient;
    private final RippleStorageFacade storageFacade;

    public GroupService(
            MessageAPISender messageAPISender,
            SnowflakeIdClient snowflakeIdClient,
            RippleStorageFacade storageFacade) {
        this.messageAPISender = messageAPISender;
        this.snowflakeIdClient = snowflakeIdClient;
        this.storageFacade = storageFacade;
    }

    public ResponseEntity<CreateGroupResponse> createGroup(CreateGroupRequest request) {
        try {
            GenerateIdResponse groupIdResponse = snowflakeIdClient.requestSnowflakeId().get();
            long groupId = groupIdResponse.getId();

            GenerateIdResponse messageIdResponse = snowflakeIdClient.requestSnowflakeId().get();
            long messageId = messageIdResponse.getId();
            long senderId = Long.parseLong(request.getSenderId());

            GroupCreateCommand.Builder groupCreateCommandBuilder = GroupCreateCommand.newBuilder();
            groupCreateCommandBuilder.setGroupName(request.getGroupName());
            if (request.getGroupAvatar() != null && !request.getGroupAvatar().isEmpty()) {
                groupCreateCommandBuilder.setGroupAvatar(request.getGroupAvatar());
            }
            List<Long> newMemberIdsLong = new ArrayList<>();
            for (String s : request.getMemberIds()) {
                try {
                    newMemberIdsLong.add(Long.parseLong(s));
                } catch (NumberFormatException e) {
                    log.error("createGroup: Invalid new member ID format: {}", s, e);
                    return ResponseEntity.badRequest()
                            .body(CreateGroupResponse.error(400, "Invalid new member ID format"));
                }
            }
            groupCreateCommandBuilder.addAllMemberIds(newMemberIdsLong);
            SendGroupCommandReq.Builder commandReqBuilder = SendGroupCommandReq.newBuilder();
            commandReqBuilder.setSenderId(senderId);
            commandReqBuilder.setGroupId(groupId);
            commandReqBuilder.setMessageId(messageId);
            commandReqBuilder.setSendTimestamp(Instant.now().toEpochMilli());
            commandReqBuilder.setGroupCreateCommand(groupCreateCommandBuilder.build());
            messageAPISender.sendGroupCommand(commandReqBuilder.build());
            return ResponseEntity.ok(CreateGroupResponse.success(String.valueOf(groupId)));
        } catch (NumberFormatException e) {
            log.error("createGroup: Invalid sender ID format", e);
            return ResponseEntity.badRequest()
                    .body(CreateGroupResponse.error(400, "Invalid sender ID format"));
        } catch (Exception e) {
            log.error("createGroup: Error creating group", e);
            return ResponseEntity.status(500)
                    .body(CreateGroupResponse.error(500, "Internal server error"));
        }
    }

    public ResponseEntity<CommonResponse> inviteGroupMembers(
            long groupId, InviteGroupMemberRequest request) {
        try {
            GenerateIdResponse messageIdResponse = snowflakeIdClient.requestSnowflakeId().get();
            long messageId = messageIdResponse.getId();
            long senderId = Long.parseLong(request.getSenderId());

            GroupInviteCommand.Builder inviteCommandBuilder = GroupInviteCommand.newBuilder();

            List<Long> newMemberIdsLong = new ArrayList<>();
            for (String s : request.getNewMemberIds()) {
                try {
                    newMemberIdsLong.add(Long.parseLong(s));
                } catch (NumberFormatException e) {
                    log.error("inviteGroupMembers: Invalid new member ID format: {}", s, e);
                    return ResponseEntity.badRequest()
                            .body(CommonResponse.error(400, "Invalid new member ID format"));
                }
            }
            inviteCommandBuilder.addAllNewMemberIds(newMemberIdsLong);
            inviteCommandBuilder.setGroupName(request.getGroupName());
            if (request.getGroupAvatar() != null) {
                inviteCommandBuilder.setGroupAvatar(request.getGroupAvatar());
            }
            SendGroupCommandReq.Builder commandReqBuilder = SendGroupCommandReq.newBuilder();
            commandReqBuilder.setSenderId(senderId);
            commandReqBuilder.setGroupId(groupId);
            commandReqBuilder.setMessageId(messageId);
            commandReqBuilder.setSendTimestamp(Instant.now().toEpochMilli());
            commandReqBuilder.setGroupInviteCommand(inviteCommandBuilder.build());

            messageAPISender.sendGroupCommand(commandReqBuilder.build());
            return ResponseEntity.ok(CommonResponse.success());
        } catch (NumberFormatException e) {
            log.error("inviteGroupMembers: Invalid sender ID format", e);
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error(400, "Invalid sender ID format"));
        } catch (Exception e) {
            log.error("inviteGroupMembers: Error inviting members to group {}", groupId, e);
            return ResponseEntity.status(500)
                    .body(CommonResponse.error(500, "Internal server error"));
        }
    }

    public ResponseEntity<GetGroupMembersResponse> getGroupMembers(
            long groupId, long requesterId, String nextPageToken, int pageSize) {
        // Validate pageSize
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(GetGroupMembersResponse.error(400, "Invalid page size"));
        }

        try {
            // For first page, verify requester is a member using non-paged query
            if (nextPageToken == null || nextPageToken.isEmpty()) {
                List<GroupMemberInfo> allMembersInfo = storageFacade.getGroupMembersInfo(groupId);
                boolean isRequesterMember =
                        allMembersInfo.stream().anyMatch(m -> m.getUserId() == requesterId);

                if (!isRequesterMember) {
                    log.warn(
                            "getGroupMembers: User {} is not a member of group {}",
                            requesterId,
                            groupId);
                    return ResponseEntity.status(403)
                            .body(
                                    GetGroupMembersResponse.error(
                                            403, "Forbidden: Not a group member"));
                }
            }

            PagedGroupMemberResult result =
                    storageFacade.getGroupMembersPaged(groupId, nextPageToken, pageSize);

            List<GroupMemberData> members =
                    result.getMembers().stream()
                            .map(
                                    m -> {
                                        GroupMemberData data = new GroupMemberData();
                                        data.setUserId(String.valueOf(m.getUserId()));
                                        data.setName(m.getName());
                                        data.setAvatar(m.getAvatar());
                                        return data;
                                    })
                            .collect(Collectors.toList());

            GroupMembersData data =
                    new GroupMembersData(
                            members, result.getNextPageToken(), result.isHasMore(), null);
            if (!result.isHasMore()) {
                data.setLastVersion(storageFacade.getLatestGroupVersion(groupId));
            }

            return ResponseEntity.ok(GetGroupMembersResponse.success(data));
        } catch (NotFoundGroupException e) {
            log.error("getGroupMembers: Group {} not found", groupId, e);
            return ResponseEntity.status(404)
                    .body(GetGroupMembersResponse.error(404, "Group not found"));
        } catch (Exception e) {
            log.error("getGroupMembers: Error retrieving members for group {}", groupId, e);
            return ResponseEntity.status(500)
                    .body(GetGroupMembersResponse.error(500, "Internal server error"));
        }
    }

    public ResponseEntity<CommonResponse> removeGroupMember(long groupId, long userId) {
        try {
            GenerateIdResponse messageIdResponse = snowflakeIdClient.requestSnowflakeId().get();
            long messageId = messageIdResponse.getId();

            GroupQuitCommand.Builder quitCommandBuilder = GroupQuitCommand.newBuilder();

            SendGroupCommandReq.Builder commandReqBuilder = SendGroupCommandReq.newBuilder();
            commandReqBuilder.setSenderId(userId);
            commandReqBuilder.setGroupId(groupId);
            commandReqBuilder.setMessageId(messageId);
            commandReqBuilder.setSendTimestamp(Instant.now().toEpochMilli());
            commandReqBuilder.setGroupQuitCommand(quitCommandBuilder.build());

            messageAPISender.sendGroupCommand(commandReqBuilder.build());
            return ResponseEntity.ok(CommonResponse.success());
        } catch (Exception e) {
            log.error(
                    "removeGroupMember: Error removing user {} from group {}", userId, groupId, e);
            return ResponseEntity.status(500)
                    .body(CommonResponse.error(500, "Internal server error"));
        }
    }

    public ResponseEntity<CommonResponse> updateGroupName(
            long groupId, UpdateGroupInfoRequest request) {
        try {
            GenerateIdResponse messageIdResponse = snowflakeIdClient.requestSnowflakeId().get();
            long messageId = messageIdResponse.getId();
            long senderId = Long.parseLong(request.getSenderId());

            GroupUpdateInfoCommand.Builder updateCommandBuilder =
                    GroupUpdateInfoCommand.newBuilder();
            updateCommandBuilder.setUpdateType(GroupUpdateInfoCommand.UpdateType.UPDATE_NAME);
            updateCommandBuilder.setNewName(request.getValue());

            SendGroupCommandReq.Builder commandReqBuilder = SendGroupCommandReq.newBuilder();
            commandReqBuilder.setSenderId(senderId);
            commandReqBuilder.setGroupId(groupId);
            commandReqBuilder.setMessageId(messageId);
            commandReqBuilder.setSendTimestamp(Instant.now().toEpochMilli());
            commandReqBuilder.setGroupUpdateInfoCommand(updateCommandBuilder.build());

            messageAPISender.sendGroupCommand(commandReqBuilder.build());
            return ResponseEntity.ok(CommonResponse.success());
        } catch (NumberFormatException e) {
            log.error("updateGroupName: Invalid sender ID format", e);
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error(400, "Invalid sender ID format"));
        } catch (Exception e) {
            log.error("updateGroupName: Error updating group {} name", groupId, e);
            return ResponseEntity.status(500)
                    .body(CommonResponse.error(500, "Internal server error"));
        }
    }

    public ResponseEntity<GroupSyncResponse> syncGroupMembers(long groupId, String version) {
        // If version is null or empty, require full sync
        if (version == null || version.isEmpty()) {
            GroupSyncData data = new GroupSyncData(true, null, new ArrayList<>());
            return ResponseEntity.ok(new GroupSyncResponse(200, "success", data));
        }

        try {
            // Query changes with max batch size
            List<GroupVersionChange> records =
                    this.storageFacade.getGroupChanges(groupId, version, MAX_SYNC_CHANGES);

            // Convert records to GroupChange DTOs with new list-based structure
            List<GroupChange> changes =
                    records.stream()
                            .map(
                                    record -> {
                                        GroupChange groupChange = new GroupChange();
                                        groupChange.setGroupId(String.valueOf(record.getGroupId()));
                                        groupChange.setVersion(record.getVersion());

                                        // Map list of ChangeDetail to ChangeDetailDto
                                        if (record.getChanges() != null) {
                                            List<GroupChangeDetail> changeDtos =
                                                    record.getChanges().stream()
                                                            .map(
                                                                    detail -> {
                                                                        return new GroupChangeDetail(
                                                                                detail
                                                                                        .getOperation(),
                                                                                detail.getUserId()
                                                                                                != 0
                                                                                        ? String
                                                                                                .valueOf(
                                                                                                        detail
                                                                                                                .getUserId())
                                                                                        : null,
                                                                                detail.getName(),
                                                                                detail.getAvatar());
                                                                    })
                                                            .collect(Collectors.toList());
                                            groupChange.setData(changeDtos);
                                        }

                                        return groupChange;
                                    })
                            .collect(Collectors.toList());

            // Get latest version (from last record) for next batch sync
            String latestVersion =
                    records.isEmpty() ? version : records.get(records.size() - 1).getVersion();
            GroupSyncData data = new GroupSyncData(false, latestVersion, changes);
            return ResponseEntity.ok(new GroupSyncResponse(200, "success", data));

        } catch (InvalidVersionException e) {
            GroupSyncData data = new GroupSyncData(true, null, new ArrayList<>());
            return ResponseEntity.ok(new GroupSyncResponse(200, "success", data));
        } catch (Exception e) {
            log.error("syncGroupMembers: Error syncing members for group {}", groupId, e);
            return ResponseEntity.status(500)
                    .body(new GroupSyncResponse(500, "Internal server error", null));
        }
    }

    public ResponseEntity<UserGroupSyncResponse> syncUserGroups(long userId, String version) {
        // If version is null or empty, require full sync
        if (version == null || version.isEmpty()) {
            UserGroupSyncData data = new UserGroupSyncData(true, null, new ArrayList<>());
            return ResponseEntity.ok(new UserGroupSyncResponse(200, "success", data));
        }

        try {
            // Query changes with max batch size
            List<UserGroupVersionChange> records =
                    this.storageFacade.getUserGroupChanges(userId, version, MAX_SYNC_CHANGES);

            // Convert records to UserGroupChange DTOs
            List<UserGroupChange> changes =
                    records.stream()
                            .map(
                                    record ->
                                            new UserGroupChange(
                                                    record.getVersion(),
                                                    record.getOperation(),
                                                    String.valueOf(record.getGroupId()),
                                                    record.getGroupName(),
                                                    record.getGroupAvatar()))
                            .collect(Collectors.toList());

            // Get latest version (from last record) for next batch sync
            String latestVersion =
                    records.isEmpty() ? version : records.get(records.size() - 1).getVersion();
            UserGroupSyncData data = new UserGroupSyncData(false, latestVersion, changes);
            return ResponseEntity.ok(new UserGroupSyncResponse(200, "success", data));

        } catch (InvalidVersionException e) {
            UserGroupSyncData data = new UserGroupSyncData(true, null, new ArrayList<>());
            return ResponseEntity.ok(new UserGroupSyncResponse(200, "success", data));
        } catch (Exception e) {
            log.error("syncUserGroups: Error syncing groups for user {}", userId, e);
            return ResponseEntity.status(500)
                    .body(new UserGroupSyncResponse(500, "Internal server error", null));
        }
    }

    public ResponseEntity<GetUserGroupsResponse> getUserGroups(
            long userId, String nextPageToken, int pageSize) {
        // Validate pageSize
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(GetUserGroupsResponse.error(400, "Invalid page size"));
        }

        try {
            PagedUserGroupResult result =
                    storageFacade.getUserGroupsPaged(userId, nextPageToken, pageSize);

            List<UserGroupData> groups =
                    result.getGroups().stream()
                            .map(
                                    g ->
                                            new UserGroupData(
                                                    String.valueOf(g.getGroupId()),
                                                    g.getGroupName(),
                                                    g.getGroupAvatar()))
                            .collect(Collectors.toList());

            UserGroupsData data =
                    new UserGroupsData(groups, result.getNextPageToken(), result.isHasMore(), null);
            if (!result.isHasMore()) {
                data.setLastVersion(storageFacade.getLatestUserGroupVersion(userId));
            }

            return ResponseEntity.ok(GetUserGroupsResponse.success(data));
        } catch (Exception e) {
            log.error("getUserGroups: Error retrieving groups for user {}", userId, e);
            return ResponseEntity.status(500)
                    .body(GetUserGroupsResponse.error(500, "Internal server error"));
        }
    }
}
