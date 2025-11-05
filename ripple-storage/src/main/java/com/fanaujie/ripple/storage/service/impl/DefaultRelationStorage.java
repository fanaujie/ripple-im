package com.fanaujie.ripple.storage.service.impl;

import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.cache.KvCache;
import com.fanaujie.ripple.storage.cache.RelationCachePrefixKey;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.service.CachedRelationStorage;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class DefaultRelationStorage implements CachedRelationStorage {
    private final Logger logger = LoggerFactory.getLogger(DefaultRelationStorage.class);
    private final KvCache kvCache;
    private final RelationRepository relationRepository;
    private final long expireSeconds = 3600;

    public DefaultRelationStorage(KvCache kvCache, RelationRepository relationRepository) {
        this.kvCache = kvCache;
        this.relationRepository = relationRepository;
    }

    public Optional<UserIds> getFriendIds(long userId) {
        String key = RelationCachePrefixKey.FRIEND_IDS.getValue() + userId;
        byte[] value =
                this.kvCache.getIfPresent(
                        key,
                        this.expireSeconds,
                        () -> {
                            List<Long> friendIds = this.relationRepository.getFriendIds(userId);
                            UserIds.Builder b = UserIds.newBuilder();
                            b.addAllUserIds(friendIds);
                            return b.build().toByteArray();
                        });
        if (value != null) {
            try {
                return Optional.of(UserIds.parseFrom(value));
            } catch (InvalidProtocolBufferException e) {
                logger.error("Failed to parse UserIds from cache for userId: {}", userId, e);
            }
        }
        return Optional.empty();
    }
}
