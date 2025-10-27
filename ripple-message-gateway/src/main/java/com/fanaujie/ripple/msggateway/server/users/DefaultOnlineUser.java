package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.communication.batch.BatchExecutorService;
import com.fanaujie.ripple.msggateway.batch.UserOnlineBatchTask;
import com.fanaujie.ripple.shaded.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultOnlineUser implements OnlineUser {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOnlineUser.class);

    private record Key(String userId, String deviceId) {}

    private final Map<Key, Channel> userChannelMap = new ConcurrentHashMap<>();
    private final Map<Channel, Key> channelUserMap = new ConcurrentHashMap<>();
    private final BatchExecutorService<UserOnlineBatchTask> batchExecutorService;
    private final String serverLocation;

    public DefaultOnlineUser(
            int localServiceGrpcPort,
            BatchExecutorService<UserOnlineBatchTask> batchExecutorService)
            throws UnknownHostException {
        this.batchExecutorService = batchExecutorService;
        this.serverLocation =
                String.format(
                        "%s:%d", InetAddress.getLocalHost().getHostAddress(), localServiceGrpcPort);
    }

    @Override
    public void add(String userId, String deviceId, Channel channel) {
        logger.debug(
                "add: Adding online user - userId: {}, deviceId: {}, channel: {}",
                userId,
                deviceId,
                channel);
        Key key = new Key(userId, deviceId);
        userChannelMap.put(key, channel);
        channelUserMap.put(channel, key);

        // Push user online status to batch queue
        try {
            UserOnlineBatchTask task =
                    new UserOnlineBatchTask(userId, deviceId, true, this.serverLocation);
            batchExecutorService.push(task);
            logger.debug(
                    "add: User online task pushed to batch queue - userId: {}, deviceId: {}",
                    userId,
                    deviceId);
            logger.debug(
                    "add: User added successfully, total online users: {}", userChannelMap.size());
        } catch (InterruptedException e) {
            logger.error(
                    "add: Failed to push user online task to batch queue - userId: {}, deviceId: {}",
                    userId,
                    deviceId,
                    e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Optional<Channel> get(String userId, String deviceId) {
        logger.debug("get: Querying channel for userId: {}, deviceId: {}", userId, deviceId);
        Key key = new Key(userId, deviceId);
        Channel c = userChannelMap.get(key);
        if (c != null) {
            logger.debug("get: Channel found for userId: {}, deviceId: {}", userId, deviceId);
        } else {
            logger.debug("get: Channel not found for userId: {}, deviceId: {}", userId, deviceId);
        }
        return Optional.ofNullable(c);
    }

    @Override
    public void remove(Channel channel) {
        logger.debug("remove: Removing online user with channel: {}", channel);
        Key key = channelUserMap.remove(channel);
        if (key != null) {
            userChannelMap.remove(key);

            // Push user offline status to batch queue
            try {
                UserOnlineBatchTask task =
                        new UserOnlineBatchTask(key.userId(), key.deviceId(), false, "");
                batchExecutorService.push(task);
                logger.debug(
                        "remove: User removed successfully - userId: {}, deviceId: {}, remaining online users: {}",
                        key.userId(),
                        key.deviceId(),
                        userChannelMap.size());
            } catch (InterruptedException e) {
                logger.error(
                        "remove: Failed to push user offline task to batch queue - userId: {}, deviceId: {}",
                        key.userId(),
                        key.deviceId(),
                        e);
                Thread.currentThread().interrupt();
            }
        } else {
            logger.debug("remove: No user mapping found for channel: {}", channel);
        }
    }
}
