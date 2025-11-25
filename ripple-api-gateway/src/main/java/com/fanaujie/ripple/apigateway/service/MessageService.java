package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.Message;
import com.fanaujie.ripple.storage.model.Messages;
import com.fanaujie.ripple.storage.repository.ConversationRepository;
import com.fanaujie.ripple.storage.utils.ConversationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageService {

    private static final int MAX_READ_SIZE = 200;

    private final MessageAPISender messageAPISender;
    private final SnowflakeIdClient snowflakeIdClient;
    private final ConversationRepository conversationRepository;

    public MessageService(
            MessageAPISender messageAPISender,
            SnowflakeIdClient snowflakeIdClient,
            ConversationRepository conversationRepository) {
        this.messageAPISender = messageAPISender;
        this.snowflakeIdClient = snowflakeIdClient;
        this.conversationRepository = conversationRepository;
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
            Messages result =
                    conversationRepository.getMessages(conversationId, messageId, readSize);
            List<MessageItem> messageItems =
                    result.getMessages().stream()
                            .map(this::toMessageItem)
                            .collect(Collectors.toList());
            ReadMessagesData data = new ReadMessagesData(messageItems);
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
            conversationRepository.markLastReadMessageId(conversationId, currentUserId, messageId);
            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            log.error("readMessages: Error reading messages", e);
            return ResponseEntity.status(500)
                    .body(new CommonResponse(500, "Internal server error"));
        }
    }

    private long validateAndConvertSendMessageRequest(
            SendMessageRequest request, SendMessageReq.Builder builder) throws Exception {
        if (request.getSenderId() == null || request.getReceiverId() == null) {
            throw new IllegalArgumentException("SenderId and ReceiverId cannot be null");
        }
        long senderId = Long.parseLong(request.getSenderId());
        long receiverId = Long.parseLong(request.getReceiverId());
        if (request.getConversationId().isEmpty()) {
            String conversationId =
                    ConversationUtils.generateConversationId(
                            Long.parseLong(request.getSenderId()),
                            Long.parseLong(request.getReceiverId()));
            request.setConversationId(conversationId);
        }
        GenerateIdResponse res = snowflakeIdClient.requestSnowflakeId().get();
        builder.setMessageId(res.getId());
        builder.setSenderId(senderId)
                .setMessageId(res.getId())
                .setConversationId(request.getConversationId())
                .setReceiverId(receiverId)
                .setSendTimestamp(Instant.now().getEpochSecond())
                .setSingleMessageContent(request.toSingleMessageContent());
        return res.getId();
    }

    private MessageItem toMessageItem(Message message) {
        return new MessageItem(
                message.getConversationId(),
                String.valueOf(message.getMessageId()),
                String.valueOf(message.getSenderId()),
                String.valueOf(message.getReceiverId() == 0 ? null : message.getReceiverId()),
                String.valueOf(message.getGroupId() == 0 ? null : message.getGroupId()),
                message.getSendTimestamp(),
                message.getText(),
                message.getFileUrl(),
                message.getFileName());
    }
}
