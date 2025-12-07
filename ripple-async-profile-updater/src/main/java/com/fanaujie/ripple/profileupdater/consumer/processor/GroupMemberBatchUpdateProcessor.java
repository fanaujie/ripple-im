package com.fanaujie.ripple.profileupdater.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.profileupdater.GroupMemberBatchUpdateData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class GroupMemberBatchUpdateProcessor implements Processor<ProfileUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(GroupMemberBatchUpdateProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;

    public GroupMemberBatchUpdateProcessor(
            RippleStorageFacade storageFacade, ExecutorService executorService) {
        this.storageFacade = storageFacade;
        this.executorService = executorService;
    }

    @Override
    public Void handle(ProfileUpdatePayload payload) throws Exception {
        GroupMemberBatchUpdateData batchData = payload.getGroupMemberBatchUpdateData();
        List<Future<Void>> futures = new ArrayList<>();
        for (Long groupId : batchData.getGroupIdsList()) {
            Future<Void> future =
                    executorService.submit(
                            () -> {
                                updateGroupMemberProfile(batchData, groupId);
                                return null;
                            });
            futures.add(future);
        }
        for (Future<Void> f : futures) {
            f.get();
        }
        return null;
    }

    private void updateGroupMemberProfile(GroupMemberBatchUpdateData data, Long groupId) {
        String nickname = null;
        String avatar = null;
        switch (data.getUpdateType()) {
            case UPDATE_NICKNAME:
                nickname = data.getNickname();
                break;
            case UPDATE_AVATAR:
                avatar = data.getAvatar();
                break;
            case DELETE_AVATAR:
                avatar = null;
                break;
            default:
                logger.error("Unknown update type: {}", data.getUpdateType());
                throw new IllegalArgumentException("Unknown update type: " + data.getUpdateType());
        }
        storageFacade.updateGroupMemberProfile(groupId, data.getUserId(), nickname, avatar);
    }
}
