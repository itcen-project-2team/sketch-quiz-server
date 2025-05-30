package com.itcen.whiteboardserver.game.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameStartRequest {
    @NotNull(message = "방 ID는 필수입니다.")
    private Long roomId;
}
