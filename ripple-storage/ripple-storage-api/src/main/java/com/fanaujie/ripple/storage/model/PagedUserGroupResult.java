package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedUserGroupResult {
    private List<UserGroup> groups;
    private String nextPageToken;
    private boolean hasMore;
}
