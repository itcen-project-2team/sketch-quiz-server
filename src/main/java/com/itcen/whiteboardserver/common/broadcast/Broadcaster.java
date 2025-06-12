package com.itcen.whiteboardserver.common.broadcast;

import com.itcen.whiteboardserver.common.broadcast.dto.TurnBroadcastDto;
import com.itcen.whiteboardserver.common.broadcast.dto.TurnUnicastDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Broadcaster {
    private final SimpMessagingTemplate simpMessagingTemplate;

    public <T> void broadcast(TurnBroadcastDto<T> turnBroadcastDto) {
        simpMessagingTemplate.convertAndSend(
                turnBroadcastDto.destination(),
                turnBroadcastDto.data()
        );
    }

    public <T> void unicast(TurnUnicastDto<T> turnUnicastDto) {
        simpMessagingTemplate.convertAndSendToUser(
                turnUnicastDto.email(),
                turnUnicastDto.destination(),
                turnUnicastDto.data()
        );
    }
}
