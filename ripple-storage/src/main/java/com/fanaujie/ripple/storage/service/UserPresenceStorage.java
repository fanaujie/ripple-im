package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;

import java.util.List;

public interface UserPresenceStorage {

    void setUserOnline(UserOnlineReq request);

    void setUserOnlineBatch(List<UserOnlineReq> requests);

    QueryUserOnlineResp getUserOnline(QueryUserOnlineReq request);
}
