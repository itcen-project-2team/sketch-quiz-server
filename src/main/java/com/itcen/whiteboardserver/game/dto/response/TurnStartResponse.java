package com.itcen.whiteboardserver.game.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TurnStartResponse {
    private Long turnId;
    private Long drawerId;
    private String drawerName;
    private String keyword;
    private int turnDurationSeconds;
}
