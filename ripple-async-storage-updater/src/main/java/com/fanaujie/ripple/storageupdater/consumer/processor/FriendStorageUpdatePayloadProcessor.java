package com.fanaujie.ripple.storageupdater.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.storageupdater.FriendStorageUpdateData;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class FriendStorageUpdatePayloadProcessor implements Processor<StorageUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(FriendStorageUpdatePayloadProcessor.class);

    private final RippleStorageFacade storageFacade;

    public FriendStorageUpdatePayloadProcessor(RippleStorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @Override
    public Void handle(StorageUpdatePayload storageUpdateData) throws Exception {
        FriendStorageUpdateData friendStorageUpdateData =
                storageUpdateData.getFriendStorageUpdateData();
        try {
            this.storageFacade.syncFriendInfo(
                    friendStorageUpdateData.getUserId(),
                    friendStorageUpdateData.getFriendId(),
                    friendStorageUpdateData.getFriendNickname(),
                    friendStorageUpdateData.getFriendAvatar(),
                    friendStorageUpdateData.getSendTimestamp());
        } catch (NotFoundRelationException e) {
            logger.warn(
                    "Relation not found when updating friend storage: userId={}, friendId={}",
                    friendStorageUpdateData.getUserId(),
                    friendStorageUpdateData.getFriendId());
        }
        return null;
    }
}
