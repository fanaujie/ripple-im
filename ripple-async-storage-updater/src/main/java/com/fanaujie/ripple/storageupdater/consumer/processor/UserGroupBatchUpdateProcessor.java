package com.fanaujie.ripple.storageupdater.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.protobuf.storageupdater.UserGroupBatchUpdateData;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class UserGroupBatchUpdateProcessor implements Processor<StorageUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(UserGroupBatchUpdateProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;

    public UserGroupBatchUpdateProcessor(
            RippleStorageFacade storageFacade, ExecutorService executorService) {
        this.storageFacade = storageFacade;
        this.executorService = executorService;
    }

    @Override
    public Void handle(StorageUpdatePayload payload) throws Exception {
        UserGroupBatchUpdateData batchData = payload.getUserGroupBatchUpdateData();
        List<Future<Void>> futures = new ArrayList<>();
        for (Long groupId : batchData.getGroupIdsList()) {
            Future<Void> future =
                    executorService.submit(
                            () -> {
                                updateGroupMemberStorage(batchData, groupId);
                                return null;
                            });
            futures.add(future);
        }
        for (Future<Void> f : futures) {
            f.get();
        }
        return null;
    }

    private void updateGroupMemberStorage(UserGroupBatchUpdateData data, Long groupId) {
        switch (data.getUpdateType()) {
            case UPDATE_NICKNAME:
                storageFacade.updateGroupMemberName(
                        groupId, data.getUserId(), data.getNickname(), data.getSendTimestamp());
                break;
            case UPDATE_AVATAR:
                storageFacade.updateGroupMemberAvatar(
                        groupId, data.getUserId(), data.getAvatar(), data.getSendTimestamp());
                break;
            case DELETE_AVATAR:
                storageFacade.updateGroupMemberAvatar(
                        groupId, data.getUserId(), null, data.getSendTimestamp());
                break;
            default:
                logger.error("Unknown update type: {}", data.getUpdateType());
                throw new IllegalArgumentException("Unknown update type: " + data.getUpdateType());
        }
    }
}
