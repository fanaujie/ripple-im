package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_UPDATE_INFO_COMMAND;

public class UpdateGroupInfoCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private final Logger logger =
            LoggerFactory.getLogger(UpdateGroupInfoCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;

    public UpdateGroupInfoCommandPayloadProcessor(RippleStorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @Override
    public Void handle(GroupCommandData groupCommandData) throws Exception {
        SendGroupCommandReq sendGroupCommandReq = groupCommandData.getData();
        if (sendGroupCommandReq.getCommandContentCase() == GROUP_UPDATE_INFO_COMMAND) {
            this.updateGroupStorage(sendGroupCommandReq);
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for CreateGroupCommandPayloadProcessor");
    }

    private void updateGroupStorage(SendGroupCommandReq sendGroupCommandReq) throws Exception {}
}
