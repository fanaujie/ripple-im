package com.fanaujie.ripple.profileupdater.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.profileupdater.FriendProfileUpdateData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class FriendProfileUpdatePayloadProcessor implements Processor<ProfileUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(FriendProfileUpdatePayloadProcessor.class);

    private final RippleStorageFacade storageFacade;

    public FriendProfileUpdatePayloadProcessor(RippleStorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @Override
    public Void handle(ProfileUpdatePayload profileUpdateData) throws Exception {
        FriendProfileUpdateData friendProfileUpdateData =
                profileUpdateData.getFriendProfileUpdateData();
        try {
            this.storageFacade.syncFriendInfo(
                    friendProfileUpdateData.getUserId(),
                    friendProfileUpdateData.getFriendId(),
                    friendProfileUpdateData.getFriendNickname(),
                    friendProfileUpdateData.getFriendAvatar());
        } catch (NotFoundRelationException e) {
            logger.warn(
                    "Relation not found when updating friend profile: userId={}, friendId={}",
                    friendProfileUpdateData.getUserId(),
                    friendProfileUpdateData.getFriendId());
        }
        return null;
    }
}
