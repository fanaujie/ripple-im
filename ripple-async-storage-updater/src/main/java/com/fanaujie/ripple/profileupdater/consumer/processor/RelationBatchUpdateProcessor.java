package com.fanaujie.ripple.profileupdater.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.profileupdater.RelationBatchUpdateData;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class RelationBatchUpdateProcessor implements Processor<ProfileUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(RelationBatchUpdateProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;

    public RelationBatchUpdateProcessor(
            RippleStorageFacade storageFacade, ExecutorService executorService) {
        this.storageFacade = storageFacade;
        this.executorService = executorService;
    }

    @Override
    public Void handle(ProfileUpdatePayload payload) throws Exception {
        RelationBatchUpdateData batchData = payload.getRelationBatchUpdateData();
        List<Future<Void>> futures = new ArrayList<>();

        // Process each friend in parallel
        for (Long friendId : batchData.getFriendIdsList()) {
            Future<Void> future =
                    executorService.submit(
                            () -> {
                                updateFriendProfile(batchData, friendId);
                                return null;
                            });
            futures.add(future);
        }

        for (Future<Void> f : futures) {
            f.get();
        }
        return null;
    }

    private void updateFriendProfile(RelationBatchUpdateData data, Long friendId) {
        try {
            switch (data.getUpdateType()) {
                case UPDATE_NICKNAME:
                    storageFacade.updateFriendNickName(
                            friendId, data.getUserId(), data.getNickname());
                    break;
                case UPDATE_AVATAR:
                    storageFacade.updateFriendAvatar(friendId, data.getUserId(), data.getAvatar());
                    break;
                case DELETE_AVATAR:
                    storageFacade.updateFriendAvatar(friendId, data.getUserId(), null);
                    break;
                default:
                    logger.error("Unknown update type: {}", data.getUpdateType());
                    throw new IllegalArgumentException(
                            "Unknown update type: " + data.getUpdateType());
            }
        } catch (NotFoundRelationException e) {
            logger.warn(
                    "Relation not found for userId: {}, friendId: {}. Skipping update.",
                    data.getUserId(),
                    friendId);
        }
    }
}
