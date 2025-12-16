package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupSyncData {
    private boolean fullSync;
    private String latestVersion;
    private List<UserGroupChange> changes;
}
