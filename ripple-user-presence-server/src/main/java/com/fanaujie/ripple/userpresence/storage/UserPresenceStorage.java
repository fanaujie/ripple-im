package com.fanaujie.ripple.userpresence.storage;

import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;

public interface UserPresenceStorage {

    void setUserOnline(UserOnlineReq request);
}
