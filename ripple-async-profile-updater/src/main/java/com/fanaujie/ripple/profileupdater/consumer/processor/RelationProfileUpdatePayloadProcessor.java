package com.fanaujie.ripple.profileupdater.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.profileupdater.RelationProfileUpdateData;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.model.UpdateFriendAvatarResult;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

import static com.fanaujie.ripple.protobuf.profileupdater.RelationProfileUpdateData.UpdateType.UPDATE_AVATAR;
import static com.fanaujie.ripple.protobuf.push.UserNotificationType.USER_NOTIFICATION_TYPE_CONVERSATION_UPDATE;

public class RelationProfileUpdatePayloadProcessor
        implements Processor<ProfileUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(RelationProfileUpdatePayloadProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;

    public RelationProfileUpdatePayloadProcessor(
            RippleStorageFacade storageFacade, ExecutorService executorService) {
        this.storageFacade = storageFacade;
        this.executorService = executorService;
    }

    @Override
    public Void handle(ProfileUpdatePayload profileUpdateData) throws Exception {
        RelationProfileUpdateData relationProfileUpdateData =
                profileUpdateData.getRelationProfileUpdateData();
        this.storageFacade
                .getFriendIds(relationProfileUpdateData.getUserId())
                .ifPresent(
                        friendIds ->
                                this.updateRelationAndConversationStorage(
                                        friendIds, relationProfileUpdateData));

        return null;
    }

    private void updateRelationAndConversationStorage(
            UserIds friendIds, RelationProfileUpdateData relationProfileUpdateData) {

        for (long friendId : friendIds.getUserIdsList()) {
            executorService.submit(
                    () -> {
                        try {
                            switch (relationProfileUpdateData.getUpdateType()) {
                                case UPDATE_NICKNAME:
                                    storageFacade.updateFriendNickName(
                                            friendId,
                                            relationProfileUpdateData.getUserId(),
                                            relationProfileUpdateData.getNickname());
                                    break;
                                case UPDATE_AVATAR:
                                case DELETE_AVATAR:
                                    String avatar =
                                            relationProfileUpdateData.getUpdateType()
                                                            == UPDATE_AVATAR
                                                    ? relationProfileUpdateData.getAvatar()
                                                    : null;
                                    storageFacade.updateFriendAvatar(
                                            friendId,
                                            relationProfileUpdateData.getUserId(),
                                            avatar);
                                    break;
                                default:
                                    logger.error(
                                            "updateRelationAndConversationStorage: Unknown update type {} for RelationProfileUpdateData",
                                            relationProfileUpdateData.getUpdateType());
                                    throw new IllegalArgumentException(
                                            "Unknown update type %s for RelationProfileUpdateData"
                                                    .formatted(
                                                            relationProfileUpdateData
                                                                    .getUpdateType()));
                            }
                        } catch (NotFoundRelationException e) {
                            // Log and ignore if relation not found
                            logger.warn(
                                    "updateRelationAndConversationStorage: Relation not found for userId {} and friendId {}",
                                    relationProfileUpdateData.getUserId(),
                                    friendId);
                        }
                    });
        }
    }
}
