package com.itcen.whiteboardserver.common.broadcast.dto;

import com.itcen.whiteboardserver.turn.dto.response.TurnResponse;
import lombok.Builder;

@Builder
public record TurnBroadcastDto<T>(
        String destination,
        TurnResponse<T> data
) {
}
