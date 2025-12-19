package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.Messages;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class MessageService {

    private static final int MAX_READ_SIZE = 200;

    private final MessageAPISender messageAPISender;
    private final SnowflakeIdClient snowflakeIdClient;
    private final RippleStorageFacade storageFacade;
    private final ConversationSummaryStorage conversationStorage;

    public MessageService(
            MessageAPISender messageAPISender,
            SnowflakeIdClient snowflakeIdClient,
            RippleStorageFacade storageFacade,
            ConversationSummaryStorage conversationStorage) {
        this.messageAPISender = messageAPISender;
        this.snowflakeIdClient = snowflakeIdClient;
        this.storageFacade = storageFacade;
        this.conversationStorage = conversationStorage;
    }

    public ResponseEntity<MessageResponse> sendMessage(SendMessageRequest request) {
        try {
            SendMessageReq.Builder builder = SendMessageReq.newBuilder();
            long msgId = validateAndConvertSendMessageRequest(request, builder);
            messageAPISender.seenMessage(builder.build());
            return ResponseEntity.status(200).body(MessageResponse.success(String.valueOf(msgId)));
        } catch (IllegalArgumentException e) {
            log.error("sendMessage: Invalid request data", e);
            return ResponseEntity.badRequest()
                    .body(
                            new MessageResponse(
                                    400, "Invalid request data: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("sendMessage: Error sending message", e);
            return ResponseEntity.status(500)
                    .body(new MessageResponse(500, "Internal server error", null));
        }
    }

    public ResponseEntity<ReadMessagesResponse> readMessages(
            String conversationId, long messageId, int readSize, long currentUserId) {
        try {
            // Validate readSize
            if (readSize <= 0 || readSize > MAX_READ_SIZE) {
                return ResponseEntity.badRequest()
                        .body(
                                ReadMessagesResponse.error(
                                        400, "Invalid read size. Must be between 1 and 200"));
            }
            Messages result = storageFacade.getMessages(conversationId, messageId, readSize);
            ReadMessagesData data =
                    new ReadMessagesData(
                            result.getMessages().stream().map(MessageItem::new).toList());
            return ResponseEntity.ok(ReadMessagesResponse.success(data));
        } catch (NumberFormatException e) {
            log.error("readMessages: Invalid message ID format", e);
            return ResponseEntity.badRequest()
                    .body(ReadMessagesResponse.error(400, "Invalid message ID format"));
        } catch (Exception e) {
            log.error("readMessages: Error reading messages", e);
            return ResponseEntity.status(500)
                    .body(ReadMessagesResponse.error(500, "Internal server error"));
        }
    }

    public ResponseEntity<CommonResponse> markLastReadMessageId(
            String conversationId, long messageId, long currentUserId) {
        try {
            this.storageFacade.markLastReadMessageId(conversationId, currentUserId, messageId);
            this.conversationStorage.resetUnreadCount(currentUserId, conversationId);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            log.error("readMessages: Error reading messages", e);
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Internal server error"));
        }
    }

    private long validateAndConvertSendMessageRequest(
            SendMessageRequest request, SendMessageReq.Builder builder) throws Exception {
        if (request.getSenderId() == null) {
            throw new IllegalArgumentException("SenderId cannot be null");
        }

        boolean hasReceiverId =
                request.getReceiverId() != null && !request.getReceiverId().isEmpty();
        boolean hasGroupId = request.getGroupId() != null && !request.getGroupId().isEmpty();

        if (hasReceiverId && hasGroupId) {
            throw new IllegalArgumentException(
                    "Only one of receiverId or groupId should be provided, not both");
        }
        if (!hasReceiverId && !hasGroupId) {
            throw new IllegalArgumentException("Either receiverId or groupId is required");
        }

        long senderId = Long.parseLong(request.getSenderId());
        GenerateIdResponse res = snowflakeIdClient.requestSnowflakeId().get();

        builder.setSenderId(senderId)
                .setMessageId(res.getId())
                .setSendTimestamp(Instant.now().getEpochSecond())
                .setSingleMessageContent(request.toSingleMessageContent());

        if (hasGroupId) {
            // Group message
            long groupId = Long.parseLong(request.getGroupId());
            if (request.getConversationId() == null || request.getConversationId().isEmpty()) {
                request.setConversationId(ConversationUtils.generateGroupConversationId(groupId));
            }
            builder.setConversationId(request.getConversationId()).setGroupId(groupId);
        } else {
            // Single chat message
            long receiverId = Long.parseLong(request.getReceiverId());
            if (request.getConversationId() == null || request.getConversationId().isEmpty()) {
                request.setConversationId(
                        ConversationUtils.generateConversationId(senderId, receiverId));
            }
            builder.setConversationId(request.getConversationId()).setReceiverId(receiverId);
        }

        return res.getId();
    }
}
