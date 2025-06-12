package com.itcen.whiteboardserver.draw.service;

import com.itcen.whiteboardserver.common.broadcast.Broadcaster;
import com.itcen.whiteboardserver.common.broadcast.dto.TurnBroadcastDto;
import com.itcen.whiteboardserver.draw.dto.DrawDto;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponse;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponseType;
import com.itcen.whiteboardserver.turn.entitiy.Turn;
import com.itcen.whiteboardserver.turn.repository.TurnRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Transactional
@RequiredArgsConstructor
@Service
public class DrawServiceImpl implements DrawService {
    private final TurnRepository turnRepository;
    private final Broadcaster broadcaster;

    @Override
    public void draw(DrawDto drawDto, Long gameId, String email) {
        Turn turn = turnRepository.findById(drawDto.turnId()).orElseThrow(
                () -> new RuntimeException("해당하는 턴이 존재하지 않습니다.")
        );

        if (turn.getIsTurnOver()) {
            throw new RuntimeException("현재 턴이 종료되었습니다.");
        }

        if (!email.equals(turn.getMember().getEmail())) {
            throw new RuntimeException("현재 턴 출제자가 아닙니다.");
        }

        broadcastDraw(drawDto, gameId);
    }

    private void broadcastDraw(DrawDto drawDto, Long gameId) {
        TurnResponse<DrawDto> response = new TurnResponse<>(
                TurnResponseType.DRAW,
                drawDto
        );

        broadcaster.broadcast(
                TurnBroadcastDto.<DrawDto>builder()
                        .destination("/topic/game/" + gameId + "/draw")
                        .data(response)
                        .build()
        );
    }
}
