package com.fanaujie.ripple.msgapiserver.server;

import com.fanaujie.ripple.protobuf.msgapiserver.*;
import com.fanaujie.ripple.storage.model.BotInfo;
import com.fanaujie.ripple.storage.model.UserInstalledBot;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class BotManagementServiceImpl extends BotManagementServiceGrpc.BotManagementServiceImplBase {

    private final Logger logger = LoggerFactory.getLogger(BotManagementServiceImpl.class);
    private final RippleStorageFacade storageFacade;

    public BotManagementServiceImpl(RippleStorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @Override
    public void installBot(InstallBotReq request, StreamObserver<InstallBotResp> responseObserver) {
        try {
            BotInfo bot = storageFacade.getBot(request.getBotId());
            if (bot == null) {
                responseObserver.onNext(InstallBotResp.newBuilder()
                        .setSuccess(false)
                        .setErrorMessage("Bot not found")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            UserInstalledBot installedBot = UserInstalledBot.builder()
                    .userId(request.getUserId())
                    .botId(request.getBotId())
                    .installedAt(new Date())
                    .build();
            
            storageFacade.installBot(installedBot);

            responseObserver.onNext(InstallBotResp.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error installing bot", e);
            responseObserver.onNext(InstallBotResp.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Internal error")
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void uninstallBot(UninstallBotReq request, StreamObserver<UninstallBotResp> responseObserver) {
        try {
            storageFacade.uninstallBot(request.getUserId(), request.getBotId());
            responseObserver.onNext(UninstallBotResp.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
             logger.error("Error uninstalling bot", e);
            responseObserver.onNext(UninstallBotResp.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Internal error")
                    .build());
            responseObserver.onCompleted();
        }
    }
}
