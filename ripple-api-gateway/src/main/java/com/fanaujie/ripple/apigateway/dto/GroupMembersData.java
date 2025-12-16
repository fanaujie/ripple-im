package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMembersData {
    private List<GroupMemberData> members;
    private String nextPageToken;
    private boolean hasMore;
    private String lastVersion;
}
