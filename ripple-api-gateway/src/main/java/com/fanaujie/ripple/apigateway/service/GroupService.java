package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.CreateGroupRequest;
import com.fanaujie.ripple.apigateway.dto.CreateGroupResponse;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupCreateCommand;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class GroupService {

    private final MessageAPISender messageAPISender;
    private final SnowflakeIdClient snowflakeIdClient;

    public GroupService(MessageAPISender messageAPISender, SnowflakeIdClient snowflakeIdClient) {
        this.messageAPISender = messageAPISender;
        this.snowflakeIdClient = snowflakeIdClient;
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
            groupCreateCommandBuilder.addAllMemberIds(request.getMemberIds());
            SendGroupCommandReq.Builder commandReqBuilder = SendGroupCommandReq.newBuilder();
            commandReqBuilder.setSenderId(senderId);
            commandReqBuilder.setGroupId(groupId);
            commandReqBuilder.setMessageId(messageId);
            commandReqBuilder.setSendTimestamp(Instant.now().getEpochSecond());
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
}
