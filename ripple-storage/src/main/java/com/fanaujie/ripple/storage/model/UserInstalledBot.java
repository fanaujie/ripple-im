package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInstalledBot {
    private Long userId;
    private Long botId;
    private Date installedAt;
    private String settings;
}
