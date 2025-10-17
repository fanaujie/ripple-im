package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;

public interface UserPresenceStorage {

    void setUserOnline(UserOnlineReq request);

    QueryUserOnlineResp getUserOnline(QueryUserOnlineReq request);
}
