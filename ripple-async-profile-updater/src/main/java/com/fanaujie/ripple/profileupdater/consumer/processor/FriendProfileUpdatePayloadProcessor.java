package com.fanaujie.ripple.profileupdater.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.profileupdater.FriendProfileUpdateData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class FriendProfileUpdatePayloadProcessor implements Processor<ProfileUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(FriendProfileUpdatePayloadProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;

    public FriendProfileUpdatePayloadProcessor(
            RippleStorageFacade storageFacade, ExecutorService executorService) {
        this.storageFacade = storageFacade;
        this.executorService = executorService;
    }

    @Override
    public Void handle(ProfileUpdatePayload profileUpdateData) throws Exception {
        FriendProfileUpdateData friendProfileUpdateData =
                profileUpdateData.getFriendProfileUpdateData();
        this.storageFacade.syncFriendInfo(
                friendProfileUpdateData.getUserId(),
                friendProfileUpdateData.getFriendId(),
                friendProfileUpdateData.getFriendNickname(),
                friendProfileUpdateData.getFriendAvatar());
        return null;
    }
}
